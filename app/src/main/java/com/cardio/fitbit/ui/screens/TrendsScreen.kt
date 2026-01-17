package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardio.fitbit.ui.components.TrendsChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    viewModel: TrendsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

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

                        // Card 1: Night RHR
                        Card(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Nuit (Sommeil)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1565C0) // Blue title
                                )
                                Spacer(Modifier.height(8.dp))
                                TrendsChart(
                                    data = state.data,
                                    type = com.cardio.fitbit.ui.components.TrendsChartType.NIGHT,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Card 2: Day RHR
                        Card(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Jour (Repos)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFB8C00) // Orange title
                                )
                                Spacer(Modifier.height(8.dp))
                                TrendsChart(
                                    data = state.data,
                                    type = com.cardio.fitbit.ui.components.TrendsChartType.DAY,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Card 3: Average RHR
                        Card(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Moyenne Globale",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF7B1FA2) // Purple title
                                )
                                Spacer(Modifier.height(8.dp))
                                TrendsChart(
                                    data = state.data,
                                    type = com.cardio.fitbit.ui.components.TrendsChartType.AVERAGE,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Card 4: HRV Trends
                        Card(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Variabilité Cardiaque (HRV)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE91E63) // Pink title
                                )
                                Spacer(Modifier.height(8.dp))
                                TrendsChart(
                                    data = state.data,
                                    type = com.cardio.fitbit.ui.components.TrendsChartType.HRV,
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
