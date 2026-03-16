package com.worldmonitor.android.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worldmonitor.android.ui.components.NewsCard
import com.worldmonitor.android.ui.theme.BgDeep
import com.worldmonitor.android.ui.theme.BgElevated
import com.worldmonitor.android.ui.theme.BgSurface
import com.worldmonitor.android.ui.theme.CyanPrimary
import com.worldmonitor.android.ui.theme.OrangeAlert
import com.worldmonitor.android.ui.theme.TextMuted
import com.worldmonitor.android.ui.theme.TextPrimary
import com.worldmonitor.android.ui.theme.TextSecondary
import com.worldmonitor.android.viewmodel.NewsViewModel

private val CATEGORIES = listOf(
    "general", "geopolitics", "conflict", "economics",
    "technology", "environment", "health"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    vm: NewsViewModel = viewModel(),
    initialCountry: String? = null,
) {
    val state by vm.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var searchActive by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    // Apply initial country filter on first composition (or whenever initialCountry changes)
    LaunchedEffect(initialCountry) {
        if (initialCountry != null) {
            vm.filterByCountry(initialCountry)
        }
    }

    // Infinite scroll — load more when near the end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.items.size - 5 && state.hasMore && !state.isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep),
    ) {
        // Breaking news banner
        if (state.items.any { it.isBreaking }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrangeAlert)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "● BREAKING NEWS",
                    style = MaterialTheme.typography.labelSmall,
                    color = BgDeep,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Country filter badge (shown when navigated from map or filter active)
        if (state.selectedCountry != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Filtered:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                SuggestionChip(
                    onClick = { vm.filterByCountry(null) },
                    label = {
                        Text(
                            state.selectedCountry!!.uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = BgDeep,
                        )
                    },
                    icon = {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear country filter",
                            tint = BgDeep,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = CyanPrimary,
                        iconContentColor = BgDeep,
                    ),
                    shape = RoundedCornerShape(50),
                )
            }
        }

        // Search bar
        SearchBar(
            query = searchText,
            onQueryChange = { searchText = it },
            onSearch = { vm.search(it.ifBlank { null }) },
            active = searchActive,
            onActiveChange = { searchActive = it },
            placeholder = { Text("Search news…", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary) },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = { searchText = ""; vm.search(null) }) {
                        Icon(Icons.Default.Clear, null, tint = TextSecondary)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = SearchBarDefaults.colors(
                containerColor = BgSurface,
            ),
            tonalElevation = 0.dp,
            content = {},
        )

        // Category filters
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            item {
                FilterChip(
                    selected = state.selectedCategory == null,
                    onClick = { vm.filterByCategory(null) },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPrimary,
                        selectedLabelColor = BgDeep,
                    ),
                )
            }
            items(CATEGORIES.size) { i ->
                val cat = CATEGORIES[i]
                FilterChip(
                    selected = state.selectedCategory == cat,
                    onClick = { vm.filterByCategory(if (state.selectedCategory == cat) null else cat) },
                    label = { Text(cat.replaceFirstChar { it.uppercase() }) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPrimary,
                        selectedLabelColor = BgDeep,
                    ),
                )
            }
        }

        if (state.isLoading && state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CyanPrimary)
            }
            return@Column
        }

        state.error?.let { err ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(err, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.refresh() }) {
                        Text("Retry", color = CyanPrimary)
                    }
                }
            }
            return@Column
        }

        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No articles found", color = TextSecondary)
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(state.items, key = { _, item -> item.id }) { _, article ->
                NewsCard(
                    article = article,
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
                        context.startActivity(intent)
                    },
                )
            }
            if (state.isLoadingMore) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = CyanPrimary,
                        trackColor = BgSurface,
                    )
                }
            }
        }
    }
}
