package net.mkhalil.taktik.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.mkhalil.taktik.data.repo.SettingsRepository
import net.mkhalil.taktik.di.TaktikViewModels

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel(factory = TaktikViewModels.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    var rateText by remember(s.coinToUsd) { mutableStateOf(s.coinToUsd.toString()) }
    var signKey by remember(s.signApiKey) { mutableStateOf(s.signApiKey) }
    var signUrl by remember(s.signBaseUrl) { mutableStateOf(s.signBaseUrl) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // Live source mode
        SettingsCard(
            icon = Icons.Default.SwapHoriz,
            title = "Live source",
            subtitle = "Mock emits synthetic events for testing the UI. WebCast connects to a real TikTok stream (requires a free Eulerstream API key).",
        ) {
            val options = listOf(SettingsRepository.LiveSourceMode.MOCK, SettingsRepository.LiveSourceMode.WEBCAST)
            val labels = listOf("Mock", "WebCast")
            val selected = options.indexOf(s.liveSourceMode).coerceAtLeast(0)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = i == selected,
                        onClick = { vm.setLiveSourceMode(options[i]) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                    ) { Text(label) }
                }
            }
        }

        // Sign provider config (only shown when WebCast)
        if (s.liveSourceMode == SettingsRepository.LiveSourceMode.WEBCAST) {
            SettingsCard(
                icon = Icons.Default.Cloud,
                title = "Sign provider",
                subtitle = "WebCast needs a TikTok signing service. Get a free API key at eulerstream.com → dashboard. Leave URL blank to use the default Eulerstream endpoint.",
            ) {
                OutlinedTextField(
                    value = signUrl,
                    onValueChange = { signUrl = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("Sign URL (blank = default Eulerstream)") },
                    placeholder = { Text("https://tiktok.eulerstream.com") },
                )
                OutlinedTextField(
                    value = signKey,
                    onValueChange = { signKey = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    label = { Text("Eulerstream API key (required)") },
                    leadingIcon = { Icon(Icons.Default.Key, null) },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Button(
                    onClick = {
                        vm.setSignBaseUrl(signUrl)
                        vm.setSignApiKey(signKey)
                    },
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Save sign config") }
            }
        }

        // Coin rate
        SettingsCard(icon = Icons.Default.AttachMoney, title = "Coin → USD rate", subtitle = "Used to estimate gift earnings") {
            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                label = { Text("USD per coin") },
                supportingText = {
                    val parsed = rateText.toDoubleOrNull()
                    Text(
                        if (parsed != null && parsed >= 0) "Saved: $parsed" else "Enter a positive number",
                        color = if (parsed != null && parsed >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                },
            )
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Button(
                onClick = { rateText.toDoubleOrNull()?.takeIf { it >= 0 }?.let(vm::setCoinRate) },
                shape = RoundedCornerShape(12.dp),
            ) { Text("Save rate") }
        }

        // Native Android TTS
        ToggleRow(
            icon = Icons.Default.VolumeUp,
            title = "อ่านคอมเมนต์ด้วยเสียง Android",
            subtitle = "ใช้ Text-to-Speech ที่ติดตั้งในเครื่อง ไม่ส่งข้อความไปสร้างเสียงบนคลาวด์",
            checked = s.ttsEnabled,
            onCheckedChange = vm::setTtsEnabled,
        )

        // Background tracking
        ToggleRow(
            icon = Icons.Default.Notifications,
            title = "Background tracking",
            subtitle = "Keep recording when the app is backgrounded (shows a foreground notification)",
            checked = s.backgroundTracking,
            onCheckedChange = vm::setBackgroundTracking,
        )

        // Auto reconnect
        ToggleRow(
            icon = Icons.Default.Replay,
            title = "Auto-reconnect",
            subtitle = "Automatically reconnect when a stream restarts",
            checked = s.autoReconnect,
            onCheckedChange = vm::setAutoReconnect,
        )

        // Theme
        SettingsCard(icon = Icons.Default.DarkMode, title = "Appearance", subtitle = "Theme used across the app") {
            val options = listOf<Boolean?>(null, false, true)
            val labels = listOf("System", "Light", "Dark")
            val selectedIndex = options.indexOf(s.darkTheme).coerceAtLeast(0)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                labels.forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = i == selectedIndex,
                        onClick = { vm.setDarkTheme(options[i]) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = labels.size),
                    ) { Text(label) }
                }
            }
        }

        SettingsCard(
            icon = Icons.Default.Code,
            title = "About",
            subtitle = "TikTok Live TTS — อ่านคอมเมนต์ TikTok LIVE ด้วยเสียงของ Android",
        ) {
            Text(
                "Developed by Mohamad Khalil",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "• Mock mode: synthetic events, works offline, perfect for trying the UI.\n" +
                    "• WebCast mode: real TikTok data via Eulerstream's signing service.\n" +
                    "  Sign up at eulerstream.com (free tier available) to get an API key.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
