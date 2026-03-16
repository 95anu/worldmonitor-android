package com.worldmonitor.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmonitor.android.data.preferences.AppPreferences
import com.worldmonitor.android.ui.theme.BgCard
import com.worldmonitor.android.ui.theme.BgDeep
import com.worldmonitor.android.ui.theme.BgElevated
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.GreenOk
import com.worldmonitor.android.ui.theme.RedCritical
import com.worldmonitor.android.ui.theme.TextPrimary
import com.worldmonitor.android.ui.theme.TextSecondary
import com.worldmonitor.android.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var mapStyleField by remember(state.mapStyleUrl) { mutableStateOf(state.mapStyleUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(20.dp))

        // ── Server Connection ─────────────────────────────────────────────
        SectionHeader("Server Connection")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BgElevated)
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bulbColor = if (state.isConnected) GreenOk else RedCritical
            // Glowing bulb — drop shadow in the bulb colour for glow
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(14.dp)
                    .shadow(
                        elevation = if (state.isConnected) 8.dp else 4.dp,
                        shape = CircleShape,
                        ambientColor = bulbColor,
                        spotColor = bulbColor,
                    )
                    .clip(CircleShape)
                    .background(bulbColor)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = if (state.isConnected) "Connected to server" else "Server unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = bulbColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = AppPreferences.DEFAULT_SERVER_URL,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Divider(color = BgCard)
        Spacer(Modifier.height(20.dp))

        // ── Refresh Interval ─────────────────────────────────────────────
        SectionHeader("Data Refresh")
        Text(
            "Refresh every ${state.refreshInterval} minute${if (state.refreshInterval == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Slider(
            value = state.refreshInterval.toFloat(),
            onValueChange = { vm.saveRefreshInterval(it.toInt()) },
            valueRange = 1f..30f,
            steps = 28,
            colors = SliderDefaults.colors(thumbColor = CyanPrimary, activeTrackColor = CyanPrimary),
        )

        Spacer(Modifier.height(20.dp))
        Divider(color = BgCard)
        Spacer(Modifier.height(20.dp))

        // ── Map Style ────────────────────────────────────────────────────
        SectionHeader("Map Style")
        OutlinedTextField(
            value = mapStyleField,
            onValueChange = { mapStyleField = it },
            label = { Text("MapLibre Style URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = TextSecondary,
                focusedLabelColor = CyanPrimary,
                cursorColor = CyanPrimary,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.saveMapStyleUrl(mapStyleField) },
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Save Style", color = BgDeep, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))
        Divider(color = BgCard)
        Spacer(Modifier.height(20.dp))

        // ── About ────────────────────────────────────────────────────────
        SectionHeader("About")
        Text(
            "WorldMonitor Android v1.0",
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
        )
        Text(
            "Inspired by github.com/koala73/worldmonitor",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        Text(
            "Backend: FastAPI + SQLite on Raspberry Pi 4 (via Cloudflare Tunnel)",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = CyanPrimary,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
}
