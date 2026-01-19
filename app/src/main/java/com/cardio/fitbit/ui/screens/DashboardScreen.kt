package com.cardio.fitbit.ui.screens

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
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogout: () -> Unit,
    onNavigateToTrends: () -> Unit,
    onNavigateToBackup: () -> Unit
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
    val highThreshold by viewModel.highHrThreshold.collectAsState(initial = 120)
    val lowThreshold by viewModel.lowHrThreshold.collectAsState(initial = 50)
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val syncInterval by viewModel.syncIntervalMinutes.collectAsState(initial = 15)
    val currentProviderId by viewModel.currentProviderId.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState(initial = "system")
    val dateOfBirth by viewModel.dateOfBirth.collectAsState(initial = null)
    val userMaxHr by viewModel.userMaxHr.collectAsState(initial = 220)


    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

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

    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.loadAllData(forceRefresh = true)
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
            onSyncIntervalChange = viewModel::updateSyncInterval,

            currentLanguage = appLanguage,
            onLanguageChange = viewModel::updateAppLanguage,
            dateOfBirthState = dateOfBirth,
            onDateOfBirthChange = viewModel::setDateOfBirth
        )
    }

    if (showAboutDialog) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val packageInfo = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            null
        }
        val versionName = packageInfo?.versionName ?: stringResource(R.string.version_unknown)
        
        val providerName = when (currentProviderId) {
            "GOOGLE_FIT" -> stringResource(R.string.provider_google_fit)
            "health_connect" -> stringResource(R.string.provider_health_connect)
            else -> stringResource(R.string.provider_fitbit)
        }
        


        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.about_app_label), fontWeight = FontWeight.Bold)
                        Text("CardioLens")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.about_version_label), fontWeight = FontWeight.Bold)
                        Text(versionName)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.about_account_label), fontWeight = FontWeight.Bold)
                        Text(providerName)
                    }

                    HorizontalDivider()

                    // GitHub Links
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    TextButton(
                        onClick = { uriHandler.openUri("https://github.com/Dynag1/CardioLens/blob/master/README.md") },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_help), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.about_readme))
                        }
                    }
                    TextButton(
                        onClick = { uriHandler.openUri("https://github.com/Dynag1/CardioLens/releases") },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(androidx.compose.ui.res.painterResource(id = android.R.drawable.ic_menu_info_details), contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.about_release_notes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_trends)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToTrends()
                    }
                )
                
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_settings)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showSettingsDialog = true
                    }
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    label = { Text("Sauvegarde / Restauration") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToBackup()
                    }
                )
                
                // Push disconnect to bottom
                
                
                // Push disconnect to bottom
                Spacer(Modifier.weight(1f))
                
                // About
                 NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_about)) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showAboutDialog = true
                    }
                )
                
                NavigationDrawerItem(
                    icon = { getProviderIcon() },
                    label = { Text(stringResource(R.string.menu_logout)) },
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
                        // Last Sync Display
                        val lastSync by viewModel.lastSyncTimestamp.collectAsState(initial = 0L)
                        val timestamp = lastSync
                        if (timestamp != null && timestamp > 0) {
                            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
                            Text(
                                text = "Sync: $timeStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }

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
                        
                        // Mood Selector
                        item {
                            val currentMood by viewModel.dailyMood.collectAsState()
                            MoodSelector(
                                currentRating = currentMood,
                                onRatingSelected = { rating -> viewModel.saveMood(rating) }
                            )
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
                                                    text = stringResource(R.string.label_resting_hr),
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
                                                    text = stringResource(R.string.label_min_max),
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
                                                    text = stringResource(R.string.label_steps),
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
                                    allMinuteData = intradayData?.minuteData ?: emptyList(),
                                    selectedDate = selectedDate,
                                    dateOfBirth = dateOfBirth
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
}
