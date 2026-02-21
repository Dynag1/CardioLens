package com.cardio.fitbit.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.res.stringResource
import com.cardio.fitbit.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    initialDate: Long? = null,
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onNavigateToTrends: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSleep: (java.util.Date) -> Unit,
    onNavigateToWorkouts: () -> Unit,
    openDrawer: () -> Unit
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
    val hrvData by viewModel.hrvData.collectAsState()
    val hrvAverage by viewModel.hrvDailyAverage.collectAsState()
    val spo2Data by viewModel.spo2Data.collectAsState()
    val spo2History by viewModel.spo2History.collectAsState()
    val dailySymptoms by viewModel.dailySymptoms.collectAsState()
    val comparisonStats by viewModel.comparisonStats.collectAsState()
    val dateOfBirth by viewModel.dateOfBirth.collectAsState(initial = null)
    
    val readiness by viewModel.readinessData.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val goalProgress by viewModel.goalProgress.collectAsState()

    // Handle Deep Linking / Navigation with Date
    LaunchedEffect(initialDate) {
        if (initialDate != null && initialDate > 0) {
            val date = java.util.Date(initialDate)
            viewModel.setDate(date)
        }
    }

    // Aggregate 1-minute data for Main Chart (Dashboard Only)
    // Keeps main chart readable while preserving seconds for ActivityDetail
    val oneMinuteData = remember(intradayData) {
        intradayData?.minuteData
            ?.groupBy { it.time.substring(0, 5) } // Group by HH:mm
            ?.mapNotNull { (timeKey, entries) ->
                val avgHr = entries.map { it.heartRate }.filter { it > 0 }.average()
                if (avgHr.isNaN()) return@mapNotNull null
                entries.first().copy(
                    time = timeKey,
                    heartRate = avgHr.toInt(),
                    steps = entries.sumOf { it.steps }
                )
            }
            ?.sortedBy { it.time }
            ?: emptyList()
    }

    // Settings States
    val currentProviderId by viewModel.currentProviderId.collectAsState()
    val userMaxHr by viewModel.userMaxHr.collectAsState(initial = 220)

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

    val isAuthorized by viewModel.isAuthorized.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Permission Launcher
    val healthConnectPermissions = com.cardio.fitbit.data.provider.HealthConnectProvider.PERMISSIONS
    val requestPermissions = rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        if (granted.containsAll(healthConnectPermissions)) {
            viewModel.loadAllData(forceRefresh = true)
        }
    }

    // Helper function to get the appropriate icon based on provider
    val getProviderIcon: @Composable () -> Unit = {
        when (currentProviderId) {
            "GOOGLE_FIT" -> Icon(
                painter = androidx.compose.ui.res.painterResource(id = com.cardio.fitbit.R.drawable.ic_google_fit_logo),
                contentDescription = "Google Fit"
            )
            "health_connect" -> Icon(
                painter = androidx.compose.ui.res.painterResource(id = com.cardio.fitbit.R.drawable.ic_health_connect_logo),
                contentDescription = "Health Connect"
            )
            else -> Icon(  // Fitbit
                painter = androidx.compose.ui.res.painterResource(id = com.cardio.fitbit.R.drawable.ic_fitbit_logo),
                contentDescription = "Fitbit"
            )
        }
    }
    
    // Swipe detection state
    // We lift gesturesEnabled to Scaffold content or keep it local
    var drawerGesturesEnabled by remember { mutableStateOf(true) }

    var isReadinessExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = openDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "CardioLens",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = DateUtils.formatForDisplay(selectedDate),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                actions = {
                    // Last Sync Display
                    val lastSync by viewModel.lastSyncTimestamp.collectAsState(initial = 0L)
                    val timestamp = lastSync
                    if (timestamp != null && timestamp > 0) {
                        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
                        Text(
                            text = "Sync: $timeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.changeDate(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Précédent", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { viewModel.changeDate(1) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Suivant", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { viewModel.loadAllData(forceRefresh = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                            text = stringResource(R.string.error_prefix, state.message),
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurface
                        )
                        Button(onClick = { viewModel.loadAllData() }) {
                            Text(stringResource(R.string.retry))
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        
                        
                        // Readiness & Insights Section (Collapsible)
                        if (readiness != null) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                        .clickable { isReadinessExpanded = !isReadinessExpanded },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, 
                                        when {
                                            readiness!!.score >= 80 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                            readiness!!.score >= 60 -> androidx.compose.ui.graphics.Color(0xFFFFB74D)
                                            else -> androidx.compose.ui.graphics.Color(0xFFE57373)
                                        }.copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        // Header (Always Visible)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(if (isReadinessExpanded) 64.dp else 48.dp)) {
                                                CircularProgressIndicator(
                                                    progress = readiness!!.score / 100f,
                                                    modifier = Modifier.matchParentSize(),
                                                    color = when {
                                                        readiness!!.score >= 80 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                                        readiness!!.score >= 60 -> androidx.compose.ui.graphics.Color(0xFFFFB74D)
                                                        else -> androidx.compose.ui.graphics.Color(0xFFE57373)
                                                    },
                                                    strokeWidth = if (isReadinessExpanded) 6.dp else 4.dp,
                                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                )
                                                Text(
                                                    text = readiness!!.score.toString(),
                                                    style = if (isReadinessExpanded) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Score de Récupération",
                                                    style = if (isReadinessExpanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (!isReadinessExpanded) {
                                                    val hasAlert = insights.any { it.type == DashboardViewModel.InsightType.HEALTH_ALERT }
                                                    if (hasAlert) {
                                                        Text(
                                                            text = "â  ALERTE SANTÃ",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    } else {
                                                        Text(
                                                            text = readiness!!.message.take(30) + "...",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                            Icon(
                                                imageVector = if (isReadinessExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Expanded Content
                                        if (isReadinessExpanded) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = readiness!!.message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            
                                            if (insights.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "Analyses & Corrélations",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                )
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    insights.forEach { insight ->
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = insight.color.copy(alpha = 0.1f)
                                                            ),
                                                            shape = RoundedCornerShape(12.dp),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, insight.color.copy(alpha = 0.2f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.padding(10.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = when(insight.type) {
                                                                        DashboardViewModel.InsightType.SLEEP_HRV -> Icons.Default.Nightlight
                                                                        DashboardViewModel.InsightType.ACTIVITY_RHR -> Icons.Default.MonitorHeart
                                                                        DashboardViewModel.InsightType.CORRELATION -> Icons.Default.Troubleshoot
                                                                        DashboardViewModel.InsightType.HEALTH_ALERT -> Icons.Default.Warning
                                                                        else -> Icons.Default.Info
                                                                    },
                                                                    contentDescription = null,
                                                                    tint = insight.color,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                                Text(
                                                                    text = insight.message,
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Personalized Goals (Integrated)
                                            if (goalProgress.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text(
                                                    text = "Objectifs du jour",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(bottom = 8.dp)
                                                )
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    goalProgress.forEach { goal ->
                                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                val label = when (goal.type) {
                                                                    DashboardViewModel.GoalType.STEPS -> "Pas"
                                                                    DashboardViewModel.GoalType.WORKOUTS -> "Entraînements (semaine)"
                                                                }
                                                                Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                                Text(
                                                                    "${goal.current} / ${goal.goal}",
                                                                    style = MaterialTheme.typography.bodySmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (goal.progress >= 1f) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                            LinearProgressIndicator(
                                                                progress = { goal.progress },
                                                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                                                color = if (goal.progress >= 1f) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
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

                        // Comparison Section (Trends vs 7-Day Average)
                        if (comparisonStats != null) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.1f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "Comparaison (Moy. 15 jours)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceAround
                                        ) {


                                            ComparisonItem(
                                                label = "HRV",
                                                currentValue = hrvAverage,
                                                averageValue = comparisonStats?.hrvAvg,
                                                unit = "ms",
                                                reverseColor = false
                                            )
                                            
                                            ComparisonItem(
                                                label = "Repos Nuit",
                                                currentValue = rhrNight,
                                                averageValue = comparisonStats?.rhrNightAvg,
                                                unit = "bpm",
                                                reverseColor = true
                                            )
                                            
                                            ComparisonItem(
                                                label = "Repos Jour",
                                                currentValue = rhrDay,
                                                averageValue = comparisonStats?.rhrDayAvg,
                                                unit = "bpm",
                                                reverseColor = true
                                            )

                                            val totalSteps = activityData?.summary?.steps
                                            ComparisonItem(
                                                label = "Pas",
                                                currentValue = totalSteps,
                                                averageValue = comparisonStats?.stepsAvg,
                                                unit = "",
                                                reverseColor = false
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Chart Section
                        intradayData?.let { data ->
                        
                            // Permission Request Card (if needed)
                            if (!isAuthorized && currentProviderId == "health_connect") {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = "Permissions Manquantes",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "De nouvelles permissions (HRV) sont requises pour afficher toutes les données.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = {
                                                    val availability = androidx.health.connect.client.HealthConnectClient.getSdkStatus(context)
                                                    if (availability == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE) {
                                                        try {
                                                            requestPermissions.launch(healthConnectPermissions)
                                                        } catch (e: Exception) {
                                                            android.widget.Toast.makeText(context, "Erreur lors du lancement : ${e.message}", android.widget.Toast.LENGTH_LONG).show()

                                                        }
                                                    } else {
                                                        val message = when(availability) {
                                                            androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE -> "Health Connect n'est pas installé."
                                                            androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Mise à jour requise pour Health Connect."
                                                            else -> "Health Connect indisponible: $availability"
                                                        }
                                                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.error
                                                )
                                            ) {
                                                Text("Accorder les permissions", color = Color.White)
                                            }
                                            // Extra button to force request all permissions even if some granted
                                            Spacer(modifier = Modifier.height(8.dp))
                                            TextButton(
                                                onClick = {
                                                    requestPermissions.launch(healthConnectPermissions)
                                                }
                                            ) {
                                                Text("Vérifier toutes les permissions", color = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                            
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
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Min/Max
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = "${minHr?.heartRate ?: "--"} / ${maxHr?.heartRate ?: "--"}",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${minHr?.time?.take(5) ?: "--"} / ${maxHr?.time?.take(5) ?: "--"}", // Show time (HH:mm)
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        HeartRateDetailChart(
                                            minuteData = oneMinuteData,
                                            aggregatedData = oneMinuteData, // Force 1-minute resolution even when zoomed out
                                            sleepSessions = sleepData,
                                            activityData = activityData,
                                            restingHeartRate = rhrDay,
                                            userMaxHr = userMaxHr,
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

                        // Mood Selector
                        item {
                            val currentMood by viewModel.dailyMood.collectAsState()
                            MoodSelector(
                                currentRating = currentMood,
                                onRatingSelected = { rating -> viewModel.saveMood(rating) }
                            )
                        }

                        // Symptom Section
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.1f))
                            ) {
                                SymptomSection(
                                    symptoms = dailySymptoms,
                                    onSave = viewModel::saveSymptoms,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                            // Activity Detail Cards (Moved to the bottom)
                        activityData?.activities?.forEach { activity ->
                            item {
                                ActivityDetailCard(
                                    activity = activity,
                                    allMinuteData = intradayData?.preciseData ?: intradayData?.minuteData ?: emptyList(),
                                    selectedDate = selectedDate,
                                    dateOfBirth = dateOfBirth,
                                    onIntensityChange = { activityId, intensity ->
                                        viewModel.saveWorkoutIntensity(activityId, intensity)
                                    }
                                )
                            }
                        }

                        // HRV Card (New)
                        if (hrvData.isNotEmpty() || hrvAverage != null) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) // Consistent style
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.2f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                             Column {
                                                 Text(
                                                     text = stringResource(R.string.chart_hrv),
                                                     style = MaterialTheme.typography.titleMedium,
                                                     fontWeight = FontWeight.Bold,
                                                     color = MaterialTheme.colorScheme.onSurface
                                                 )
                                                 Text(
                                                     text = "${hrvAverage ?: "--"} ms", // Display Daily Average
                                                     style = MaterialTheme.typography.headlineSmall,
                                                     fontWeight = FontWeight.Bold,
                                                     color = MaterialTheme.colorScheme.primary
                                                 )
                                                 Text(
                                                     text = stringResource(R.string.label_hrv_rmssd),
                                                     style = MaterialTheme.typography.bodySmall,
                                                     color = MaterialTheme.colorScheme.onSurfaceVariant
                                                 )
                                             }
                                        }
                                        

                                        if (hrvData.size > 1) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            
                                            HrvChart(
                                                hrvRecords = hrvData,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(150.dp) // Smaller than main chart
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // SpO2 Card
                        if (spo2Data != null || spo2History.isNotEmpty()) {
                            item {
                                SpO2Card(
                                    currentData = spo2Data,
                                    history = spo2History,
                                    modifier = Modifier.fillMaxWidth()
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

@Composable
private fun ComparisonItem(
    label: String,
    currentValue: Int?,
    averageValue: Int?,
    unit: String,
    reverseColor: Boolean = false // false: High=Green (HRV), true: Low=Green (RHR)
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = currentValue?.toString() ?: "--",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (currentValue != null && averageValue != null) {
            val diff = currentValue - averageValue
            val sign = if (diff > 0) "+" else ""
            val isGood = if (reverseColor) diff <= 0 else diff >= 0
            val color = if (isGood) Color(0xFF4CAF50) else Color(0xFFE57373)

            Text(
                text = "$sign$diff $unit",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                text = "--",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Transparent
            )
        }
    }
}
