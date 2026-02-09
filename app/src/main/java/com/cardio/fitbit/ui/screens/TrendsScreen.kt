package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Share
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
import kotlinx.coroutines.launch

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
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val scope = androidx.compose.runtime.rememberCoroutineScope()
                    
                    if (uiState is TrendsUiState.Success) {
                        IconButton(onClick = { 
                            scope.launch {
                                val file = viewModel.generatePdf(context)
                                if (file != null) {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Partager Rapport"))
                                }
                            }
                        }) {
                             Icon(Icons.Default.Share, contentDescription = "Exporter")
                        }
                    }

                    IconButton(onClick = { viewModel.loadTrends() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
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
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Humeur",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50) // Green title is okay
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
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Unified Trends Chart
                        Card(
                            modifier = Modifier.fillMaxWidth().height(450.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                        com.cardio.fitbit.ui.components.TrendMetric.STEPS to "Pas",
                                        com.cardio.fitbit.ui.components.TrendMetric.WORKOUTS to "Entraînements"
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

                        // Insights / Correlations Section
                        Text(
                            text = "Insights & Corrélations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (state.correlations.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.correlations.forEach { correlation ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (correlation.isPositive) 
                                                Color(0xFFE8F5E9) // Light Green
                                            else 
                                                Color(0xFFFFEBEE) // Light Red
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (correlation.isPositive) Icons.Default.CheckCircle else Icons.Default.Info,
                                                contentDescription = null,
                                                tint = if (correlation.isPositive) Color(0xFF4CAF50) else Color(0xFFE57373),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(16.dp))
                                            Column {
                                                Text(
                                                    text = correlation.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.Black // Ensure contrast on light pastel
                                                )
                                                Text(
                                                    text = correlation.description,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.DarkGray
                                                )
                                                Text(
                                                    text = "Impact: ${correlation.impact}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (correlation.isPositive) Color(0xFF2E7D32) else Color(0xFFC62828) // Darker Green/Red
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Empty State
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text(
                                        text = "Pas encore assez de données pour détecter des corrélations. Continuez à enregistrer votre humeur et vos symptômes !",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}
