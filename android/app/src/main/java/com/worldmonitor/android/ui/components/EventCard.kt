package com.worldmonitor.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmonitor.android.data.models.EventItem
import com.worldmonitor.android.ui.theme.BgCard
import com.worldmonitor.android.ui.theme.SeverityCritical
import com.worldmonitor.android.ui.theme.SeverityHigh
import com.worldmonitor.android.ui.theme.SeverityLow
import com.worldmonitor.android.ui.theme.SeverityMedium
import com.worldmonitor.android.ui.theme.TextMuted
import com.worldmonitor.android.ui.theme.TextSecondary
import java.time.ZonedDateTime
import java.time.Duration

private fun typeEmoji(type: String) = when (type) {
    "earthquake" -> "🌍"
    "fire" -> "🔥"
    "conflict" -> "⚔"
    else -> "⚠"
}

private fun severityColor(severity: String): Color = when (severity) {
    "critical" -> SeverityCritical
    "high" -> SeverityHigh
    "medium" -> SeverityMedium
    else -> SeverityLow
}

@Composable
fun EventCard(event: EventItem, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = BgCard,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Type icon + magnitude
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(52.dp),
            ) {
                Text(typeEmoji(event.type), fontSize = 28.sp)
                event.magnitude?.let { mag ->
                    Text(
                        String.format("%.1f", mag),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = severityColor(event.severity),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeverityBadge(event.severity)
                    Text(
                        formatEventTime(event.occurredAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
                Text(
                    event.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                event.description?.let { desc ->
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                event.country?.let { code ->
                    Text(
                        "📍 $code",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                    )
                }
                if (event.lat != null && event.lon != null) {
                    Text(
                        "${String.format("%.2f", event.lat)}°, ${String.format("%.2f", event.lon)}°",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun SeverityBadge(severity: String) {
    Box(
        modifier = Modifier
            .background(severityColor(severity).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            severity.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = severityColor(severity),
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatEventTime(isoTime: String?): String {
    if (isoTime == null) return ""
    return try {
        val zdt = ZonedDateTime.parse(isoTime)
        val mins = Duration.between(zdt, ZonedDateTime.now()).toMinutes()
        when {
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else -> "${mins / 1440}d ago"
        }
    } catch (e: Exception) {
        isoTime.take(10)
    }
}
