package net.mkhalil.taktik.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.mkhalil.taktik.di.TaktikViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.mkhalil.taktik.data.db.entities.EventEntity
import net.mkhalil.taktik.ui.components.Avatar
import net.mkhalil.taktik.ui.components.StatTile
import net.mkhalil.taktik.ui.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerDetailScreen(
    onBack: () -> Unit,
    vm: ViewerDetailViewModel = viewModel(factory = TaktikViewModels.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val v = s.viewer

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(v?.nickname ?: v?.unique_id ?: "Viewer") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = vm::refreshProfile) {
                        Icon(Icons.Default.Refresh, null)
                    }
                },
            )
        }
    ) { pad ->
        if (v == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Viewer not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(url = v.avatar_url, name = v.nickname ?: v.unique_id, size = 64.dp)
                        Column(Modifier.weight(1f)) {
                            Text(v.nickname ?: v.unique_id, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text("@${v.unique_id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val flag = Formatters.regionFlag(v.region_code)
                            val region = v.region ?: v.region_code
                            if (!region.isNullOrBlank()) {
                                Text("$flag  $region", style = MaterialTheme.typography.bodyMedium)
                            }
                            v.follower_count?.let {
                                Text("${Formatters.compactNumber(it)} followers", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile("Comments", "${s.totals.comments}", Icons.Default.Chat, Color(0xFF25F4EE), Modifier.weight(1f))
                    StatTile("Likes", Formatters.compactNumber(s.totals.likes), Icons.Default.Favorite, Color(0xFFFE2C55), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(
                        "Gifts",
                        "${s.totals.gifts}",
                        Icons.Default.MonetizationOn,
                        Color(0xFFFFB020),
                        Modifier.weight(1f),
                        subtitle = "${s.totals.giftCoins} coins · ${Formatters.coinsToUsd(s.totals.giftCoins, s.coinToUsd)}",
                    )
                    StatTile(
                        "Engagement",
                        "${s.totals.follows + s.totals.shares}",
                        Icons.Default.PersonAdd,
                        Color(0xFF7A5AF8),
                        Modifier.weight(1f),
                        subtitle = "${s.totals.follows} follows · ${s.totals.shares} shares",
                    )
                }
            }
            item {
                Text("Activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            items(s.events, key = { it.id }) { e -> ActivityRow(e, s.coinToUsd) }
            if (s.events.isEmpty()) {
                item {
                    Text("No tracked activity for this viewer", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(e: EventEntity, coinUsd: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val (icon, tint) = when (e.type) {
                "COMMENT" -> Icons.Default.Chat to Color(0xFF25F4EE)
                "GIFT" -> Icons.Default.MonetizationOn to Color(0xFFFFB020)
                "LIKE" -> Icons.Default.Favorite to Color(0xFFFE2C55)
                "FOLLOW" -> Icons.Default.PersonAdd to Color(0xFF18C964)
                "SHARE" -> Icons.Default.Share to Color(0xFF7A5AF8)
                else -> Icons.Default.Chat to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (e.type) {
                        "COMMENT" -> e.comment_text.orEmpty()
                        "GIFT" -> "${e.gift_name ?: "Gift"} × ${e.gift_count ?: 1} (${(e.gift_coins ?: 0L) * (e.gift_count ?: 1)} coins · ${Formatters.coinsToUsd((e.gift_coins ?: 0L) * (e.gift_count ?: 1), coinUsd)})"
                        "LIKE" -> "Liked × ${e.like_count ?: 1}"
                        "FOLLOW" -> "Started following"
                        "SHARE" -> "Shared the stream"
                        else -> e.type
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    Formatters.formatDateTime(e.created_at),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}
