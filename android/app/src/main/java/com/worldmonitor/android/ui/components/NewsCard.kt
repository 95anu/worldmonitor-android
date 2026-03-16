package com.worldmonitor.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.worldmonitor.android.data.models.NewsItem
import com.worldmonitor.android.ui.theme.BgCard
import com.worldmonitor.android.ui.theme.BgElevated
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.OrangeAlert
import com.worldmonitor.android.ui.theme.RedCritical
import com.worldmonitor.android.ui.theme.TextMuted
import com.worldmonitor.android.ui.theme.TextSecondary
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun NewsCard(article: NewsItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val leftBorderColor = when {
        article.isBreaking -> RedCritical
        article.importanceScore >= 0.8f -> OrangeAlert
        article.importanceScore >= 0.6f -> CyanPrimary
        else -> BgElevated
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(start = 3.dp, color = leftBorderColor, shape = RoundedCornerShape(4.dp)),
        color = BgCard,
        shape = RoundedCornerShape(4.dp),
        tonalElevation = 2.dp,
    ) {
        Column {
            // Breaking banner
            if (article.isBreaking) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RedCritical)
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                ) {
                    Text("BREAKING", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Row(modifier = Modifier.padding(12.dp)) {
                // Text content
                Column(modifier = Modifier.weight(1f)) {
                    // Source + time
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            article.sourceName ?: "Unknown Source",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPrimary,
                        )
                        Text(
                            formatRelativeTime(article.publishedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // Title
                    Text(
                        article.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Summary
                    article.summary?.let { summary ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    // Country + Category chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        article.countries.take(3).forEach { code ->
                            MiniChip(code, CyanPrimary.copy(alpha = 0.15f), CyanPrimary)
                        }
                        article.categories.take(1).forEach { cat ->
                            MiniChip(cat, BgElevated, TextSecondary)
                        }
                    }
                }

                // Thumbnail
                article.imageUrl?.let { url ->
                    Spacer(Modifier.width(10.dp))
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniChip(text: String, bg: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = textColor)
    }
}

// Extension to add a left border
private fun Modifier.border(start: androidx.compose.ui.unit.Dp, color: Color, shape: RoundedCornerShape): Modifier {
    return this.then(
        Modifier.border(
            width = start,
            color = color,
            shape = shape,
        )
    )
}

private fun formatRelativeTime(isoTime: String?): String {
    if (isoTime == null) return ""
    return try {
        val zdt = ZonedDateTime.parse(isoTime)
        val now = ZonedDateTime.now()
        val mins = java.time.Duration.between(zdt, now).toMinutes()
        when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else -> "${mins / 1440}d ago"
        }
    } catch (e: Exception) {
        isoTime.take(10)
    }
}
