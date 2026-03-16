package com.worldmonitor.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmonitor.android.ui.theme.BgDeep
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.GreenOk
import com.worldmonitor.android.ui.theme.RedCritical
import com.worldmonitor.android.ui.theme.TextSecondary
import com.worldmonitor.android.viewmodel.ConnectionStatus
import com.worldmonitor.android.viewmodel.SettingsViewModel

@Composable
fun SetupScreen(onConnected: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()
    var inputUrl by remember { mutableStateOf("http://192.168.1.100:8000") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.connectionStatus) {
        if (state.connectionStatus == ConnectionStatus.SUCCESS) {
            onConnected()
        }
    }

    Surface(color = BgDeep, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(72.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("WorldMonitor", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Enter your Raspberry Pi's local address",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = TextSecondary,
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it; vm.updateServerUrl(it) },
                label = { Text("Server URL") },
                placeholder = { Text("http://192.168.1.100:8000") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    focusManager.clearFocus()
                    vm.testAndSaveConnection(inputUrl)
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPrimary,
                    unfocusedBorderColor = TextSecondary,
                ),
                isError = state.connectionStatus == ConnectionStatus.FAILURE,
                supportingText = when (state.connectionStatus) {
                    ConnectionStatus.SUCCESS -> {
                        { Text("Connected!", color = GreenOk) }
                    }
                    ConnectionStatus.FAILURE -> {
                        { Text(state.connectionError ?: "Connection failed", color = RedCritical) }
                    }
                    else -> null
                },
                trailingIcon = when (state.connectionStatus) {
                    ConnectionStatus.SUCCESS -> {
                        { Icon(Icons.Default.CheckCircle, null, tint = GreenOk) }
                    }
                    ConnectionStatus.FAILURE -> {
                        { Icon(Icons.Default.Error, null, tint = RedCritical) }
                    }
                    else -> null
                },
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    focusManager.clearFocus()
                    vm.testAndSaveConnection(inputUrl)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = state.connectionStatus != ConnectionStatus.TESTING,
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
            ) {
                if (state.connectionStatus == ConnectionStatus.TESTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = BgDeep,
                    )
                } else {
                    Text("Connect", color = BgDeep, style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Make sure your phone is on the same WiFi network as your Pi",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = TextSecondary,
            )
        }
    }
}
