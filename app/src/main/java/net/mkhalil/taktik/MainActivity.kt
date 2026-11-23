package net.mkhalil.taktik

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
private const val APK_ASSET = "TikTokLiveTTS-demo.apk"

class MainActivity : ComponentActivity() {
    private var updateRequired by mutableStateOf(false)
    private var updateUrl by mutableStateOf("")
    private var updateVersion by mutableStateOf("")
    private var updateMessage by mutableStateOf("พบเวอร์ชันใหม่ กรุณาอัปเดตก่อนใช้งาน")
    private var downloadId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TaktikTheme {
                Box {
                    TaktikNavHost()
                    Surface(modifier = Modifier.align(Alignment.TopEnd).padding(10.dp), color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                        Text("DEMO", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, style = MaterialTheme.typography.labelSmall)
                    }
                    if (updateRequired) AlertDialog(
                        onDismissRequest = { },
                        title = { Text("จำเป็นต้องอัปเดต TikTokLiveTTS") },
                        text = { Text("พบเวอร์ชัน $updateVersion ใหม่กว่าเวอร์ชันนี้ กรุณาดาวน์โหลดและติดตั้งก่อนใช้งานต่อ\n\n$updateMessage") },
                        confirmButton = { Button(onClick = { downloadLatestApk() }, enabled = updateUrl.isNotBlank()) { Text("ดาวน์โหลดอัปเดต") } },
                    )
                }
            }
        }
        checkForUpdate()
    }

    private fun checkForUpdate() {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val c = (URL(RELEASE_API).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 12000
                        readTimeout = 12000
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "TikTokLiveTTS-Android")
                    }
                    try {
                        if (c.responseCode !in 200..299) error("GitHub HTTP " + c.responseCode)
                        JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    } finally { c.disconnect() }
                }
                val tag = result.optString("tag_name").removePrefix("v")
                val remoteCode = tag.substringBefore('-').toIntOrNull() ?: return@launch
                if (remoteCode > BuildConfig.VERSION_CODE) {
                    val assets = result.optJSONArray("assets")
                    for (i in 0 until (assets?.length() ?: 0)) {
                        val asset = assets!!.getJSONObject(i)
                        if (asset.optString("name") == APK_ASSET) { updateUrl = asset.optString("browser_download_url"); break }
                    }
                    updateVersion = tag
                    updateMessage = if (updateUrl.isBlank()) "พบเวอร์ชันใหม่ แต่ Release ยังไม่มีไฟล์ APK" else "ตรวจพบอัปเดตจาก GitHub Releases"
                    updateRequired = true
                }
            } catch (e: Exception) {
                android.util.Log.e("TikTokLiveTTS.Update", "Update check failed", e)
            }
        }
    }

    private fun downloadLatestApk() {
        if (updateUrl.isBlank() || downloadId != null) return
        try {
            val request = DownloadManager.Request(Uri.parse(updateUrl))
                .setTitle("TikTokLiveTTS update")
                .setDescription("กำลังดาวน์โหลดเวอร์ชัน $updateVersion")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, APK_ASSET)
            downloadId = (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            updateMessage = "ดาวน์โหลดแล้ว แตะการแจ้งเตือนเมื่อเสร็จเพื่อติดตั้ง (Android จะขออนุญาตติดตั้ง)"
        } catch (e: Exception) {
            downloadId = null
            updateMessage = "ดาวน์โหลดไม่สำเร็จ: " + e.message
        }
    }
}
