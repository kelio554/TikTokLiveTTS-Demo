package net.mkhalil.taktik.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale

/**
 * Fetches the public TikTok profile page and extracts the `region` field.
 *
 * The TikTok web profile page (https://www.tiktok.com/@<handle>) embeds a
 * `<script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">…</script>` JSON blob that
 * contains the user's `userInfo.user.region` (country code, e.g. "MA", "US").
 * We parse that out, then resolve to a human-readable country name.
 */
class TikTokUserApi(
    private val client: OkHttpClient,
) {
    data class Result(
        val uniqueId: String,
        val userId: String? = null,
        val nickname: String? = null,
        val avatarUrl: String? = null,
        val signature: String? = null,
        val followerCount: Long? = null,
        val followingCount: Long? = null,
        val verified: Boolean = false,
        val regionCode: String? = null,
        val region: String? = null,
        val roomId: String? = null,
        val isLive: Boolean = false,
    )

    /**
     * Resolves the current live `roomId` for the given handle. The static
     * `tiktok.com/@<handle>` profile page (used by [fetchProfile]) almost
     * never includes `roomId` even when the user is live — that field is
     * only present on the dedicated `/live` route. So we try, in order:
     *
     *   1. Scrape `tiktok.com/@<handle>/live` and pull
     *      `SIGI_STATE.LiveRoom.liveRoomUserInfo.user.roomId`
     *      (this is what `tiktok-live-connector` uses as method #1).
     *   2. JSON endpoint `tiktok.com/api-live/user/room/?aid=1988&uniqueId=…&sourceType=54`
     *      which returns `data.user.roomId`.
     *
     * Returns `null` only when both sources fail to surface a non-empty roomId.
     */
    suspend fun fetchRoomId(uniqueId: String): String? = withContext(Dispatchers.IO) {
        val handle = uniqueId.trim().removePrefix("@")
        if (handle.isEmpty()) return@withContext null

        scrapeRoomIdFromLivePage(handle)?.let { return@withContext it }
        fetchRoomIdFromApiLive(handle)
    }

    private fun scrapeRoomIdFromLivePage(handle: String): String? {
        val req = Request.Builder()
            .url("https://www.tiktok.com/@$handle/live")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val html = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        }.getOrNull() ?: return null

        // SIGI_STATE is the old key; modern bundles use __UNIVERSAL_DATA_FOR_REHYDRATION__.
        sigiStateRoomId(html)?.let { return it }
        universalDataRoomId(html)?.let { return it }
        return null
    }

    private fun sigiStateRoomId(html: String): String? {
        val marker = "id=\"SIGI_STATE\""
        val tagStart = html.indexOf(marker).takeIf { it >= 0 } ?: return null
        val jsonStart = html.indexOf('>', tagStart).takeIf { it >= 0 }?.plus(1) ?: return null
        val jsonEnd = html.indexOf("</script>", jsonStart).takeIf { it >= 0 } ?: return null
        val root = runCatching { JSONObject(html.substring(jsonStart, jsonEnd)) }.getOrNull() ?: return null
        val liveRoom = root.optJSONObject("LiveRoom")?.optJSONObject("liveRoomUserInfo")
        return liveRoom?.optJSONObject("user")?.optString("roomId")
            ?.takeIf { it.isNotBlank() && it != "0" }
    }

    private fun universalDataRoomId(html: String): String? {
        val json = extractUniversalData(html) ?: return null
        val scope = json.optJSONObject("__DEFAULT_SCOPE__") ?: return null
        // The live page exposes the room under webapp.live-room.
        val liveRoom = scope.optJSONObject("webapp.live-room")
            ?: scope.optJSONObject("webapp.user-detail")
        val liveRoomUserInfo = liveRoom?.optJSONObject("liveRoomUserInfo") ?: liveRoom
        val user = liveRoomUserInfo?.optJSONObject("user")
        return user?.optString("roomId")
            ?.takeIf { it.isNotBlank() && it != "0" }
    }

    private fun fetchRoomIdFromApiLive(handle: String): String? {
        val req = Request.Builder()
            .url(
                "https://www.tiktok.com/api-live/user/room/" +
                    "?aid=1988&uniqueId=$handle&sourceType=54"
            )
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            )
            .header("Accept", "application/json")
            .header("Referer", "https://www.tiktok.com/@$handle/live")
            .build()

        val body = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        }.getOrNull() ?: return null

        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (json.optInt("statusCode", 0) != 0) return null
        return json.optJSONObject("data")?.optJSONObject("user")?.optString("roomId")
            ?.takeIf { it.isNotBlank() && it != "0" }
    }

    suspend fun fetchProfile(uniqueId: String): Result? = withContext(Dispatchers.IO) {
        val handle = uniqueId.trim().removePrefix("@")
        if (handle.isEmpty()) return@withContext null

        val req = Request.Builder()
            .url("https://www.tiktok.com/@$handle")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val html = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                resp.body?.string()
            }
        }.getOrNull() ?: return@withContext null

        val json = extractUniversalData(html) ?: return@withContext null
        val parsed = runCatching { parseResult(handle, json) }.getOrNull() ?: return@withContext null

        // The static profile page often doesn't surface `liveStatus`/`roomId`
        // even when the user is live — that data only renders on the `/live`
        // route or the `api-live/user/room/` JSON endpoint. So if the profile
        // page claims offline, try the live-status fallback before we mislead
        // the Connect tab into showing "OFFLINE".
        if (!parsed.isLive) {
            val roomId = fetchRoomId(handle)
            if (!roomId.isNullOrBlank()) {
                return@withContext parsed.copy(isLive = true, roomId = roomId)
            }
        }
        parsed
    }

    private fun extractUniversalData(html: String): JSONObject? {
        val marker = "id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\""
        val tagStart = html.indexOf(marker).takeIf { it >= 0 } ?: return null
        val jsonStart = html.indexOf('>', tagStart).takeIf { it >= 0 }?.plus(1) ?: return null
        val jsonEnd = html.indexOf("</script>", jsonStart).takeIf { it >= 0 } ?: return null
        return runCatching { JSONObject(html.substring(jsonStart, jsonEnd)) }.getOrNull()
    }

    private fun parseResult(handle: String, root: JSONObject): Result {
        val scope = root.optJSONObject("__DEFAULT_SCOPE__") ?: return Result(uniqueId = handle)
        val userDetail = scope.optJSONObject("webapp.user-detail") ?: return Result(uniqueId = handle)
        val userInfo = userDetail.optJSONObject("userInfo") ?: return Result(uniqueId = handle)
        val user = userInfo.optJSONObject("user")
        val stats = userInfo.optJSONObject("stats")

        val regionCode = user?.optString("region")?.takeIf { it.isNotBlank() && it != "null" }
        val regionName = regionCode?.let { regionNameFromCode(it) }

        return Result(
            uniqueId = user?.optString("uniqueId")?.takeIf { it.isNotBlank() } ?: handle,
            userId = user?.optString("id")?.takeIf { it.isNotBlank() },
            nickname = user?.optString("nickname")?.takeIf { it.isNotBlank() },
            avatarUrl = user?.optString("avatarLarger")
                ?.takeIf { it.isNotBlank() }
                ?: user?.optString("avatarMedium")?.takeIf { it.isNotBlank() },
            signature = user?.optString("signature")?.takeIf { it.isNotBlank() },
            followerCount = stats?.optLong("followerCount", -1L)?.takeIf { it >= 0 },
            followingCount = stats?.optLong("followingCount", -1L)?.takeIf { it >= 0 },
            verified = user?.optBoolean("verified", false) == true,
            regionCode = regionCode,
            region = regionName,
            roomId = user?.optString("roomId")?.takeIf { it.isNotBlank() && it != "0" },
            isLive = (user?.optInt("liveStatus", 0) ?: 0) == 1 ||
                (user?.optString("roomId")?.let { it.isNotBlank() && it != "0" } == true),
        )
    }

    private fun regionNameFromCode(code: String): String? {
        return runCatching { Locale("", code).displayCountry }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != code }
    }
}
