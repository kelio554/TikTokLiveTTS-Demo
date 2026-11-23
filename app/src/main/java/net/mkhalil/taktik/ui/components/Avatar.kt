package net.mkhalil.taktik.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun Avatar(
    url: String?,
    name: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val initial = (name?.firstOrNull()?.uppercaseChar() ?: '?').toString()
    val seed = (name ?: "?").hashCode()
    val bg = palette[(seed and Int.MAX_VALUE) % palette.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

private val palette = listOf(
    Color(0xFFFE2C55), Color(0xFF25F4EE), Color(0xFF7A5AF8),
    Color(0xFFFFB020), Color(0xFF18C964), Color(0xFFF31260),
    Color(0xFF006FEE), Color(0xFFFF6B35), Color(0xFFC084FC),
)
