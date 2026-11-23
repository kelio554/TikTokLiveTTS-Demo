package net.mkhalil.taktik.data.live.webcast

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.mkhalil.taktik.data.live.LiveEventSource
import net.mkhalil.taktik.data.remote.TikTokUserApi
import net.mkhalil.taktik.domain.model.ConnectionState
import net.mkhalil.taktik.domain.model.HostProfile
import net.mkhalil.taktik.domain.model.LiveEvent
import net.mkhalil.taktik.domain.model.LiveRoomInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real TikTok webcast event source backed by the modern Eulerstream protobuf
 * sign endpoint.
 *
 * Pipeline:
 *  1. Resolve the host's `roomId` via [TikTokUserApi] (re-uses the Connect tab's
 *     existing profile lookup).
 *  2. Ask [EulerstreamSignClient.fetchSignedWebSocket] for a signed wss:// URL
 *     plus the initial message batch and cookies.
 *  3. Emit the initial batch immediately, then open the WebSocket.
 *  4. For every binary frame received: parse [PushFrame] → unwrap
 *     [WebcastResponse] → decode each message via [WebcastDecoders].
 *  5. If `needsAck`, send a minimal ack frame back so TikTok keeps streaming.
 *  6. On unexpected close: reconnect with exponential backoff while
 *     [autoReconnect] returns true.
 */
