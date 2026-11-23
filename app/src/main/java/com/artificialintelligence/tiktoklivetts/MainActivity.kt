package com.artificialintelligence.tiktoklivetts

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val GITHUB_RELEASE_API = "https://api.github.com/repos/kelio554/TikTokLiveTTS-Demo/releases/latest"
private val Ink = Color(0xFF171923)
private val Muted = Color(0xFF777D8C)
private val Accent = Color(0xFF7357F6)
private val Canvas = Color(0xFFF5F6FA)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady by mutableStateOf(false)
    private var updateRequired by mutableStateOf(false)
    private var updateUrl by mutableStateOf("")
    private var updateVersion by mutableStateOf("")
    private var updateMessage by mutableStateOf("กำลังตรวจสอบเวอร์ชัน…")
    private var downloadStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Canvas, surface = Color.White, onSurface = Ink)) {
                HomeScreen(ttsReady = ttsReady, onSpeak = { text ->
                    if (ttsReady && text.isNotBlank() && !updateRequired) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts-preview")
                })
                if (updateRequired) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("จำเป็นต้องอัปเดตแอป") },
                        text = { Text("พบ TikTokLiveTTS เวอร์ชันใหม่ $updateVersion กรุณาติดตั้งก่อนใช้งานต่อ\n\n$updateMessage") },
                        confirmButton = {
                            Button(onClick = { downloadLatestApk() }, enabled = updateUrl.isNotBlank()) {
                                Text("ดาวน์โหลดและอัปเดต")
                            }
                        },
                        dismissButton = { Text("บังคับอัปเดต • DEMO", color = Muted) }
                    )
                }
            }
        }
        checkForMandatoryUpdate()
    }

    private fun checkForMandatoryUpdate() {
        lifecycleScope.launch {
            try {
                val release = withContext(Dispatchers.IO) {
                    val connection = (URL(GITHUB_RELEASE_API).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 10000
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "TikTokLiveTTS-Demo")
                    }
                    try {
                        if (connection.responseCode !in 200..299) error("GitHub HTTP ${connection.responseCode}")
                        JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    } finally { connection.disconnect() }
                }
                val tag = release.optString("tag_name").removePrefix("v")
                val remoteCode = tag.toIntOrNull() ?: return@launch
                if (remoteCode > BuildConfig.VERSION_CODE) {
                    val assets = release.optJSONArray("assets")
                    for (i in 0 until (assets?.length() ?: 0)) {
                        val asset = assets!!.getJSONObject(i)
                        if (asset.optString("name") == "TikTokLiveTTS-demo.apk") {
                            updateUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                    updateVersion = tag
                    updateMessage = if (updateUrl.isBlank()) "พบเวอร์ชันใหม่ แต่ยังไม่พบไฟล์ APK ใน GitHub Release" else "กำลังบังคับอัปเดตจาก GitHub Releases"
                    updateRequired = true
                }
            } catch (_: Exception) {
                // If GitHub cannot be reached, keep the demo usable; retry next launch.
            }
        }
    }

    private fun downloadLatestApk() {
        if (downloadStarted || updateUrl.isBlank()) return
        try {
            val request = DownloadManager.Request(Uri.parse(updateUrl))
                .setTitle("TikTokLiveTTS DEMO update")
                .setDescription("กำลังดาวน์โหลดเวอร์ชัน $updateVersion")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TikTokLiveTTS-demo-update.apk")
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            downloadStarted = true
            updateMessage = "เริ่มดาวน์โหลดแล้ว แตะการแจ้งเตือนเมื่อดาวน์โหลดเสร็จเพื่อติดตั้ง APK"
        } catch (_: Exception) {
            updateMessage = "ดาวน์โหลดไม่สำเร็จ กรุณาลองใหม่อีกครั้ง"
            downloadStarted = false
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale("th", "TH")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}

@Composable
private fun HomeScreen(ttsReady: Boolean, onSpeak: (String) -> Unit) {
    var sample by remember { mutableStateOf("สวัสดี ยินดีต้อนรับเข้าสู่ไลฟ์!") }
    var room by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TikTokLiveTTS", fontSize = 25.sp, color = Ink)
                Text("LIVE comment voice assistant", color = Muted, fontSize = 13.sp)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFE8B2)) {
                Text("DEMO", color = Color(0xFF815300), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Surface(shape = RoundedCornerShape(14.dp), color = Color.White) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Accent, modifier = Modifier.padding(12.dp))
            }
        }
        Surface(shape = RoundedCornerShape(22.dp), color = Ink) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("สถานะระบบ • DEMO", color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
                Text("พร้อมทดสอบเสียง", color = Color.White, fontSize = 22.sp)
                Text(if (ttsReady) "ระบบอ่านข้อความของ Android พร้อมใช้งาน" else "กำลังเตรียมระบบเสียง…", color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeUp, null, tint = Color(0xFF9BE7C4))
                    Spacer(Modifier.width(7.dp))
                    Text(if (ttsReady) "TTS พร้อมใช้งาน" else "TTS ยังไม่พร้อม", color = Color.White)
                }
            }
        }
        SectionTitle("ทดลองอ่านข้อความ")
        OutlinedTextField(value = sample, onValueChange = { sample = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("ข้อความที่จะให้อ่าน") }, shape = RoundedCornerShape(16.dp))
        Button(onClick = { onSpeak(sample) }, enabled = ttsReady, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) {
            Icon(Icons.Default.Mic, null); Spacer(Modifier.width(8.dp)); Text("ทดลองอ่านออกเสียง")
        }
        SectionTitle("เชื่อมต่อ TikTok LIVE")
        OutlinedTextField(value = room, onValueChange = { room = it }, modifier = Modifier.fillMaxWidth(), label = { Text("ชื่อผู้ใช้ LIVE") }, placeholder = { Text("เช่น username") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        OutlinedButton(onClick = { }, enabled = room.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) { Text("เตรียมเชื่อมต่อ LIVE") }
        Text("DEMO: หน้าจอทดสอบยังไม่เชื่อมต่อระบบรับแชตจริง", color = Muted, fontSize = 12.sp)
        SectionTitle("โหมดทดลอง")
        Text("ข้ามหน้า Login สำหรับ DEMO เท่านั้น • ไม่ได้เปิดสิทธิ์ License หรือฟังก์ชันระบบจริง", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Text("TikTokLiveTTS • DEMO 1.0.0", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Ink, fontSize = 17.sp)
}
