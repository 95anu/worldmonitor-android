package com.worldmonitor.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmonitor.android.ui.components.EventCard
import com.worldmonitor.android.ui.theme.BgDeep
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.TextSecondary
import com.worldmonitor.android.viewmodel.EventsViewModel

private val EVENT_TYPES = listOf("earthquake" to "🌍 Earthquake", "fire" to "🔥 Fire", "conflict" to "⚔ Conflict")
private val DAYS_OPTIONS = listOf(1, 3, 7, 14)

@Composable
fun EventsScreen(vm: EventsViewModel = viewModel()) {
    val state by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep),
    ) {
        // Type filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(EVENT_TYPES) { (type, label) ->
                FilterChip(
                    selected = type in state.selectedTypes,
                    onClick = { vm.toggleType(type) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPrimary,
                        selectedLabelColor = BgDeep,
                    ),
                )
            }
        }

        // Days back selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Last:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            DAYS_OPTIONS.forEach { d ->
                FilterChip(
                    selected = state.daysBack == d,
                    onClick = { vm.setDaysBack(d) },
                    label = { Text("${d}d") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPrimary.copy(alpha = 0.8f),
                        selectedLabelColor = BgDeep,
                    ),
                )
            }
        }

        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanPrimary)
            }
            return@Column
        }

        state.error?.let { err ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(err, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.refresh() }) {
                        Text("Retry", color = CyanPrimary)
                    }
                }
            }
            return@Column
        }

        if (state.filteredEvents.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No events in selected filters", color = TextSecondary)
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.filteredEvents, key = { it.id }) { event ->
                EventCard(event = event)
            }
        }
    }
}
