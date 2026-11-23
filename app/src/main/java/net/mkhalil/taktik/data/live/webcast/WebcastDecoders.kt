package net.mkhalil.taktik.data.live.webcast

import net.mkhalil.taktik.domain.model.LiveEvent
import net.mkhalil.taktik.domain.model.LiveRoomInfo
import net.mkhalil.taktik.domain.model.Viewer

/**
 * Decoders for individual WebCast message types.
 *
 * --- READ BEFORE EDITING ---
 *
 * TikTok WebCast messages are protobuf-encoded. Their schemas are not public;
 * the field numbers used here come from the widely-replicated schemas in
 * [TikTokLive (Python)](https://github.com/isaackogan/TikTokLive) and
 * [TikTok-Live-Java](https://github.com/jwdeveloper/TikTok-Live-Java), which
 * are stable across most TikTok releases.
 *
 * If TikTok ships a schema change, the symptom is "events come through but
 * fields are blank / wrong". To fix: capture a live frame on a rooted device
 * via mitm or with the Eulerstream debugger, dump the raw `payload` bytes,
 * and adjust the field numbers below.
 *
 * Each decoder is intentionally lenient: it accepts unknown fields and only
 * trusts the field numbers it knows. Worst case is an event with a partially-
 * populated [Viewer]; the rest of the app handles that gracefully.
 */
object WebcastDecoders {

    /** Routes a raw message to a [LiveEvent], or null if we don't care about it. */
    fun decode(method: String, payload: ByteArray, nowMs: Long): LiveEvent? {
        return when (method) {
            "WebcastChatMessage" -> decodeChat(payload, nowMs)
            "WebcastGiftMessage" -> decodeGift(payload, nowMs)
            "WebcastLikeMessage" -> decodeLike(payload, nowMs)
            "WebcastSocialMessage" -> decodeSocial(payload, nowMs)
            "WebcastMemberMessage" -> decodeMember(payload, nowMs)
            // Other useful methods we don't yet surface as user-visible events:
            //   WebcastRoomUserSeqMessage   – viewer count + top viewers
            //   WebcastControlMessage       – stream end / pause
            //   WebcastLiveIntroMessage     – host intro/announcement
            //   WebcastSubNotifyMessage     – subscription
            //   WebcastQuestionNewMessage   – pinned Q&A
            else -> null
        }
    }

    /**
     * Decode a `WebcastRoomUserSeqMessage` to a [LiveRoomInfo]. Schema from
     * tiktok-live-proto/v3:
     *   CommonMessageData common      = 1
     *   repeated Contributor ranks    = 2
     *   int64             total       = 3   (current viewer count)
     *   string            popStr      = 4
     *   repeated Contributor seats    = 5
     *   int64             popularity  = 6
     *   int64             totalUser   = 7   (lifetime viewers since live started)
     *   int64             anonymous   = 8
     */
    fun decodeRoomUserSeq(payload: ByteArray): LiveRoomInfo? {
        val r = ProtoReader(payload)
        var viewerCount: Int? = null
        var totalViewers: Long? = null
        while (r.hasMore()) {
            val (field, wire) = r.readTag()
            when {
                field == 3 && wire == 0 -> viewerCount = r.readVarint().toInt()
                field == 7 && wire == 0 -> totalViewers = r.readVarint()
                else -> r.skip(wire)
            }
        }
        return if (viewerCount != null || totalViewers != null) {
            LiveRoomInfo(viewerCount = viewerCount, totalViewers = totalViewers)
        } else null
    }

    // ---- Common: User submessage ----

