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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmonitor.android.ui.theme.BgCard
import com.worldmonitor.android.ui.theme.BgDeep
import com.worldmonitor.android.ui.theme.BgElevated
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.GreenOk
import com.worldmonitor.android.ui.theme.RedCritical
import com.worldmonitor.android.ui.theme.TextPrimary
import com.worldmonitor.android.ui.theme.TextSecondary
import com.worldmonitor.android.viewmodel.ConnectionStatus
import com.worldmonitor.android.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var urlField by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
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

        // ── Connection Status ─────────────────────────────────────────────
        SectionHeader("Connection")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BgElevated)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val isConnected = state.connectionStatus == ConnectionStatus.SUCCESS
            val dotColor = when (state.connectionStatus) {
                ConnectionStatus.SUCCESS -> GreenOk
                ConnectionStatus.FAILURE -> RedCritical
                ConnectionStatus.TESTING -> CyanPrimary
                ConnectionStatus.IDLE -> TextSecondary
            }
            val statusLabel = when (state.connectionStatus) {
                ConnectionStatus.SUCCESS -> "Connected"
                ConnectionStatus.FAILURE -> "Disconnected"
                ConnectionStatus.TESTING -> "Testing…"
                ConnectionStatus.IDLE -> "Not tested"
            }
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = dotColor,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.connectionStatus == ConnectionStatus.SUCCESS && state.serverUrl.isNotBlank()) {
                    Text(
                        state.serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                state.connectionError?.let { err ->
                    if (state.connectionStatus == ConnectionStatus.FAILURE) {
                        Text(err, style = MaterialTheme.typography.bodySmall, color = RedCritical)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Divider(color = BgCard)
        Spacer(Modifier.height(20.dp))

        // ── Server Configuration ──────────────────────────────────────────
        SectionHeader("Server Configuration")
        OutlinedTextField(
            value = urlField,
            onValueChange = { urlField = it; vm.updateServerUrl(it) },
            label = { Text("Server URL") },
            placeholder = { Text("http://192.168.1.100:8000") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    // Auto-save URL on focus-lost if it changed
                    if (!focusState.isFocused && urlField != state.serverUrl && urlField.isNotBlank()) {
                        vm.updateServerUrl(urlField)
                    }
                },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = TextSecondary,
                focusedLabelColor = CyanPrimary,
                cursorColor = CyanPrimary,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { vm.testAndSaveConnection(urlField) },
            enabled = state.connectionStatus != ConnectionStatus.TESTING,
            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            shape = RoundedCornerShape(8.dp),
        ) {
            if (state.connectionStatus == ConnectionStatus.TESTING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = BgDeep,
                )
                Spacer(Modifier.width(8.dp))
                Text("Testing…", color = BgDeep, fontWeight = FontWeight.Bold)
            } else {
                Text("Test & Save", color = BgDeep, fontWeight = FontWeight.Bold)
            }
        }
        when (state.connectionStatus) {
            ConnectionStatus.SUCCESS -> StatusRow(Icons.Default.CheckCircle, "Connected", GreenOk)
            ConnectionStatus.FAILURE -> StatusRow(Icons.Default.Error, state.connectionError ?: "Failed", RedCritical)
            else -> {}
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
            "Backend: FastAPI + SQLite on Raspberry Pi 4",
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

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    msg: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(msg, style = MaterialTheme.typography.bodySmall, color = tint)
    }
}