class WebcastLiveEventSource(
    private val client: OkHttpClient,
    private val signClient: EulerstreamSignClient,
    private val userApi: TikTokUserApi,
    private val autoReconnect: () -> Boolean,
) : LiveEventSource {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 256)
    override val events = _events.asSharedFlow()

    private val _roomInfo = MutableStateFlow(LiveRoomInfo())
    override val roomInfo = _roomInfo.asStateFlow()

    private var scope: CoroutineScope? = null
    private var supervisor: Job? = null
    private var socket: WebSocket? = null
    private val closedByUser = AtomicBoolean(false)

    override suspend fun connect(host: HostProfile) {
        disconnect()
        closedByUser.set(false)
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        supervisor = s.launch { runConnectionLoop(host) }
    }

    override suspend fun disconnect() {
        closedByUser.set(true)
        socket?.close(1000, "Stopped by user")
        socket = null
        supervisor?.cancel()
        scope?.cancel()
        supervisor = null
        scope = null
        if (_state.value !is ConnectionState.Idle) {
            _state.value = ConnectionState.Disconnected("Stopped")
        }
    }

    private suspend fun runConnectionLoop(host: HostProfile) {
        var attempt = 0
        while (!closedByUser.get()) {
            _state.value = if (attempt == 0) ConnectionState.Connecting
                else ConnectionState.Reconnecting(attempt)
            try {
                openOneConnection(host)
                if (closedByUser.get() || !autoReconnect()) {
                    _state.value = ConnectionState.Disconnected("Closed")
                    return
                }
            } catch (e: Throwable) {
                if (!autoReconnect() || closedByUser.get()) {
                    _state.value = ConnectionState.Error(e.message ?: "Connection failed")
                    return
                }
                // Auto-reconnect path: keep the UI on "Reconnecting" instead
                // of flashing the error label between attempts.
                Log.w(TAG, "connection failed, will reconnect", e)
            }
            attempt++
            // While we wait out the backoff, show Reconnecting so the dot
            // doesn't flip back and forth with whatever the listener set.
            _state.value = ConnectionState.Reconnecting(attempt)
            val backoff = (1_000L * (1L shl attempt.coerceAtMost(5))).coerceAtMost(30_000L)
            delay(backoff)
        }
    }

    private suspend fun openOneConnection(host: HostProfile) {
        val roomId = host.roomId
            ?: userApi.fetchProfile(host.uniqueId)?.roomId
            ?: userApi.fetchRoomId(host.uniqueId)
            ?: throw IllegalStateException("Host @${host.uniqueId} is not live (no roomId)")

        val signed = signClient.fetchSignedWebSocket(
            uniqueId = host.uniqueId,
            roomId = roomId,
            userAgent = DEFAULT_UA,
        )

        // Emit the initial batch immediately so the UI shows recent activity.
        signed.initialResponse?.let { emitMessages(it) }

        val reqBuilder = Request.Builder().url(signed.wsUrl)
        signed.headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        if (signed.cookieHeader.isNotBlank()) {
            reqBuilder.header("Cookie", signed.cookieHeader)
        }

        val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
        var heartbeatJob: Job? = null
        val seqId = java.util.concurrent.atomic.AtomicLong(1L)

        socket = client.newWebSocket(reqBuilder.build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _state.value = ConnectionState.Connected
                // Match tiktok-live-connector: immediately send an im_enter_room
                // frame to "activate" the message stream, otherwise TikTok only
                // ever sends the initial batch and goes silent. Then start the
                // heartbeat loop so the WS isn't closed for inactivity.
                runCatching {
                    val enter = encodeEnterRoomFrame(roomId)
                    webSocket.send(ByteString.of(*enter))
                }.onFailure { Log.w(TAG, "im_enter_room send failed", it) }

                heartbeatJob = scope?.launch {
                    while (true) {
                        delay(HEARTBEAT_INTERVAL_MS)
                        runCatching {
                            val frame = encodeHeartbeatFrame(roomId, seqId.getAndIncrement())
                            webSocket.send(ByteString.of(*frame))
                        }
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleFrame(webSocket, bytes.toByteArray())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                heartbeatJob?.cancel()
                if (!autoReconnect() || closedByUser.get()) {
                    _state.value = ConnectionState.Disconnected("WS closed: $code $reason")
                }
                closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                heartbeatJob?.cancel()
                val httpInfo = response?.let { " (server: HTTP ${it.code} ${it.message})" }.orEmpty()
                Log.w(TAG, "WS failure$httpInfo url=${signed.wsUrl.take(80)}", t)
                if (!autoReconnect() || closedByUser.get()) {
                    _state.value = ConnectionState.Error("${t.message ?: "WS failure"}$httpInfo")
                }
                closed.complete(Unit)
            }
        })

        closed.await()
    }

    /**
     * Heartbeat PushFrame matching tiktok-live-connector's wire format:
     *   PushFrame { payloadEncoding="pb"; payloadType="hb";
     *               payload=HeartBeatMessage{ roomId, sendPacketSeqId } }
     * HeartBeatMessage: field 1 roomId (int64), field 2 sendPacketSeqId (int64).
     */
    private fun encodeHeartbeatFrame(roomId: String, seqId: Long): ByteArray {
        val hb = java.io.ByteArrayOutputStream()
        roomId.toLongOrNull()?.let { writeTag(hb, 1, 0); writeVarint(hb, it) }
        writeTag(hb, 2, 0); writeVarint(hb, seqId)
        return encodePushFrame(payloadType = "hb", payload = hb.toByteArray())
    }

    /**
     * im_enter_room PushFrame — sent right after WS open so TikTok starts
     * streaming events. Static fields mirror the values the official web
     * client sends: liveId=12, identity="audience".
     */
    private fun encodeEnterRoomFrame(roomId: String): ByteArray {
        val msg = java.io.ByteArrayOutputStream()
        roomId.toLongOrNull()?.let { writeTag(msg, 1, 0); writeVarint(msg, it) }
        writeTag(msg, 4, 0); writeVarint(msg, 12L)                  // liveId
        writeTag(msg, 5, 2); writeBytes(msg, "audience".toByteArray())
        writeTag(msg, 7, 0); writeVarint(msg, 0L)                   // accountType
        writeTag(msg, 9, 2); writeBytes(msg, "0".toByteArray())     // filterWelcomeMsg
        return encodePushFrame(payloadType = "im_enter_room", payload = msg.toByteArray())
    }

    private fun encodePushFrame(payloadType: String, payload: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        writeTag(out, 6, 2); writeBytes(out, "pb".toByteArray())
        writeTag(out, 7, 2); writeBytes(out, payloadType.toByteArray())
        writeTag(out, 8, 2); writeBytes(out, payload)
        return out.toByteArray()
    }

    private fun handleFrame(ws: WebSocket, raw: ByteArray) {
        val frame = try {
            PushFrame.parse(raw)
        } catch (_: Throwable) { return }

        if (frame.payloadType != "msg") return

        val gzipped = frame.headers["compress_type"] == "gzip" ||
            frame.payloadEncoding == "gzip"

        val resp = try {
            WebcastResponse.parse(frame.payload, gzipped)
        } catch (_: Throwable) { return }

        if (resp.needsAck) {
            runCatching {
                val ack = encodeAckFrame(frame.logId, resp.internalExt)
                ws.send(ByteString.of(*ack))
            }
        }

        scope?.launch { emitMessages(resp) }
    }

    private suspend fun emitMessages(resp: WebcastResponse) {
        val now = System.currentTimeMillis()
        for (m in resp.messages) {
            if (m.method == "WebcastRoomUserSeqMessage") {
                WebcastDecoders.decodeRoomUserSeq(m.payload)?.let { _roomInfo.value = it }
                continue
            }
            val ev = WebcastDecoders.decode(m.method, m.payload, now) ?: continue
            _events.emit(ev)
        }
    }

    /** Minimal PushFrame ack: logId (field 2), payloadType="ack" (field 7), echoed internalExt (field 8). */
    private fun encodeAckFrame(logId: Long, internalExt: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        writeTag(out, 2, 0); writeVarint(out, logId)
        writeTag(out, 7, 2); writeBytes(out, "ack".toByteArray())
        if (internalExt.isNotEmpty()) {
            writeTag(out, 8, 2); writeBytes(out, internalExt.toByteArray(Charsets.UTF_8))
        }
        return out.toByteArray()
    }

    private fun writeTag(out: java.io.ByteArrayOutputStream, field: Int, wire: Int) {
        writeVarint(out, ((field shl 3) or wire).toLong())
    }

    private fun writeVarint(out: java.io.ByteArrayOutputStream, value: Long) {
        var v = value
        while ((v and 0x7FL.inv()) != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt() and 0x7F)
    }

    private fun writeBytes(out: java.io.ByteArrayOutputStream, bytes: ByteArray) {
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }

    companion object {
        private const val TAG = "WebcastLive"
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
