package net.mkhalil.taktik.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import net.mkhalil.taktik.di.TaktikViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.mkhalil.taktik.data.db.entities.EventEntity
import net.mkhalil.taktik.data.db.entities.ViewerEntity
import net.mkhalil.taktik.domain.model.ConnectionState
import net.mkhalil.taktik.ui.components.Avatar
import net.mkhalil.taktik.ui.components.StatTile
import net.mkhalil.taktik.ui.util.Formatters

@Composable
fun LiveScreen(
    onViewerClick: (String) -> Unit,
    onStopped: () -> Unit,
    vm: LiveViewModel = viewModel(factory = TaktikViewModels.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val active by vm.activeSessionId.collectAsStateWithLifecycle()

    if (active == null && state.session == null) {
        EmptyLiveState()
        return
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Comments", "Gifts", "Likes", "Follows", "Shares", "Top viewers")

    Column(Modifier.fillMaxSize()) {
        LiveHeader(state, onStop = {
            vm.stop()
            onStopped()
        })
        ScrollableTabRow(
            selectedTabIndex = tab,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(label, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal) },
                )
            }
        }
        when (tab) {
            0 -> OverviewTab(state, onViewerClick)
            1 -> EventListTab(state.events.comments, EventKind.COMMENT, state.coinToUsd, onViewerClick)
            2 -> EventListTab(state.events.gifts, EventKind.GIFT, state.coinToUsd, onViewerClick)
            3 -> EventListTab(state.events.likes, EventKind.LIKE, state.coinToUsd, onViewerClick)
            4 -> EventListTab(state.events.follows, EventKind.FOLLOW, state.coinToUsd, onViewerClick)
            5 -> EventListTab(state.events.shares, EventKind.SHARE, state.coinToUsd, onViewerClick)
            6 -> TopViewersTab(state, onViewerClick)
        }
    }
}

