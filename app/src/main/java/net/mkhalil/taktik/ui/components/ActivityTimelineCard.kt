package net.mkhalil.taktik.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.mkhalil.taktik.data.db.dao.EventDao

@Composable
fun ActivityTimelineCard(
    timeline: List<EventDao.TimelineRow>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ShowChart, null, tint = MaterialTheme.colorScheme.primary)
                Text("Activity over time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            if (timeline.isEmpty()) {
                Text(
                    "No events yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            val series = SERIES.map { spec ->
                val rows = timeline.filter { it.type == spec.type }
                Series(spec, rows)
            }
            val minBucket = timeline.minOf { it.bucket_ms }
            val maxBucket = timeline.maxOf { it.bucket_ms }
            val maxValue = (series.flatMap { it.rows }.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                val w = size.width
                val h = size.height
                val pad = 6f
                val plotW = w - pad * 2
                val plotH = h - pad * 2

                val grid = Color(0x22FFFFFF)
                for (i in 0..3) {
                    val y = pad + plotH * i / 3f
                    drawLine(grid, Offset(pad, y), Offset(w - pad, y), strokeWidth = 1f)
                }

                val span = (maxBucket - minBucket).coerceAtLeast(1L)

                series.forEach { s ->
                    if (s.rows.size < 2) {
                        // Single point — draw a dot
                        s.rows.firstOrNull()?.let { row ->
                            val x = pad + plotW * (row.bucket_ms - minBucket).toFloat() / span
                            val y = pad + plotH * (1f - row.count.toFloat() / maxValue)
                            drawCircle(s.spec.color, radius = 3f, center = Offset(x, y))
                        }
                        return@forEach
                    }
                    val path = Path()
                    s.rows.forEachIndexed { i, row ->
                        val x = pad + plotW * (row.bucket_ms - minBucket).toFloat() / span
                        val y = pad + plotH * (1f - row.count.toFloat() / maxValue)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = s.spec.color,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SERIES.forEach { spec ->
                    LegendDot(spec.label, spec.color)
                }
            }
        }
    }
}

private data class SeriesSpec(val type: String, val label: String, val color: Color)
private data class Series(val spec: SeriesSpec, val rows: List<EventDao.TimelineRow>)

private val SERIES = listOf(
    SeriesSpec("COMMENT", "Comments", Color(0xFF25F4EE)),
    SeriesSpec("GIFT", "Gifts", Color(0xFFFFB020)),
    SeriesSpec("LIKE", "Likes", Color(0xFFFE2C55)),
)

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

