package net.mkhalil.taktik.data.license

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Google Apps Script license check. A key is never accepted from local cache alone. */
class LicenseApi(private val context: Context) {
    enum class Result { VALID, INVALID, UNAVAILABLE }

    suspend fun validate(key: String): Result = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext Result.INVALID
        var connection: HttpURLConnection? = null
        try {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
            val payload = JSONObject().apply {
                put("action", "validateKey")
                put("operation", "validate")
                put("key", key.trim())
                put("licenseKey", key.trim())
                put("apiKey", key.trim())
                put("deviceId", deviceId)
                put("app", "TikTokLiveTTS")
                put("version", "1.0.0")
            }.toString()

            connection = (URL(SCRIPT_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                instanceFollowRedirects = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) return@withContext Result.UNAVAILABLE

            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext Result.UNAVAILABLE
            val error = json.optString("error").uppercase()
            if (json.has("ok") && !json.optBoolean("ok", false) &&
                error !in setOf("INVALID_KEY", "KEY_NOT_FOUND", "KEY_REVOKED", "EXPIRED_KEY")) {
                return@withContext Result.UNAVAILABLE
            }
            val data = json.optJSONObject("data") ?: json.optJSONObject("result") ?: json
            val status = data.optString("status").lowercase()
            val valid = data.optBoolean("valid", false) ||
                data.optBoolean("success", false) ||
                data.optBoolean("active", false) ||
                data.optBoolean("exists", false) ||
                status in setOf("valid", "active", "success", "ok")
            if (valid) Result.VALID
            else if (error in setOf("INVALID_KEY", "KEY_NOT_FOUND", "KEY_REVOKED", "EXPIRED_KEY") || status in setOf("invalid", "revoked", "expired", "not_found")) Result.INVALID
            else Result.UNAVAILABLE
        } catch (_: Exception) {
            Result.UNAVAILABLE
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        // The deployment URL supplied for the existing Google Apps Script API.
        const val SCRIPT_URL = "https://script.google.com/macros/s/AKfycbw_72QzmGtFJF2KMJqyWIk221nJ21FQFfQXti7W2eNi7fWWS5sXw9KixHsXqHBceR8ffQ/exec"
    }
}
