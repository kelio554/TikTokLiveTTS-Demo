package net.mkhalil.taktik.data.live.webcast

/**
 * Decoder for the outermost WebSocket frame TikTok sends on a WebCast
 * connection (`WebcastPushFrame` / `WebcastWebsocketMessage`). Schema is
 * stable across libraries (TikTokLive Py, TikTokLiveJava, TikTokLive Sharp).
 *
 * proto:
 *   message PushFrame {
 *     uint64 seqId           = 1;
 *     uint64 logId           = 2;
 *     uint64 service         = 3;
 *     uint64 method          = 4;
 *     repeated Header headers = 5;
 *     string payloadEncoding = 6;
 *     string payloadType     = 7;   // e.g. "msg"
 *     bytes  payload         = 8;   // inner WebcastResponse, possibly gzipped
 *   }
 *   message Header { string key=1; string value=2; }
 */
data class PushFrame(
    val seqId: Long = 0,
    val logId: Long = 0,
    val service: Long = 0,
    val method: Long = 0,
    val headers: Map<String, String> = emptyMap(),
    val payloadEncoding: String = "",
    val payloadType: String = "",
    val payload: ByteArray = EMPTY_BYTES,
) {
    companion object {
        private val EMPTY_BYTES = ByteArray(0)

        fun parse(data: ByteArray): PushFrame {
            val r = ProtoReader(data)
            var seqId = 0L; var logId = 0L; var service = 0L; var method = 0L
            val headers = linkedMapOf<String, String>()
            var payloadEncoding = ""; var payloadType = ""; var payload: ByteArray = EMPTY_BYTES

            while (r.hasMore()) {
                val (field, wire) = r.readTag()
                when (field) {
                    1 -> seqId = r.readVarint()
                    2 -> logId = r.readVarint()
                    3 -> service = r.readVarint()
                    4 -> method = r.readVarint()
                    5 -> {
                        val sub = ProtoReader(r.readBytes())
                        var k = ""; var v = ""
                        while (sub.hasMore()) {
                            val (sf, sw) = sub.readTag()
                            when (sf) {
                                1 -> if (sw == 2) k = sub.readString() else sub.skip(sw)
                                2 -> if (sw == 2) v = sub.readString() else sub.skip(sw)
                                else -> sub.skip(sw)
                            }
                        }
                        if (k.isNotEmpty()) headers[k] = v
                    }
                    6 -> payloadEncoding = r.readString()
                    7 -> payloadType = r.readString()
                    8 -> payload = r.readBytes()
                    else -> r.skip(wire)
                }
            }
            return PushFrame(seqId, logId, service, method, headers, payloadEncoding, payloadType, payload)
        }
    }
}
