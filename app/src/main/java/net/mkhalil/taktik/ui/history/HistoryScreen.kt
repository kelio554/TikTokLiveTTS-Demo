package net.mkhalil.taktik.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import net.mkhalil.taktik.data.db.entities.SessionEntity
import net.mkhalil.taktik.data.db.entities.ViewerEntity
import net.mkhalil.taktik.ui.components.Avatar
import net.mkhalil.taktik.ui.util.Formatters

@Composable
fun HistoryScreen(
    onSessionClick: (Long) -> Unit,
    onViewerClick: (String) -> Unit,
    vm: HistoryViewModel = viewModel(factory = TaktikViewModels.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val tabIndex = if (s.tab == HistoryViewModel.Tab.SESSIONS) 0 else 1

    var deleteAllPrompt by remember { mutableStateOf(false) }
    var deleteSelectedPrompt by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = tabIndex) {
            Tab(
                selected = tabIndex == 0,
                onClick = { vm.setTab(HistoryViewModel.Tab.SESSIONS) },
                text = { Text("By session") },
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { vm.setTab(HistoryViewModel.Tab.VIEWERS) },
                text = { Text("By viewer") },
            )
        }

        ActionBar(
            state = s,
            onSelectAll = vm::selectAll,
            onClear = vm::exitSelectMode,
            onDeleteSelected = { if (s.selectionCount > 0) deleteSelectedPrompt = true },
            onDeleteAll = { deleteAllPrompt = true },
            onEnterSelectMode = vm::enterSelectMode,
        )

        OutlinedTextField(
            value = s.query,
            onValueChange = vm::setQuery,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = { Text(if (s.tab == HistoryViewModel.Tab.SESSIONS) "Search host…" else "Search viewer / region…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = RoundedCornerShape(14.dp),
        )
        if (s.tab == HistoryViewModel.Tab.SESSIONS) {
            DateFilters(s.date, vm::setDate)
            SessionList(
                sessions = s.sessions,
                selectMode = s.selectMode,
                selectedIds = s.selectedSessionIds,
                onClick = { id ->
                    if (s.selectMode) vm.toggleSelectSession(id) else onSessionClick(id)
                },
                onLongClick = vm::toggleSelectSession,
            )
        } else {
            ViewerList(
                viewers = s.viewers,
                selectMode = s.selectMode,
                selectedIds = s.selectedViewerIds,
                onClick = { id ->
                    if (s.selectMode) vm.toggleSelectViewer(id) else onViewerClick(id)
                },
                onLongClick = vm::toggleSelectViewer,
            )
        }
    }

    if (deleteSelectedPrompt) {
        ConfirmDeleteDialog(
            title = "Delete ${s.selectionCount} ${if (s.tab == HistoryViewModel.Tab.SESSIONS) "session(s)" else "viewer(s)"}?",
            body = "This can't be undone.",
            onConfirm = {
                vm.deleteSelected()
                deleteSelectedPrompt = false
            },
            onDismiss = { deleteSelectedPrompt = false },
        )
    }
    if (deleteAllPrompt) {
        ConfirmDeleteDialog(
            title = "Delete all ${if (s.tab == HistoryViewModel.Tab.SESSIONS) "sessions" else "viewers"}?",
            body = "Every ${if (s.tab == HistoryViewModel.Tab.SESSIONS) "session and its events" else "viewer profile"} will be removed. This can't be undone.",
            onConfirm = {
                vm.deleteAll()
                deleteAllPrompt = false
            },
            onDismiss = { deleteAllPrompt = false },
        )
    }
}

@Composable
private fun ActionBar(
    state: HistoryViewModel.UiState,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteAll: () -> Unit,
    onEnterSelectMode: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (state.selectMode) {
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
            }
            Text(
                "${state.selectionCount} selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
            }
            IconButton(
                onClick = onDeleteSelected,
                enabled = state.selectionCount > 0,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete selected",
                    tint = if (state.selectionCount > 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEnterSelectMode) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Select")
            }
            TextButton(onClick = onDeleteAll) {
                Icon(
                    Icons.Default.DeleteSweep,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.width(4.dp))
                Text("Delete all", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DateFilters(current: HistoryViewModel.DateFilter, onPick: (HistoryViewModel.DateFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val opts = listOf(
            HistoryViewModel.DateFilter.ALL to "All",
            HistoryViewModel.DateFilter.TODAY to "Today",
            HistoryViewModel.DateFilter.LAST_7 to "Last 7 days",
            HistoryViewModel.DateFilter.LAST_30 to "Last 30 days",
        )
        items(opts) { (filter, label) ->
            FilterChip(
                selected = current == filter,
                onClick = { onPick(filter) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    }
}

@Composable
private fun SessionList(
    sessions: List<SessionEntity>,
    selectMode: Boolean,
    selectedIds: Set<Long>,
    onClick: (Long) -> Unit,
    onLongClick: (Long) -> Unit,
) {
    if (sessions.isEmpty()) {
        EmptyState(Icons.Default.History, "No sessions yet", "Start tracking a live stream to build history.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                selected = session.id in selectedIds,
                selectMode = selectMode,
                onClick = { onClick(session.id) },
                onLongClick = { onLongClick(session.id) },
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionEntity,
    selected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectionBadge(selectMode, selected)
                Avatar(url = session.host_avatar_url, name = session.host_nickname ?: session.host_unique_id, size = 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        session.host_nickname ?: session.host_unique_id,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "@${session.host_unique_id}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Formatters.formatDateTime(session.started_at),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        Formatters.duration(session.started_at, session.ended_at),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Stat(Icons.Default.Chat, "${session.total_comments}")
                Stat(Icons.Default.Favorite, Formatters.compactNumber(session.total_likes))
                Stat(Icons.Default.MonetizationOn, "${session.total_gift_coins}c")
                Text(
                    "· ${session.unique_viewers} viewers",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ViewerList(
    viewers: List<ViewerEntity>,
    selectMode: Boolean,
    selectedIds: Set<String>,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit,
) {
    if (viewers.isEmpty()) {
        EmptyState(Icons.Default.History, "No viewers yet", "Viewer profiles will appear here after your first tracked session.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(viewers, key = { it.user_id }) { viewer ->
            ViewerCard(
                viewer = viewer,
                selected = viewer.user_id in selectedIds,
                selectMode = selectMode,
                onClick = { onClick(viewer.user_id) },
                onLongClick = { onLongClick(viewer.user_id) },
            )
        }
    }
}

@Composable
private fun ViewerCard(
    viewer: ViewerEntity,
    selected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectionBadge(selectMode, selected)
            Avatar(url = viewer.avatar_url, name = viewer.nickname ?: viewer.unique_id)
            Column(Modifier.weight(1f)) {
                Text(viewer.nickname ?: viewer.unique_id, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text("@${viewer.unique_id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val flag = Formatters.regionFlag(viewer.region_code)
                val region = viewer.region ?: viewer.region_code
                if (!region.isNullOrBlank()) {
                    Text("$flag  $region", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Last seen\n${Formatters.formatDateTime(viewer.last_seen_at)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectionBadge(selectMode: Boolean, selected: Boolean) {
    if (!selectMode) return
    val color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(color)
            .border(
                width = 2.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(11.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(icon: ImageVector, title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