    /**
     * User protobuf schema (best-known stable subset):
     *   uint64  id        = 1
     *   string  nickname  = 3
     *   ImageStruct avatarThumb = 4    (nested: list<string> urls @ field 1)
     *   string  signature = 10
     *   bool    verified  = 28
     *   string  uniqueId  = 38         (may differ across TikTok builds)
     */
    private fun parseUser(bytes: ByteArray): Viewer {
        val r = ProtoReader(bytes)
        var id = 0L
        var nickname: String? = null
        var avatar: String? = null
        var uniqueId: String? = null
        var verified = false
        // Track every short string we see so we can recover uniqueId even when
        // the field number drifts; pick the best candidate at the end.
        val stringCandidates = mutableListOf<Pair<Int, String>>()

        while (r.hasMore()) {
            val (field, wire) = r.readTag()
            when (field) {
                1 -> if (wire == 0) id = r.readVarint() else r.skip(wire)
                3 -> if (wire == 2) nickname = r.readString() else r.skip(wire)
                4 -> if (wire == 2) avatar = parseImageStruct(r.readBytes()) else r.skip(wire)
                28 -> if (wire == 0) verified = r.readVarint() != 0L else r.skip(wire)
                38 -> if (wire == 2) uniqueId = r.readString() else r.skip(wire)
                else -> {
                    if (wire == 2) {
                        val raw = r.readBytes()
                        // Heuristic: short ASCII string that looks like a handle.
                        if (raw.size in 2..30 && raw.all { it in 0x21..0x7E }) {
                            stringCandidates += field to String(raw, Charsets.UTF_8)
                        }
                    } else {
                        r.skip(wire)
                    }
                }
            }
        }

        if (uniqueId.isNullOrBlank()) {
            // Fall back to any field looking like a handle: lowercase letters,
            // digits, dots, underscores.
            uniqueId = stringCandidates
                .map { it.second }
                .firstOrNull { it.matches(Regex("^[a-z0-9._]{2,30}$")) }
        }

        return Viewer(
            userId = id.toString(),
            uniqueId = uniqueId ?: id.toString(),
            nickname = nickname,
            avatarUrl = avatar,
            verified = verified,
        )
    }

    /**
     * ImageStruct: repeated string urls @ field 1. Return the first URL.
     */
    private fun parseImageStruct(bytes: ByteArray): String? {
        val r = ProtoReader(bytes)
        while (r.hasMore()) {
            val (field, wire) = r.readTag()
            if (field == 1 && wire == 2) return r.readString()
            r.skip(wire)
        }
        return null
    }

    // ---- Specific message decoders ----

    /**
     * ChatMessage:
     *   Common common = 1   (skip — header)
     *   User user     = 2
     *   string content = 3
     */
    private fun decodeChat(bytes: ByteArray, nowMs: Long): LiveEvent.Comment? {
        val r = ProtoReader(bytes)
        var user: Viewer? = null
        var text: String? = null
        while (r.hasMore()) {
            val (field, wire) = r.readTag()
            when (field) {
                2 -> if (wire == 2) user = parseUser(r.readBytes()) else r.skip(wire)
                3 -> if (wire == 2) text = r.readString() else r.skip(wire)
                else -> r.skip(wire)
            }
        }
        return if (user != null && text != null) LiveEvent.Comment(user, nowMs, text) else null
    }

    /**
     * GiftMessage (modern TikTok web schema, verified against tiktok-live-proto/v3):
     *   CommonMessageData common      = 1
     *   int64             giftId      = 2     (top-level giftId, redundant with gift.id)
     *   int32             repeatCount = 5     (current combo size)
     *   User              user        = 7     (sender)
     *   User              toUser      = 8     (host)
     *   int32             repeatEnd   = 9     (0 = combo in progress, 1 = final tick)
     *   Gift              gift        = 15    { id=5, diamondCount=12, name=16 }
     *
     * We only emit on `repeatEnd != 0` so multi-tap combos get counted once
     * with the final `repeatCount`, matching what TikTokLive Py/TS do. This
     * keeps `total_gift_coins` accurate (otherwise 1+2+3+…+N is summed).
     */
    private fun decodeGift(bytes: ByteArray, nowMs: Long): LiveEvent.Gift? {
        val r = ProtoReader(bytes)
        var user: Viewer? = null
        var giftId = 0L
        var giftName = "Gift"
        var coins = 0
        var count = 1
        var repeatEnd = 0
        while (r.hasMore()) {
            val (field, wire) = r.readTag()
            when (field) {
                2 -> if (wire == 0) giftId = r.readVarint() else r.skip(wire)
                5 -> if (wire == 0) count = r.readVarint().toInt().coerceAtLeast(1) else r.skip(wire)
                7 -> if (wire == 2) user = parseUser(r.readBytes()) else r.skip(wire)
                9 -> if (wire == 0) repeatEnd = r.readVarint().toInt() else r.skip(wire)
                15 -> if (wire == 2) {
                    val sub = ProtoReader(r.readBytes())
                    while (sub.hasMore()) {
                        val (sf, sw) = sub.readTag()
                        when (sf) {
                            5 -> if (sw == 0) {
                                val id = sub.readVarint()
                                if (giftId == 0L) giftId = id
                            } else sub.skip(sw)
                            12 -> if (sw == 0) coins = sub.readVarint().toInt() else sub.skip(sw)
                            16 -> if (sw == 2) giftName = sub.readString() else sub.skip(sw)
                            else -> sub.skip(sw)
                        }
                    }
                } else r.skip(wire)
                else -> r.skip(wire)
            }
        }
        // Suppress combo-in-progress messages — only count when the combo finishes.
        if (repeatEnd == 0) return null
        return if (user != null) LiveEvent.Gift(user, nowMs, giftId, giftName, count, coins) else null
    }

