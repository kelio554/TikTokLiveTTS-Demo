package net.mkhalil.taktik.ui.session

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import net.mkhalil.taktik.di.TaktikViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.mkhalil.taktik.data.db.entities.EventEntity
import net.mkhalil.taktik.data.export.CsvExporter
import net.mkhalil.taktik.ui.components.ActivityTimelineCard
import net.mkhalil.taktik.ui.components.Avatar
import net.mkhalil.taktik.ui.components.StatTile
import net.mkhalil.taktik.ui.components.TopRegionsCard
import net.mkhalil.taktik.ui.util.Formatters
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onViewerClick: (String) -> Unit,
    vm: SessionDetailViewModel = viewModel(factory = TaktikViewModels.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val session = s.session

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(session?.host_nickname ?: session?.host_unique_id ?: "Session")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    if (session != null) {
                        IconButton(onClick = {
                            scope.launch {
                                val events = vm.loadEventsForExport()
                                val intent = CsvExporter.exportSession(
                                    context = context,
                                    sessionId = vm.sessionId,
                                    hostHandle = session.host_unique_id,
                                    events = events,
                                )
                                context.startActivity(Intent.createChooser(intent, "Export session CSV"))
                            }
                        }) { Icon(Icons.Default.FileDownload, null) }
                    }
                },
            )
        }
    ) { pad ->
        if (session == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Session not found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        var tab by rememberSaveable { mutableIntStateOf(0) }
        val tabs = listOf("Overview", "Comments", "Gifts", "Likes", "Follows", "Shares")

        Column(Modifier.fillMaxSize().padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
                tabs.forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }
            when (tab) {
                0 -> OverviewTab(s, onViewerClick)
                1 -> EventList(s.comments, s.coinToUsd, onViewerClick)
                2 -> EventList(s.gifts, s.coinToUsd, onViewerClick)
                3 -> EventList(s.likes, s.coinToUsd, onViewerClick)
                4 -> EventList(s.follows, s.coinToUsd, onViewerClick)
                5 -> EventList(s.shares, s.coinToUsd, onViewerClick)
            }
        }
    }
}

@Composable
private fun OverviewTab(s: SessionDetailViewModel.UiState, onViewerClick: (String) -> Unit) {
    val session = s.session ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
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
                    Avatar(url = session.host_avatar_url, name = session.host_nickname ?: session.host_unique_id, size = 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(session.host_nickname ?: session.host_unique_id, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text("@${session.host_unique_id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${Formatters.formatDateTime(session.started_at)} · ${Formatters.duration(session.started_at, session.ended_at)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Comments", Formatters.compactNumber(session.total_comments.toLong()), Icons.Default.Chat, Color(0xFF25F4EE), Modifier.weight(1f))
                StatTile("Likes", Formatters.compactNumber(session.total_likes), Icons.Default.Favorite, Color(0xFFFE2C55), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    "Gifts",
                    Formatters.compactNumber(session.total_gifts.toLong()),
                    Icons.Default.MonetizationOn,
                    Color(0xFFFFB020),
                    Modifier.weight(1f),
                    subtitle = "${session.total_gift_coins} coins · ${Formatters.coinsToUsd(session.total_gift_coins, s.coinToUsd)}",
                )
                StatTile(
                    "Engagement",
                    "${session.unique_viewers}",
                    Icons.Default.PersonAdd,
                    Color(0xFF7A5AF8),
                    Modifier.weight(1f),
                    subtitle = "${session.total_follows} follows · ${session.total_shares} shares",
                )
            }
        }
        item { ActivityTimelineCard(s.timeline) }
        item { TopRegionsCard(s.regions) }
    }
}

@Composable
private fun EventList(rows: List<SessionDetailViewModel.EventRow>, coinUsd: Double, onViewerClick: (String) -> Unit) {
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Nothing here for this session", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(rows, key = { it.event.id }) { row -> RowView(row, coinUsd, onViewerClick) }
    }
}

@Composable
private fun RowView(row: SessionDetailViewModel.EventRow, coinUsd: Double, onViewerClick: (String) -> Unit) {
    val e = row.event
    val v = row.viewer
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { m -> v?.user_id?.let { id -> m.clickable { onViewerClick(id) } } ?: m },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(url = v?.avatar_url, name = v?.nickname ?: v?.unique_id)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(v?.nickname ?: v?.unique_id ?: "Unknown", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    val flag = Formatters.regionFlag(v?.region_code)
                    if (flag.isNotEmpty()) Text(flag)
                    Spacer(Modifier.weight(1f))
                    Text(Formatters.formatTime(e.created_at), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Body(e, coinUsd)
            }
        }
    }
}

@Composable
private fun Body(e: EventEntity, coinUsd: Double) {
    when (e.type) {
        "COMMENT" -> Text(e.comment_text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        "GIFT" -> {
            val count = e.gift_count ?: 1
            val coins = (e.gift_coins ?: 0) * count
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.MonetizationOn, null, tint = Color(0xFFFFB020), modifier = Modifier.size(16.dp))
                Text("${e.gift_name ?: "Gift"} × $count", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text("$coins coins · ${Formatters.coinsToUsd(coins, coinUsd)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        "LIKE" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Favorite, null, tint = Color(0xFFFE2C55), modifier = Modifier.size(16.dp))
            Text("Liked × ${e.like_count ?: 1}", style = MaterialTheme.typography.bodyMedium)
        }
        "FOLLOW" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.PersonAdd, null, tint = Color(0xFF18C964), modifier = Modifier.size(16.dp))
            Text("Started following", style = MaterialTheme.typography.bodyMedium)
        }
        "SHARE" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Share, null, tint = Color(0xFF7A5AF8), modifier = Modifier.size(16.dp))
            Text("Shared the stream", style = MaterialTheme.typography.bodyMedium)
        }
        else -> Text(e.type, style = MaterialTheme.typography.bodyMedium)
    }
}
