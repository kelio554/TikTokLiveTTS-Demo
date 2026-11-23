package net.mkhalil.taktik.data.live.webcast

/**
 * Decoder for Eulerstream's `ProtoMessageFetchResult` — the protobuf returned
 * by `GET /webcast/fetch`. Schema is identical to the proto used by the
 * official `tiktok-live-connector` (TS) and `TikTokLive` (Py) clients:
 *
 *   message ProtoMessageFetchResult {
 *     repeated Message       messages       = 1;
 *     string                 cursor         = 2;
 *     int64                  fetchInterval  = 3;
 *     int64                  now            = 4;
 *     string                 internalExt    = 5;
 *     int32                  fetchType      = 6;
 *     map<string,string>     routeParams    = 7;   // <-- signing params
 *     int64                  heartbeatDur   = 8;
 *     bool                   needAck        = 9;
 *     string                 pushServer     = 10;  // <-- WSS server URL
 *     bool                   isFirst        = 11;
 *     ...
 *   }
 *
 * The client then builds the WebSocket URL as:
 *     pushServer + "?compress=gzip&room_id=...&cursor=...&internal_ext=..."
 *                  + "&<routeParams>"
 */
data class ProtoMessageFetchResult(
    val cursor: String,
    val needsAck: Boolean,
    val internalExt: String,
    val messages: List<WebcastResponse.RawMessage>,
    /** Field 10 — server URL only (no query string). */
    val pushServer: String?,
    /** Field 7 — signing params to append as the query string. */
    val routeParams: Map<String, String>,
) {
    fun toWebcastResponse(): WebcastResponse =
        WebcastResponse(cursor, needsAck, internalExt, messages)

    companion object {
        fun parse(bytes: ByteArray): ProtoMessageFetchResult {
            val r = ProtoReader(bytes)
            var cursor = ""; var needsAck = false; var internalExt = ""
            val messages = mutableListOf<WebcastResponse.RawMessage>()
            var pushServer: String? = null
            val routeParams = linkedMapOf<String, String>()

            // Be lenient: if a single field is malformed, stop walking the
            // outer envelope but keep whatever we already parsed (we mostly
            // care about pushServer + routeParams + initial messages).
            try {
                while (r.hasMore()) {
                    val (field, wire) = r.readTag()
                    when (field) {
                        1 -> {
                            val msgBytes = r.readBytes()
                            runCatching { parseMessage(msgBytes) }
                                .getOrNull()
                                ?.takeIf { it.method.isNotEmpty() }
                                ?.let { messages += it }
                        }
                        2 -> cursor = readStringForWire(r, wire)
                        5 -> internalExt = readStringForWire(r, wire)
                        7 -> {
                            // routeParams map: each entry is a length-delimited
                            // sub-message with { key=1, value=2 }.
                            val entry = runCatching { parseMapEntry(r.readBytes()) }.getOrNull()
                            if (entry != null && entry.first.isNotEmpty()) {
                                routeParams[entry.first] = entry.second
                            }
                        }
                        9 -> needsAck = r.readVarint() != 0L
                        10 -> pushServer = readStringForWire(r, wire)
                        else -> r.skip(wire)
                    }
                }
            } catch (_: Throwable) {
                if (pushServer.isNullOrBlank()) {
                    pushServer = scanForPushServer(bytes)
                }
            }

            // Always run a fallback scan for routeParams when strict parsing
            // didn't find any. Strict parsing may finish "successfully" yet
            // leave the map empty because of a wire-format quirk in one of
            // the map entries; without those params TikTok refuses the WS
            // upgrade so we'd rather have them via scan than not at all.
            if (routeParams.isEmpty()) {
                routeParams.putAll(scanForRouteParams(bytes))
            }

            if (pushServer.isNullOrBlank()) {
                throw IllegalStateException(
                    "Truncated at byte ${r.position()}/${r.size()}: pushServer not found",
                )
            }

            return ProtoMessageFetchResult(
                cursor = cursor,
                needsAck = needsAck,
                internalExt = internalExt,
                messages = messages,
                pushServer = pushServer,
                routeParams = routeParams,
            )
        }

        private fun parseMessage(msgBytes: ByteArray): WebcastResponse.RawMessage {
            val sub = ProtoReader(msgBytes)
            var method = ""
            var payload: ByteArray = ByteArray(0)
            while (sub.hasMore()) {
                val (sf, sw) = sub.readTag()
                when (sf) {
                    1 -> method = readStringForWire(sub, sw)
                    2 -> payload = if (sw == 2) sub.readBytes() else { sub.skip(sw); payload }
                    else -> sub.skip(sw)
                }
            }
            return WebcastResponse.RawMessage(method, payload)
        }

        private fun parseMapEntry(entryBytes: ByteArray): Pair<String, String> {
            val sub = ProtoReader(entryBytes)
            var k = ""; var v = ""
            while (sub.hasMore()) {
                val (sf, sw) = sub.readTag()
                when (sf) {
                    1 -> k = readStringForWire(sub, sw)
                    2 -> v = readStringForWire(sub, sw)
                    else -> sub.skip(sw)
                }
            }
            return k to v
        }

        private fun readStringForWire(r: ProtoReader, wire: Int): String =
            if (wire == 2) r.readString() else { r.skip(wire); "" }

        /**
         * Linear scan for `wss://...` ascii in the response. `pushServer` is
         * field 10 of the envelope: `0x52 <varint length> <utf8 bytes>`.
         * We can't scan-forward by "printable ASCII" because the next
         * protobuf tag byte is also printable, so we'd overshoot. Instead,
         * find "wss://", walk backwards looking for the field-10 tag (0x52)
         * within the previous 1-5 bytes, then trust the varint length.
         */
        private fun scanForPushServer(bytes: ByteArray): String? {
            val needle = "wss://".toByteArray(Charsets.US_ASCII)
            var i = 0
            outer@ while (i <= bytes.size - needle.size) {
                for (j in needle.indices) {
                    if (bytes[i + j] != needle[j]) { i++; continue@outer }
                }
                // Found "wss://" at i. Look back 1..5 bytes for 0x52 (field 10 wire 2).
                for (back in 1..5) {
                    val tagPos = i - back
                    if (tagPos < 0) break
                    if (bytes[tagPos] != 0x52.toByte()) continue
                    val (len, lenSize) = readVarintAt(bytes, tagPos + 1) ?: continue
                    if (lenSize != back - 1) continue
                    if (len < needle.size || tagPos + 1 + lenSize + len > bytes.size) continue
                    return String(bytes, i, len, Charsets.UTF_8)
                }
                i++
            }
            return null
        }

        /**
         * Scan for `routeParams` map entries (field 7 of the envelope).
         * Each entry is `0x3A <varint length> <map entry submsg>`,
         * and the submsg is `0x0A <klen> <key> 0x12 <vlen> <value>`.
         */
        private fun scanForRouteParams(bytes: ByteArray): Map<String, String> {
            val out = linkedMapOf<String, String>()
            var i = 0
            while (i < bytes.size) {
                if (bytes[i] != 0x3A.toByte()) { i++; continue }
                val (entryLen, lenSize) = readVarintAt(bytes, i + 1) ?: run { i++; continue }
                val entryStart = i + 1 + lenSize
                val entryEnd = entryStart + entryLen
                if (entryLen <= 0 || entryEnd > bytes.size) { i++; continue }
                val k = readMapField(bytes, entryStart, entryEnd, 0x0A.toByte())
                val v = readMapField(bytes, entryStart, entryEnd, 0x12.toByte())
                if (k != null && v != null && k.isNotEmpty()) out[k] = v
                i = entryEnd
            }
            return out
        }

        private fun readMapField(bytes: ByteArray, start: Int, end: Int, tag: Byte): String? {
            var p = start
            while (p < end) {
                if (bytes[p] != tag) {
                    // Skip unknown field: tag (assume 1 byte) + varint length + payload (wire 2).
                    val wt = (bytes[p].toInt() and 0x07)
                    p++
                    when (wt) {
                        0 -> { val (_, s) = readVarintAt(bytes, p) ?: return null; p += s }
                        2 -> {
                            val (l, s) = readVarintAt(bytes, p) ?: return null
                            p += s + l
                        }
                        else -> return null
                    }
                    continue
                }
                val (len, lenSize) = readVarintAt(bytes, p + 1) ?: return null
                val valStart = p + 1 + lenSize
                if (valStart + len > end) return null
                return String(bytes, valStart, len, Charsets.UTF_8)
            }
            return null
        }

        /** Read varint starting at [pos]; returns (value, bytesConsumed) or null on failure. */
        private fun readVarintAt(bytes: ByteArray, pos: Int): Pair<Int, Int>? {
            var result = 0L
            var shift = 0
            var p = pos
            while (p < bytes.size) {
                val b = bytes[p].toInt()
                result = result or ((b and 0x7F).toLong() shl shift)
                p++
                if (b and 0x80 == 0) return result.toInt() to (p - pos)
                shift += 7
                if (shift >= 64) return null
            }
            return null
        }
    }
}