    /**
     * LikeMessage:
     *   Common common = 1
     *   int32 count  = 2
     *   int32 total  = 3
     *   User  user   = 5
     */
    private fun decodeLike(bytes: ByteArray, nowMs: Long): LiveEvent.Like? {
        val r = ProtoReader(bytes)
        var user: Viewer? = null
        var count = 1
        while (r.hasMore()) {
            val (field, wire) = r.readTag()
            when (field) {
                2 -> if (wire == 0) count = r.readVarint().toInt().coerceAtLeast(1) else r.skip(wire)
                5 -> if (wire == 2) user = parseUser(r.readBytes()) else r.skip(wire)
                else -> r.skip(wire)
            }
        }
        return if (user != null) LiveEvent.Like(user, nowMs, count) else null
    }

    /**
     * SocialMessage — follow / share split.
     *   CommonMessageData common = 1    (displayType lives at inner field 5)
     *   User              user   = 2
     *   int64             shareType = 3 (non-zero == share)
     *   int64             action    = 4 (1 = follow per TikTokLive Py)
     *
     * We trust shareType first (definitive), fall back to action == 1 or the
     * displayType string for older bundles.
     */
    private fun decodeSocial(bytes: ByteArray, nowMs: Long): LiveEvent? {
        val r = ProtoReader(bytes)
        var user: Viewer? = null
        var displayType: String? = null
        var shareType = 0L
        var action = 0L
        while (r.hasMore()) {
            val (field, wire) = r.readTag()
            when (field) {
                1 -> if (wire == 2) {
                    val sub = ProtoReader(r.readBytes())
                    while (sub.hasMore()) {
                        val (sf, sw) = sub.readTag()
                        when (sf) {
                            5 -> if (sw == 2) displayType = sub.readString() else sub.skip(sw)
                            else -> sub.skip(sw)
                        }
                    }
                } else r.skip(wire)
                2 -> if (wire == 2) user = parseUser(r.readBytes()) else r.skip(wire)
                3 -> if (wire == 0) shareType = r.readVarint() else r.skip(wire)
                4 -> if (wire == 0) action = r.readVarint() else r.skip(wire)
                else -> r.skip(wire)
            }
        }
        val v = user ?: return null
        return when {
            shareType > 0L -> LiveEvent.Share(v, nowMs)
            action == 1L -> LiveEvent.Follow(v, nowMs)
            displayType?.contains("share", ignoreCase = true) == true -> LiveEvent.Share(v, nowMs)
            displayType?.contains("follow", ignoreCase = true) == true -> LiveEvent.Follow(v, nowMs)
            else -> LiveEvent.Follow(v, nowMs)
        }
    }

    /**
     * MemberMessage (user joined the room):
     *   Common common = 1
     *   User   user   = 2
     *   uint64 viewerCount = 3
     */
    private fun decodeMember(bytes: ByteArray, nowMs: Long): LiveEvent.Join? {
        val r = ProtoReader(bytes)
        var user: Viewer? = null
        while (r.hasMore()) {
            val (field, wire) = r.readTag()
            when (field) {
                2 -> if (wire == 2) user = parseUser(r.readBytes()) else r.skip(wire)
                else -> r.skip(wire)
            }
        }
        return user?.let { LiveEvent.Join(it, nowMs) }
    }
}
