package com.worldmonitor.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.worldmonitor.android.ui.theme.BgDeep
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.TextSecondary
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
fun StatsBar(
    articlesCount: Int,
    sourcesCount: Int,
    lastUpdated: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(BgDeep.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatItem("$articlesCount articles today")
        Bullet()
        StatItem("$sourcesCount sources")
        lastUpdated?.let { ts ->
            Bullet()
            StatItem("Updated ${formatTime(ts)}")
        }
    }
}

@Composable
private fun StatItem(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
}

@Composable
private fun Bullet() {
    Text("•", style = MaterialTheme.typography.labelSmall, color = CyanPrimary)
}

private fun formatTime(iso: String): String {
    return try {
        val zdt = ZonedDateTime.parse(iso)
        zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        iso.take(5)
    }
}
