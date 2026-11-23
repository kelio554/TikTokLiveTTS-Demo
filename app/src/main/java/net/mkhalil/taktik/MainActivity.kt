package net.mkhalil.taktik

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mkhalil.taktik.ui.nav.TaktikNavHost
import net.mkhalil.taktik.ui.theme.TaktikTheme
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASE_API = "https://api.github.com/repos/kelio554/TikTokLiveTTS-Demo/releases/latest"

class MainActivity : ComponentActivity() {
    private var updateAvailable by mutableStateOf(false)
    private var updateUrl by mutableStateOf("")
    private var updateVersion by mutableStateOf("")
    private var updateStatus by mutableStateOf("พบเวอร์ชันใหม่ กรุณาอัปเดตก่อนใช้งาน")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaktikTheme {
                Box(Modifier.fillMaxSize()) {
                    TaktikNavHost()
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 10.dp),
                        color = Color(0xFFFFE6A6),
                        shape = MaterialTheme.shapes.small,
                    ) { Text("DEMO", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Color(0xFF694500)) }
                    if (updateAvailable) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("ต้องอัปเดต TikTokLiveTTS") },
                            text = { Text("พบเวอร์ชัน $updateVersion\n$updateStatus") },
                            confirmButton = {
                                TextButton(onClick = { downloadUpdate() }, enabled = updateUrl.isNotBlank()) { Text("ดาวน์โหลด APK") }
                            },
                        )
                    }
                }
            }
        }
        checkForUpdate()
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    val c = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12000
                        readTimeout = 12000
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "TikTokLiveTTS-Android")
                    }
                    try {
                        if (c.responseCode !in 200..299) error("GitHub HTTP ${c.responseCode}")
                        JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    } finally { c.disconnect() }
                }
                val tag = release.optString("tag_name").removePrefix("v")
                val remoteCode = tag.substringBefore('-').toIntOrNull() ?: return@launch
                if (remoteCode > BuildConfig.VERSION_CODE) {
                    val assets = release.optJSONArray("assets")
                    for (i in 0 until (assets?.length() ?: 0)) {
                        val a = assets!!.getJSONObject(i)
                        if (a.optString("name").endsWith(".apk", true)) {
                            updateUrl = a.optString("browser_download_url")
                            break
                        }
                    }
                    updateVersion = tag
                    updateStatus = if (updateUrl.isBlank()) "Release นี้ยังไม่มีไฟล์ APK" else "เวอร์ชันปัจจุบัน ${BuildConfig.VERSION_NAME} ต้องอัปเดตก่อนใช้งาน"
                    updateAvailable = true
                }
            } catch (_: Exception) {
                // Network/API unavailable: keep current app usable and retry next launch.
            }
        }
    }

    private fun downloadUpdate() {
        if (updateUrl.isBlank()) return
        runCatching {
            val request = DownloadManager.Request(Uri.parse(updateUrl))
                .setTitle("TikTokLiveTTS update")
                .setDescription("กำลังดาวน์โหลดเวอร์ชัน $updateVersion")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TikTokLiveTTS-update.apk")
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            updateStatus = "ดาวน์โหลดแล้ว แตะการแจ้งเตือนเพื่อเริ่มติดตั้ง Android อาจขออนุญาตติดตั้งแอป"
        }.onFailure { updateStatus = "ดาวน์โหลดไม่สำเร็จ: ${it.message}" }
    }
}
