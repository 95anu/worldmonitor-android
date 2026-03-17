package com.worldmonitor.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.worldmonitor.android.data.models.EventItem
import com.worldmonitor.android.ui.theme.BgCard
import com.worldmonitor.android.ui.theme.RedCritical
import com.worldmonitor.android.ui.theme.SeverityCritical
import com.worldmonitor.android.ui.theme.SeverityHigh
import com.worldmonitor.android.ui.theme.SeverityLow
import com.worldmonitor.android.ui.theme.SeverityMedium
import com.worldmonitor.android.ui.theme.TextMuted
import com.worldmonitor.android.ui.theme.TextPrimary
import com.worldmonitor.android.ui.theme.TextSecondary
import java.time.ZonedDateTime
import java.time.Duration

private fun typeEmoji(type: String) = when (type) {
    "earthquake" -> "🌍"
    "fire"       -> "🔥"
    "conflict"   -> "⚔"
    else         -> "⚠"
}

private fun severityColor(severity: String): Color = when (severity) {
    "critical" -> SeverityCritical
    "high"     -> SeverityHigh
    "medium"   -> SeverityMedium
    else       -> SeverityLow
}

@Composable
fun EventCard(event: EventItem, modifier: Modifier = Modifier) {
    val sevColor = severityColor(event.severity)
    val isCritical = event.severity == "critical"
    val isHigh = event.severity in listOf("high", "critical")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        // Severity stripe — gradient from bright to dim
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(listOf(sevColor, sevColor.copy(alpha = 0.35f)))
                )
        )

        // Card body
        Box(
            modifier = Modifier
                .weight(1f)
                .background(BgCard)
        ) {
            // Subtle left glow for high/critical events
            if (isHigh) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(sevColor.copy(alpha = if (isCritical) 0.10f else 0.06f), Color.Transparent),
                                startX = 0f,
                                endX = 80f,
                            )
                        )
                )
            }

            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Type icon + magnitude
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(52.dp),
                ) {
                    Text(typeEmoji(event.type), fontSize = 26.sp)
                    event.magnitude?.let { mag ->
                        Text(
                            "%.1f".format(mag),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = sevColor,
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
                        SeverityBadge(event.severity, sevColor)
                        Text(
                            formatEventTime(event.occurredAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        event.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    event.description?.let { desc ->
                        Spacer(Modifier.height(2.dp))
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (event.country != null || (event.lat != null && event.lon != null)) {
                        Spacer(Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            event.country?.let { code ->
                                Text(
                                    "📍 $code",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                )
                            }
                            if (event.lat != null && event.lon != null) {
                                Text(
                                    "${"%.2f".format(event.lat)}°, ${"%.2f".format(event.lon)}°",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityBadge(severity: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Dot for critical severity
        if (severity == "critical") {
            Box(Modifier.size(5.dp).clip(CircleShape).background(RedCritical))
        }
        Text(
            severity.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

private fun formatEventTime(isoTime: String?): String {
    if (isoTime == null) return ""
    return try {
        val zdt = ZonedDateTime.parse(isoTime)
        val mins = Duration.between(zdt, ZonedDateTime.now()).toMinutes()
        when {
            mins < 60   -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else        -> "${mins / 1440}d ago"
        }
    } catch (e: Exception) {
        isoTime.take(10)
    }
}
