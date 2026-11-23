package net.mkhalil.taktik.data.live.webcast

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Eulerstream sign client targeting the modern protobuf endpoint used by the
 * official `tiktok-live-connector` (TS) and `TikTokLive` (Py) libraries.
 *
 * Endpoint: `GET {baseUrl}/webcast/fetch`
 *
 * Required query parameters:
 *   - client       Library identity ("ttlive-node" works on the public tier)
 *   - room_id      The TikTok room id
 *   - user_agent   The User-Agent the signed request will use
 *   - platform     "Web" or "Mobile"
 *
 * Authentication: header `X-Api-Key: <key>` — required since Eulerstream
 * tightened their free tier; sign up for a free key at https://www.eulerstream.com
 *
 * Response:
 *   - Status 200, Content-Type: application/protobuf
 *   - Body: serialized [ProtoMessageFetchResult] which embeds the signed
 *     `wsUrl` (field 10) and the initial batch of messages (fields 1, 2, 5, 9
 *     which our [WebcastResponse] parser already handles).
 *   - Header `x-set-tt-cookie`: cookies to forward to the WebSocket
 *   - Header `x-room-id`: echoes the room id
 */
class EulerstreamSignClient(
    private val client: OkHttpClient,
    private val baseUrlProvider: () -> String,
    private val apiKeyProvider: () -> String,
) {

    data class SignResult(
        /** Signed wss:// URL ready to connect to. */
        val wsUrl: String,
        /** Headers to send when opening the WebSocket. */
        val headers: Map<String, String>,
        /** Cookies (already pre-formatted as `Cookie:` header value). */
        val cookieHeader: String,
        /** First batch of webcast messages bundled with the sign response. */
        val initialResponse: WebcastResponse?,
    )

    class SignException(message: String, cause: Throwable? = null) : Exception(message, cause)

    suspend fun fetchSignedWebSocket(
        uniqueId: String,
        roomId: String,
        userAgent: String,
    ): SignResult = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
        val key = apiKeyProvider()

        val url = "$baseUrl/webcast/fetch".toHttpUrl().newBuilder()
            .addQueryParameter("client", CLIENT_NAME)
            .addQueryParameter("room_id", roomId)
            .addQueryParameter("user_agent", userAgent)
            .addQueryParameter("platform", "web")
            .build()

        val reqBuilder = Request.Builder().url(url).get()
        if (key.isNotBlank()) reqBuilder.header("X-Api-Key", key)
        reqBuilder.header("User-Agent", userAgent)

        val response = try {
            client.newCall(reqBuilder.build()).execute()
        } catch (e: Throwable) {
            throw SignException("Sign request failed: ${e.message}", e)
        }

        response.use { resp ->
            val rawBytes = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful) {
                val detail = rawBytes.toString(Charsets.UTF_8).take(300).ifBlank { resp.message }
                throw SignException("Sign provider HTTP ${resp.code}: $detail")
            }

            val contentType = resp.header("Content-Type").orEmpty().lowercase()
            val parsed = try {
                when {
                    contentType.contains("json") || looksLikeJson(rawBytes) ->
                        parseJsonResponse(rawBytes)
                    else ->
                        parseProtobufResponse(rawBytes)
                }
            } catch (e: SignException) {
                throw e
            } catch (e: Throwable) {
                throw SignException(
                    "Sign response parse failed (ct=$contentType len=${rawBytes.size}): " +
                        "${e.message} | text=${previewText(rawBytes)} | hex=${previewHex(rawBytes)}",
                    e,
                )
            }

            // Cookies may arrive either as an `x-set-tt-cookie` response header
            // (protobuf endpoint) or inside the JSON body.
            val cookieHeader = resp.header("x-set-tt-cookie")
                ?: parsed.cookieHeader
                ?: throw SignException("Sign response missing cookies")

            val pushServer = parsed.wsUrl?.takeIf { it.isNotBlank() }
                ?: throw SignException("Sign response missing pushServer (preview=${rawBytes.toString(Charsets.UTF_8).take(200)})")

            // Build the full URL the same way the official tiktok-live-connector
            // does: the static set of TikTok web-client params + dynamic values
            // (compress, room_id, cursor, internal_ext) + the routeParams
            // returned by the sign server. version_code is intentionally listed
            // twice — TikTok's web bundle does the same and uses the second
            // (later) appearance which is the modern value.
            val lastRtt = (100 + (Math.random() * 100).toInt()).toString()
            val params = linkedMapOf<String, String>().apply {
                put("version_code", "180800")
                put("aid", "1988")
                put("app_language", "en-US")
                put("app_name", "tiktok_web")
                put("browser_language", "en-US")
                put("browser_name", "Mozilla")
                put("browser_online", "true")
                put("browser_platform", "Win32")
                put("browser_version", "5.0 (Windows)")
                put("client_enter", "1")
                put("cookie_enabled", "true")
                put("cursor", parsed.cursor.orEmpty())
                put("device_platform", "web")
                put("did_rule", "3")
                put("heartbeat_duration", "0")
                put("history_comment_count", "6")
                put("identity", "audience")
                put("internal_ext", parsed.internalExt.orEmpty())
                put("last_rtt", lastRtt)
                put("live_id", "12")
                put("resp_content_type", "protobuf")
                put("room_id", roomId)
                put("screen_height", "1080")
                put("screen_width", "1920")
                put("sup_ws_ds_opt", "1")
                put("tz_name", "Etc/GMT")
                put("update_version_code", "2.0.0")
                put("webcast_language", "en-US")
                put("ws_direct", "0")
                put("compress", "gzip")
                // routeParams from the sign server (overrides any default).
                for ((k, v) in parsed.wsParams) {
                    if (v.isNotBlank()) put(k, v)
                }
                // Append-parameter from defaults — overrides version_code
                // with the modern web-bundle value.
                put("version_code", "270000")
            }

            val signedUrl = appendWsParams(pushServer, params)

            SignResult(
                wsUrl = signedUrl,
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "Origin" to "https://www.tiktok.com",
                ),
                cookieHeader = cookieHeader,
                initialResponse = parsed.initialResponse,
            )
        }
    }

    private data class Parsed(
        val wsUrl: String?,
        val wsParams: Map<String, String>,
        val cursor: String?,
        val internalExt: String?,
        val cookieHeader: String?,
        val initialResponse: WebcastResponse?,
    )

    private fun looksLikeJson(bytes: ByteArray): Boolean {
        for (b in bytes) {
            when (b.toInt().toChar()) {
                ' ', '\n', '\r', '\t' -> continue
                '{', '[' -> return true
                else -> return false
            }
        }
        return false
    }

    private fun parseProtobufResponse(bytes: ByteArray): Parsed {
        val r = ProtoMessageFetchResult.parse(bytes)
        return Parsed(
            wsUrl = r.pushServer,
            wsParams = r.routeParams,
            cursor = r.cursor,
            internalExt = r.internalExt,
            cookieHeader = null,
            initialResponse = r.toWebcastResponse(),
        )
    }

    private fun parseJsonResponse(bytes: ByteArray): Parsed {
        val json = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrElse {
            throw SignException("Sign response is neither protobuf nor JSON: ${bytes.toString(Charsets.UTF_8).take(200)}", it)
        }
        // The response sometimes wraps the real data inside a `data` or `result` key.
        val root: JSONObject = json.optJSONObject("data")
            ?: json.optJSONObject("result")
            ?: json

        val wsUrl = root.optString("wsUrl").takeIf { it.isNotBlank() }
            ?: root.optString("ws_url").takeIf { it.isNotBlank() }
            ?: root.optString("signed_url").takeIf { it.isNotBlank() }

        val wsParams = mutableMapOf<String, String>()
        root.optJSONObject("wsParams")?.keys()?.forEachRemaining { k ->
            wsParams[k] = root.optJSONObject("wsParams")!!.optString(k)
        }
        root.optJSONObject("ws_params")?.keys()?.forEachRemaining { k ->
            wsParams[k] = root.optJSONObject("ws_params")!!.optString(k)
        }

        val cookieHeader: String? = root.optString("cookies").takeIf { it.isNotBlank() }
            ?: root.optJSONObject("cookies")?.let { obj ->
                obj.keys().asSequence().joinToString("; ") { k -> "$k=${obj.optString(k)}" }
            }

        // Some servers also embed an initial message batch as base64 protobuf.
        val initialResponse: WebcastResponse? = root.optString("fetchResult")
            .takeIf { it.isNotBlank() }
            ?.let { b64 ->
                runCatching {
                    val raw = Base64.decode(b64, Base64.DEFAULT)
                    ProtoMessageFetchResult.parse(raw).toWebcastResponse()
                }.getOrNull()
            }

        return Parsed(
            wsUrl = wsUrl,
            wsParams = wsParams,
            cursor = root.optString("cursor").takeIf { it.isNotBlank() },
            internalExt = root.optString("internalExt").takeIf { it.isNotBlank() }
                ?: root.optString("internal_ext").takeIf { it.isNotBlank() },
            cookieHeader = cookieHeader,
            initialResponse = initialResponse,
        )
    }

    private fun appendWsParams(url: String, params: Map<String, String>): String {
        if (params.isEmpty()) return url
        val sep = if (url.contains('?')) '&' else '?'
        return url + sep + params.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, Charsets.UTF_8.name())

    private fun previewText(bytes: ByteArray): String {
        val s = bytes.toString(Charsets.UTF_8).take(200)
        // Replace non-printable so logcat / Compose text stays readable.
        return buildString(s.length) {
            for (c in s) append(if (c.code in 32..126 || c == '\n' || c == '\t') c else '.')
        }
    }

    private fun previewHex(bytes: ByteArray): String {
        val n = minOf(64, bytes.size)
        val sb = StringBuilder(n * 2)
        for (i in 0 until n) {
            val b = bytes[i].toInt() and 0xFF
            if (b < 0x10) sb.append('0')
            sb.append(Integer.toHexString(b))
        }
        if (bytes.size > n) sb.append("...")
        return sb.toString()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://tiktok.eulerstream.com"
        const val CLIENT_NAME = "ttlive-node"
    }
}
