package com.worldmonitor.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmonitor.android.ui.components.EventCard
import com.worldmonitor.android.ui.theme.BgDeep
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.SkeletonBase
import com.worldmonitor.android.ui.theme.SkeletonHighlight
import com.worldmonitor.android.ui.theme.TextSecondary
import com.worldmonitor.android.viewmodel.EventsViewModel
import kotlinx.coroutines.delay

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

        // Skeleton loaders while fetching
        if (state.isLoading) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(6) { EventCardSkeleton() }
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

        // Staggered entrance animation
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.filteredEvents, key = { _, event -> event.id }) { index, event ->
                var visible by remember(event.id) { mutableStateOf(false) }
                LaunchedEffect(event.id) {
                    delay(index.coerceAtMost(10) * 45L)
                    visible = true
                }
                AnimatedVisibility(
                    visible = visible,
                    enter   = fadeIn(tween(250)) + slideInVertically(tween(220)) { it / 3 },
                ) {
                    EventCard(event = event, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun EventCardSkeleton() {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val alpha by shimmer.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(950, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(SkeletonHighlight.copy(alpha = alpha))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .background(SkeletonBase)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.spacedBy(12.dp),
                Alignment.CenterVertically,
            ) {
                // Emoji placeholder
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SkeletonHighlight.copy(alpha = alpha))
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        Arrangement.SpaceBetween,
                        Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(60.dp).height(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(SkeletonHighlight.copy(alpha = alpha))
                        )
                        Box(
                            Modifier
                                .width(40.dp).height(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(SkeletonHighlight.copy(alpha = alpha * 0.7f))
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth(0.85f).height(13.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(SkeletonHighlight.copy(alpha = alpha), SkeletonHighlight.copy(alpha = alpha * 0.4f))
                                )
                            )
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.60f).height(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SkeletonHighlight.copy(alpha = alpha * 0.5f))
                    )
                }
            }
        }
    }
}
