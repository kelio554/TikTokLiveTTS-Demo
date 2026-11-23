package net.mkhalil.taktik.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mkhalil.taktik.data.db.dao.EventDao
import net.mkhalil.taktik.ui.util.Formatters

@Composable
fun TopRegionsCard(
    rows: List<EventDao.RegionRow>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Public, null, tint = MaterialTheme.colorScheme.primary)
                Text("Top regions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (rows.isEmpty()) {
                Text(
                    "No region data yet — viewers' countries fill in as TakTik resolves their profiles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val max = rows.maxOf { it.viewers }.coerceAtLeast(1)
            rows.forEachIndexed { idx, row ->
                RegionBar(row, max, palette[idx % palette.size])
            }
        }
    }
}

@Composable
private fun RegionBar(row: EventDao.RegionRow, max: Int, color: Color) {
    val flag = Formatters.regionFlag(row.region_code)
    val label = row.region ?: row.region_code ?: "Unknown"
    val widthFraction = (row.viewers.toFloat() / max).coerceIn(0.05f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (flag.isNotBlank()) Text(flag, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                "${row.viewers} viewer${if (row.viewers == 1) "" else "s"} · ${row.events} ev",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(widthFraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color),
            )
        }
    }
}

private val palette = listOf(
    Color(0xFFFE2C55), Color(0xFF25F4EE), Color(0xFF7A5AF8),
    Color(0xFFFFB020), Color(0xFF18C964), Color(0xFF006FEE),
    Color(0xFFF31260), Color(0xFFFF6B35), Color(0xFFC084FC),
    Color(0xFF22D3EE), Color(0xFFFACC15), Color(0xFFA3E635),
)
