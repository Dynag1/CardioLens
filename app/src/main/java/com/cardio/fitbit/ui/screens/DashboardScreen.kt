package com.cardio.fitbit.ui.screens

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
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
    val sleepData by viewModel.sleepData.collectAsState()
    val activityData by viewModel.activityData.collectAsState()
    val intradayData by viewModel.intradayData.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    
    // Derived
    val rhrDay by viewModel.rhrDay.collectAsState()
    val rhrNight by viewModel.rhrNight.collectAsState()
    val aggregatedMinuteData by viewModel.aggregatedMinuteData.collectAsState()
    val minHr by viewModel.minHr.collectAsState()
    val maxHr by viewModel.maxHr.collectAsState()

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

    // Pull to Refresh State
    val pullRefreshState = rememberPullToRefreshState()

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.loadAllData(forceRefresh = true)
        }
    }

    LaunchedEffect(uiState) {
        if (uiState !is DashboardUiState.Loading) {
            pullRefreshState.endRefresh()
        }
    }

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
                
                // Push disconnect to bottom
                Spacer(Modifier.weight(1f))
                
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
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Black)
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = "CardioLens",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text(
                                text = DateUtils.formatForDisplay(selectedDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Black.copy(alpha = 0.7f)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.changeDate(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Précédent", tint = Color.Black)
                        }
                        IconButton(onClick = { viewModel.changeDate(1) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Suivant", tint = Color.Black)
                        }
                        IconButton(onClick = { viewModel.loadAllData(forceRefresh = true) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = Color.Black)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFF5F5F5),
                        titleContentColor = Color.Black,
                        actionIconContentColor = Color.Black
                    )
                )
            },
            containerColor = androidx.compose.ui.graphics.Color.White
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .nestedScroll(pullRefreshState.nestedScrollConnection)
            ) {
                when (val state = uiState) {
                    is DashboardUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    // Swipe detection state
                    var swipeOffset by remember { mutableFloatStateOf(0f) }
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (kotlin.math.abs(swipeOffset) > 100) {
                                            if (swipeOffset > 0) {
                                                viewModel.changeDate(-1) // Swipe right = previous
                                            } else {
                                                viewModel.changeDate(1)  // Swipe left = next
                                            }
                                        }
                                        swipeOffset = 0f
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        swipeOffset += dragAmount
                                    }
                                )
                            },
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Chart Section
                        intradayData?.let { data ->
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                            horizontalArrangement = Arrangement.SpaceAround,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Resting HR
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "${rhrNight ?: "--"} / ${rhrDay ?: "--"}",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Repos (N/J)",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            // Min/Max
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "${minHr ?: "--"} / ${maxHr ?: "--"}",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Min / Max",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            // Steps
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                val totalSteps = activityData?.summary?.steps ?: 0
                                                Text(
                                                    text = totalSteps.toString(),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Pas",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        HeartRateDetailChart(
                                            minuteData = data.minuteData,
                                            aggregatedData = aggregatedMinuteData,
                                            sleepSessions = sleepData,
                                            activityData = activityData,
                                            restingHeartRate = rhrDay,
                                            selectedDate = selectedDate,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(280.dp),
                                            onChartInteraction = { isInteracting ->
                                                drawerGesturesEnabled = !isInteracting
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Activity Detail Cards (Moved to the bottom)
                        activityData?.activities?.forEach { activity ->
                            item {
                                ActivityDetailCard(
                                    activity = activity,
                                    allMinuteData = intradayData?.minuteData ?: emptyList(),
                                    selectedDate = selectedDate
                                )
                            }
                        }
                    }
                }
                }
                
                PullToRefreshContainer(
                    state = pullRefreshState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}
