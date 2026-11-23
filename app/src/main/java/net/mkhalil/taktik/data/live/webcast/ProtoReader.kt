package net.mkhalil.taktik.data.live.webcast

/**
 * Minimal protobuf wire-format reader. Avoids pulling protobuf-javalite for
 * the outer frame parsing where we only need a handful of fields.
 *
 * Reference: https://protobuf.dev/programming-guides/encoding/
 *
 * Wire types:
 *   0 = varint        (int32/int64/uint32/uint64/sint32/sint64/bool/enum)
 *   1 = 64-bit        (fixed64/sfixed64/double)
 *   2 = length-delim  (string/bytes/embedded message/packed repeated)
 *   5 = 32-bit        (fixed32/sfixed32/float)
 */
class ProtoReader(private val buf: ByteArray) {
    private var pos = 0

    fun position(): Int = pos
    fun size(): Int = buf.size

    fun hasMore(): Boolean = pos < buf.size

    /** Returns (fieldNumber, wireType). */
    fun readTag(): Pair<Int, Int> {
        val tag = readVarint().toInt()
        return (tag ushr 3) to (tag and 0x7)
    }

    fun readVarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            check(pos < buf.size) { "Truncated varint" }
            val b = buf[pos].toInt(); pos++
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            check(shift < 64) { "Varint too long" }
        }
    }

    fun readBytes(): ByteArray {
        val len = readVarint().toInt()
        check(len >= 0 && pos + len <= buf.size) { "Bad length-delimited field len=$len" }
        val out = buf.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    fun readString(): String = String(readBytes(), Charsets.UTF_8)

    fun read64(): Long {
        check(pos + 8 <= buf.size) { "Truncated fixed64" }
        var v = 0L
        for (i in 0 until 8) v = v or ((buf[pos + i].toLong() and 0xFF) shl (i * 8))
        pos += 8
        return v
    }

    fun read32(): Int {
        check(pos + 4 <= buf.size) { "Truncated fixed32" }
        var v = 0
        for (i in 0 until 4) v = v or ((buf[pos + i].toInt() and 0xFF) shl (i * 8))
        pos += 4
        return v
    }

    /** Skip a field with the given wire type. Useful for ignoring unknown fields. */
    fun skip(wireType: Int) {
        when (wireType) {
            0 -> { readVarint() }
            1 -> { read64() }
            2 -> { readBytes() }
            5 -> { read32() }
            else -> error("Unsupported wire type: $wireType")
        }
    }
}
