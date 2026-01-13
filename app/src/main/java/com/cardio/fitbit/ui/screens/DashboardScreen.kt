package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cardio.fitbit.data.models.*
import com.cardio.fitbit.ui.components.*
import com.cardio.fitbit.ui.theme.*
import com.cardio.fitbit.utils.DateUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val heartRateData by viewModel.heartRateData.collectAsState()
    val sleepData by viewModel.sleepData.collectAsState()
    val stepsData by viewModel.stepsData.collectAsState()
    val activityData by viewModel.activityData.collectAsState()
    val intradayData by viewModel.intradayData.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    
    // Derived
    val rhrDay by viewModel.rhrDay.collectAsState()
    val rhrNight by viewModel.rhrNight.collectAsState()
    val aggregatedMinuteData by viewModel.aggregatedMinuteData.collectAsState()

    // Settings States
    val highThreshold by viewModel.highHrThreshold.collectAsState(initial = 120)
    val lowThreshold by viewModel.lowHrThreshold.collectAsState(initial = 50)
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val syncInterval by viewModel.syncIntervalMinutes.collectAsState(initial = 15)

    var showSettingsDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // State to control if drawer gestures are enabled
    var drawerGesturesEnabled by remember { mutableStateOf(true) }

    if (showSettingsDialog) {
        SettingsDialog(
            onDismiss = { showSettingsDialog = false },
            highThreshold = highThreshold,
            lowThreshold = lowThreshold,
            notificationsEnabled = notificationsEnabled,
            syncInterval = syncInterval,
            onHighThresholdChange = viewModel::updateHighHrThreshold,
            onLowThresholdChange = viewModel::updateLowHrThreshold,
            onNotificationsChange = viewModel::toggleNotifications,
            onSyncIntervalChange = viewModel::updateSyncInterval
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Paramètres") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showSettingsDialog = true
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
                    label = { Text("Déconnexion") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.logout()
                        onLogout()
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = "CardioLens",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = DateUtils.formatForDisplay(selectedDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface.copy(alpha = 0.7f)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.changeDate(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Précédent")
                        }
                        IconButton(onClick = { viewModel.changeDate(1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Suivant")
                        }
                        IconButton(onClick = { viewModel.loadAllData(forceRefresh = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = OnSurface)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Surface,
                        titleContentColor = OnSurface
                    )
                )
            },
            containerColor = androidx.compose.ui.graphics.Color.White
        ) { paddingValues ->
            when (val state = uiState) {
                is DashboardUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
                is DashboardUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Error,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                text = "Erreur: ${state.message}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = OnSurface
                            )
                            Button(onClick = { viewModel.loadAllData() }) {
                                Text("Réessayer")
                            }
                        }
                    }
                }
                is DashboardUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Chart Section
                        intradayData?.let { data ->
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White), // Explicit White
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Fréquence Cardiaque",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )
                                        HeartRateDetailChart(
                                            minuteData = data.minuteData,
                                            aggregatedData = aggregatedMinuteData,
                                            sleepData = sleepData,
                                            activityData = activityData,
                                            selectedDate = selectedDate,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(350.dp),
                                            onChartInteraction = { isInteracting ->
                                                drawerGesturesEnabled = !isInteracting
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Activity Detail Cards (Moved here for better visibility)
                        activityData?.activities?.forEach { activity ->
                            item {
                                ActivityDetailCard(
                                    activity = activity,
                                    allMinuteData = intradayData?.minuteData ?: emptyList(),
                                    selectedDate = selectedDate
                                )
                            }
                        }

                        // Total Steps Card
                        item {
                            val totalSteps = aggregatedMinuteData.sumOf { it.steps }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Surface),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Nombre de pas total",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = OnSurface.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "$totalSteps",
                                            style = MaterialTheme.typography.displayMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Primary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.DirectionsWalk,
                                        contentDescription = null,
                                        tint = Primary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }
                        
                        // Resting Heart Rate Card
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Surface),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = Icons.Default.WbSunny,
                                            contentDescription = "Jour",
                                            tint = Color(0xFFFFB74D), // Orange
                                            modifier = Modifier.size(32.dp).padding(end = 8.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Repos Jour",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = OnSurface.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = rhrDay?.let { "$it bpm" } ?: "--",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = OnSurface
                                            )
                                        }
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(40.dp)
                                            .background(OnSurface.copy(alpha = 0.1f))
                                    )
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically, 
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "Repos Nuit",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = OnSurface.copy(alpha = 0.7f)
                                            )
                                            Text(
                                                text = rhrNight?.let { "$it bpm" } ?: "--",
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = OnSurface
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.NightsStay,
                                            contentDescription = "Nuit",
                                            tint = Color(0xFF9575CD), // Purple
                                            modifier = Modifier.size(32.dp).padding(start = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
