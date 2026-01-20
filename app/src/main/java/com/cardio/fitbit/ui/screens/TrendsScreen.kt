package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardio.fitbit.ui.components.TrendsChart

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: (java.util.Date) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedMetrics by remember { mutableStateOf(setOf(
        com.cardio.fitbit.ui.components.TrendMetric.NIGHT, 
        com.cardio.fitbit.ui.components.TrendMetric.DAY
    )) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val titleSuffix = if (uiState is TrendsUiState.Success) {
                        "(${(uiState as TrendsUiState.Success).selectedDays} Jours)"
                    } else {
                        "(7 Jours)"
                    }
                    Text("Tendances $titleSuffix") 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadTrends() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFF5F5F5)
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is TrendsUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is TrendsUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Erreur: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadTrends() }) {
                            Text("Réessayer")
                        }
                    }
                }
                is TrendsUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Filter Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val selectedDays = state.selectedDays
                            FilterChip(
                                selected = selectedDays == 7,
                                onClick = { viewModel.loadTrends(7) },
                                label = { Text("7 Jours") },
                                leadingIcon = if (selectedDays == 7) {
                                    { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null
                            )
                            FilterChip(
                                selected = selectedDays == 15,
                                onClick = { viewModel.loadTrends(15) },
                                label = { Text("15 Jours") },
                                leadingIcon = if (selectedDays == 15) {
                                    { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null
                            )
                            FilterChip(
                                selected = selectedDays == 30,
                                onClick = { viewModel.loadTrends(30) },
                                label = { Text("30 Jours") },
                                leadingIcon = if (selectedDays == 30) {
                                    { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null
                            )
                        }

                        // Mood History
                        if (state.data.any { it.moodRating != null }) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Humeur",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50) // Green title
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    androidx.compose.foundation.lazy.LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        val dateFormat = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
                                        items(state.data.size) { index ->
                                            val point = state.data[index]
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.clickable { 
                                                    onNavigateToDashboard(point.date) 
                                                }
                                            ) {
                                                val emoji = when (point.moodRating) {
                                                    1 -> "😫"
                                                    2 -> "😞"
                                                    3 -> "😐"
                                                    4 -> "🙂"
                                                    5 -> "😀"
                                                    else -> "-"
                                                }
                                                Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = dateFormat.format(point.date),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Unified Trends Chart
                        Card(
                            modifier = Modifier.fillMaxWidth().height(450.dp), // Taller for unified
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Graphique Combiné",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                
                                // Metric Toggles (Capsules)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    val availableMetrics = listOf(
                                        com.cardio.fitbit.ui.components.TrendMetric.NIGHT to "Nuit (Repos)",
                                        com.cardio.fitbit.ui.components.TrendMetric.DAY to "Jour (Repos)",
                                        com.cardio.fitbit.ui.components.TrendMetric.AVG to "Moyenne",
                                        com.cardio.fitbit.ui.components.TrendMetric.HRV to "HRV",
                                        com.cardio.fitbit.ui.components.TrendMetric.STEPS to "Pas"
                                    )
                                    
                                    availableMetrics.forEach { (metric, label) ->
                                        FilterChip(
                                            selected = selectedMetrics.contains(metric),
                                            onClick = {
                                                selectedMetrics = if (selectedMetrics.contains(metric)) {
                                                    if (selectedMetrics.size > 1) selectedMetrics - metric else selectedMetrics
                                                } else {
                                                    selectedMetrics + metric
                                                }
                                            },
                                            label = { Text(label) },
                                            leadingIcon = if (selectedMetrics.contains(metric)) {
                                                { Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                            } else null
                                        )
                                    }
                                }
                                
                                TrendsChart(
                                    data = state.data,
                                    selectedMetrics = selectedMetrics,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
