package net.mkhalil.taktik.data.live.webcast

import java.util.zip.GZIPInputStream

/**
 * Decoder for `WebcastResponse` — the inner protobuf payload of a [PushFrame]
 * with `payloadType = "msg"`. The schema is stable:
 *
 *   message WebcastResponse {
 *     repeated Message  messages       = 1;
 *     string            cursor         = 2;
 *     int64             fetchInterval  = 3;
 *     int64             now            = 4;
 *     string            internalExt    = 5;
 *     int32             fetchType      = 6;
 *     ...
 *     bool              needsAck       = 9;
 *     string            heartbeatDuration = 10;
 *     bytes             ack            = 11;
 *   }
 *   message Message {
 *     string method   = 1;  // e.g. "WebcastChatMessage"
 *     bytes  payload  = 2;  // type-specific protobuf bytes
 *     uint64 msgId    = 3;
 *     int32  msgType  = 4;
 *     int64  offset   = 5;
 *   }
 */
data class WebcastResponse(
    val cursor: String,
    val needsAck: Boolean,
    val internalExt: String,
    val messages: List<RawMessage>,
) {
    data class RawMessage(val method: String, val payload: ByteArray)

    companion object {
        /** Parse a `WebcastResponse` from possibly gzip-compressed bytes. */
        fun parse(bytes: ByteArray, gzipped: Boolean): WebcastResponse {
            val raw = if (gzipped) ungzip(bytes) else bytes
            val r = ProtoReader(raw)
            var cursor = ""; var needsAck = false; var internalExt = ""
            val messages = mutableListOf<RawMessage>()

            while (r.hasMore()) {
                val (field, wire) = r.readTag()
                when (field) {
                    1 -> {
                        val sub = ProtoReader(r.readBytes())
                        var method = ""; var payload: ByteArray = ByteArray(0)
                        while (sub.hasMore()) {
                            val (sf, sw) = sub.readTag()
                            when (sf) {
                                1 -> method = sub.readString()
                                2 -> payload = sub.readBytes()
                                else -> sub.skip(sw)
                            }
                        }
                        if (method.isNotEmpty()) messages += RawMessage(method, payload)
                    }
                    2 -> cursor = r.readString()
                    5 -> internalExt = r.readString()
                    9 -> needsAck = r.readVarint() != 0L
                    else -> r.skip(wire)
                }
            }

            return WebcastResponse(cursor, needsAck, internalExt, messages)
        }

        private fun ungzip(bytes: ByteArray): ByteArray =
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    }
}