@Composable
private fun EmptyLiveState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.Visibility,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        Text("No active session", style = MaterialTheme.typography.titleMedium)
        Text(
            "Go to the Connect tab and enter a TikTok @handle to start tracking.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveHeader(state: LiveViewModel.Snapshot, onStop: () -> Unit) {
    val s = state.session
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(url = s?.host_avatar_url, name = s?.host_nickname ?: s?.host_unique_id, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    s?.host_nickname ?: s?.host_unique_id ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConnectionDot(state.connection)
                    Text(
                        connectionLabel(state.connection),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (s != null) {
                        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Formatters.duration(s.started_at, s.ended_at),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.roomInfo.viewerCount?.let { count ->
                        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            Icons.Default.Visibility,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            Formatters.compactNumber(count.toLong()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Stop, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Stop", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun ConnectionDot(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.Connected -> Color(0xFF18C964)
        ConnectionState.Connecting -> Color(0xFFFFB020)
        is ConnectionState.Reconnecting -> Color(0xFFFFB020)
        is ConnectionState.Disconnected, is ConnectionState.Error -> Color(0xFFFE2C55)
        ConnectionState.Idle -> Color(0xFF8E8E93)
    }
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    ConnectionState.Idle -> "Idle"
    ConnectionState.Connecting -> "Connecting…"
    ConnectionState.Connected -> "Live"
    is ConnectionState.Reconnecting -> "Reconnecting (#${state.attempt})"
    is ConnectionState.Disconnected -> state.reason?.takeIf { it.isNotBlank() }
        ?.let { "Disconnected: $it" } ?: "Disconnected"
    is ConnectionState.Error -> "Error: ${state.message}"
}

@Composable
private fun OverviewTab(state: LiveViewModel.Snapshot, onViewerClick: (String) -> Unit) {
    val s = state.session
    val recent by androidx.compose.runtime.remember(state.events) {
        androidx.compose.runtime.derivedStateOf {
            (state.events.comments + state.events.gifts + state.events.likes +
                state.events.follows + state.events.shares)
                .sortedByDescending { it.event.created_at }
                .take(60)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Comments",
                    value = Formatters.compactNumber((s?.total_comments ?: 0).toLong()),
                    icon = Icons.Default.Chat,
                    accent = Color(0xFF25F4EE),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Likes",
                    value = Formatters.compactNumber(s?.total_likes ?: 0),
                    icon = Icons.Default.Favorite,
                    accent = Color(0xFFFE2C55),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Gifts",
                    value = Formatters.compactNumber((s?.total_gifts ?: 0).toLong()),
                    icon = Icons.Default.MonetizationOn,
                    accent = Color(0xFFFFB020),
                    modifier = Modifier.weight(1f),
                    subtitle = "${s?.total_gift_coins ?: 0} coins · " +
                        Formatters.coinsToUsd(s?.total_gift_coins ?: 0L, state.coinToUsd),
                )
                StatTile(
                    label = "Viewers",
                    value = Formatters.compactNumber(
                        (state.roomInfo.viewerCount?.toLong() ?: s?.unique_viewers?.toLong() ?: 0L),
                    ),
                    icon = Icons.Default.GroupAdd,
                    accent = Color(0xFF7A5AF8),
                    modifier = Modifier.weight(1f),
                    subtitle = "${s?.unique_viewers ?: 0} unique · ${s?.total_follows ?: 0} follows",
                )
            }
        }
        item {
            Text(
                "Recent activity",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
        }
        items(recent, key = { it.event.id }) { row ->
            EventRow(row, state.coinToUsd, onViewerClick)
        }
    }
}

private enum class EventKind { COMMENT, GIFT, LIKE, FOLLOW, SHARE }

@Composable
private fun EventListTab(
    rows: List<LiveViewModel.EventWithViewer>,
    @Suppress("UNUSED_PARAMETER") kind: EventKind,
    coinUsd: Double,
    onViewerClick: (String) -> Unit,
) {
    if (rows.isEmpty()) {
        EmptyListState()
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(rows, key = { it.event.id }) { EventRow(it, coinUsd, onViewerClick) }
    }
}

@Composable
private fun EmptyListState() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            "No events yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventRow(
    row: LiveViewModel.EventWithViewer,
    coinUsd: Double,
    onViewerClick: (String) -> Unit,
) {
    val e = row.event
    val v = row.viewer
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { m -> if (v != null) m else m },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(
                url = v?.avatar_url,
                name = v?.nickname ?: v?.unique_id,
                modifier = Modifier.let { m -> v?.user_id?.let { m.clickable(onClick = { onViewerClick(it) }) } ?: m },
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        v?.nickname ?: v?.unique_id ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    val flag = Formatters.regionFlag(v?.region_code)
                    if (flag.isNotEmpty()) Text(flag)
                    Spacer(Modifier.weight(1f))
                    Text(
                        Formatters.formatTime(e.created_at),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                EventBody(e, coinUsd)
            }
        }
    }
}

@Composable
private fun EventBody(e: EventEntity, coinUsd: Double) {
    when (e.type) {
        "COMMENT" -> Text(e.comment_text.orEmpty(), style = MaterialTheme.typography.bodyMedium)
        "GIFT" -> {
            val count = e.gift_count ?: 1
            val coins = (e.gift_coins ?: 0) * count
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.MonetizationOn, null, tint = Color(0xFFFFB020), modifier = Modifier.size(16.dp))
                Text(
                    "${e.gift_name ?: "Gift"} × $count",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "$coins coins · ${Formatters.coinsToUsd(coins, coinUsd)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun TopViewersTab(state: LiveViewModel.Snapshot, onViewerClick: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        rankSection("Top gifters (coins)", state.ranks.gifters, state.coinToUsd, onViewerClick, isCoin = true)
        rankSection("Top commenters", state.ranks.commenters, state.coinToUsd, onViewerClick, isCoin = false)
        rankSection("Top likers", state.ranks.likers, state.coinToUsd, onViewerClick, isCoin = false)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rankSection(
    title: String,
    rows: List<LiveViewModel.RankRow>,
    coinUsd: Double,
    onViewerClick: (String) -> Unit,
    isCoin: Boolean,
) {
    item {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    if (rows.isEmpty()) {
        item {
            Text(
                "No data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    itemsIndexed(rows, key = { idx, r -> "${title}_${r.viewer?.user_id ?: idx}" }) { idx, r ->
        RankRowView(idx + 1, r, coinUsd, onViewerClick, isCoin)
    }
}

@Composable
private fun RankRowView(
    rank: Int,
    row: LiveViewModel.RankRow,
    coinUsd: Double,
    onViewerClick: (String) -> Unit,
    isCoin: Boolean,
) {
    val v = row.viewer
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "#$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Avatar(
                url = v?.avatar_url,
                name = v?.nickname ?: v?.unique_id,
                modifier = Modifier.let { m -> v?.user_id?.let { m.clickable(onClick = { onViewerClick(it) }) } ?: m },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    v?.nickname ?: v?.unique_id ?: "Unknown",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                )
                val flag = Formatters.regionFlag(v?.region_code)
                val region = v?.region ?: v?.region_code ?: ""
                if (flag.isNotEmpty() || region.isNotEmpty()) {
                    Text(
                        "$flag  $region",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (isCoin) "${row.score} coins" else Formatters.compactNumber(row.score),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (isCoin) {
                    Text(
                        Formatters.coinsToUsd(row.score, coinUsd),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "${row.count} events",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

