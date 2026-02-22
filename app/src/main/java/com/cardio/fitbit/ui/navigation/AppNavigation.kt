package com.cardio.fitbit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cardio.fitbit.ui.MainViewModel
import com.cardio.fitbit.ui.screens.ApiSetupScreen
import com.cardio.fitbit.ui.screens.DashboardScreen
import com.cardio.fitbit.ui.screens.LoginScreen
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.* 
import kotlinx.coroutines.launch
import com.cardio.fitbit.R
import com.cardio.fitbit.ui.screens.SettingsDialog
import com.cardio.fitbit.ui.screens.HealthSettingsDialog

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object ApiSetup : Screen("api_setup")
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object GoogleFitSetup : Screen("google_fit_setup")
    object Trends : Screen("trends")
    object HealthConnectPermissions : Screen("health_connect_permissions")
    object ProviderSelection : Screen("provider_selection")
    object Backup : Screen("backup")
    object Sleep : Screen("sleep")
    object Calendar : Screen("calendar")
    object Workouts : Screen("workouts")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val mainViewModel: MainViewModel = hiltViewModel()
    val startDestination by mainViewModel.startDestination.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val currentProviderId by mainViewModel.currentProviderId.collectAsState(initial = null)

    // Determine if drawer gesture should be enabled (only on root screens)
    val drawerGesturesEnabled = currentRoute?.let { route ->
        route.startsWith(Screen.Dashboard.route) ||
        route.startsWith(Screen.Trends.route) ||
        route == Screen.Workouts.route ||
        route == Screen.Calendar.route ||
        route.startsWith(Screen.Sleep.route)
    } ?: false

    // Dialog States hoisted
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showHealthSettingsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Note: In a real app we might want to show a Splash screen while startDestination is determined
    // But since DataStore is fast, this might be okay.

    // Hoisted Dialogs
    if (showSettingsDialog) {
        // We need to fetch settings state here or pass ViewModel to SettingsDialog
        // For simplicity, we can let Dashboard handle it via callback or instantiate SettingsScreen as a dialog
        // But SettingsDialog in DashboardScreen uses ViewModel.
        // Let's use a simplified approach: Dashboard keeps its dialogs, but we trigger them via callback if they are on Dashboard?
        // Actually, better to have GLOBAL Settings Logic. Ideally MainViewModel should handle this.
        // For now, let's keep it simple: AppDrawer callbacks will be passed to Dashboard?
        // NO, AppDrawer is outside NavHost.
        // We will pass `onNavigateToSettings` to AppDrawer, which sets showSettingsDialog = true.
        // And we render SettingsDialog here at root.
        
        // However, SettingsDialog needs data from DashboardViewModel or MainViewModel. 
        // Let's assume we can get it from MainViewModel or create a SettingsViewModel.
        // Since we are refactoring, let's just make settings a SCREEN or keep it as Dialog but we need access to prefs.
        // DashboardViewModel has prefs. Let's use MainViewModel for prefs if possible, or hiltViewModel<DashboardViewModel>() here (scoping issues).
        // Quickest fix: Use SettingsScreen (Screen.Settings) instead of Dialog for global access?
        // Or render SettingsDialog here using hiltViewModel<DashboardViewModel>() (might create new instance).
        
        // Let's delegate:
        // For this task, "Add Hamburger to all pages", the drawer needs to work.
        // If I click Settings in Drawer, I want to see Settings.
        // I will instantiate `com.cardio.fitbit.ui.components.SettingsDialog` here, 
        // and feed it from `mainViewModel` if it has the data. 
        // `MainViewModel` doesn't seem to have all settings exposed effectively.
        // Let's check `DashboardScreen.kt`: it uses `viewModel.syncIntervalMinutes`, etc.
        // I will SKIP hoisting the Dialogs fully for now to avoid breaking logic, 
        // AND INSTEAD: The Drawer is Global. But the Actions might need to be context aware?
        // No, Settings are global.
        // I will use `Screen.Settings` for settings if possible? There is no `Screen.Settings` in the list, but there is `SettingsScreen.kt`.
        // Let's add `Settings` to `Screen` sealed class and navigate there! Much cleaner.
        // But `HealthSettings` is a Dialog.
        
        // Alternative: Pass `showSettings` state down to Dashboard? No, drawer is outside.
        
        // OK, I'll Wrap NavHost. The Drawer Callbacks will navigate to `Screen.Settings` (I will add it).
        // For HealthSettings, I will also add `Screen.HealthSettings`.
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
             com.cardio.fitbit.ui.components.AppDrawer(
                currentRoute = currentRoute,
                onNavigateToDashboard = { date ->
                    navController.navigate("${Screen.Dashboard.route}?date=$date") {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onNavigateToTrends = { navController.navigate(Screen.Trends.route) },
                onNavigateToWorkouts = { navController.navigate(Screen.Workouts.route) },
                onNavigateToCalendar = { navController.navigate(Screen.Calendar.route) },
                onNavigateToSleep = { 
                    val date = java.util.Date().time
                    navController.navigate("${Screen.Sleep.route}?date=$date") 
                },
                onNavigateToHealthSettings = { 
                    // Open Dialog?
                    showHealthSettingsDialog = true 
                    // Note: We need to render the dialog. We'll do it below NavHost or inside if possible.
                },
                onNavigateToSettings = { 
                    // Show Settings Dialog
                    showSettingsDialog = true
                },
                onLogout = {
                    mainViewModel.logout()
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0)
                    }
                },
                closeDrawer = { scope.launch { drawerState.close() } },
                currentProviderId = currentProviderId
            )
        }
    ) {
        // Render Global Dialogs here if visible
        if (showSettingsDialog) {
            // We need a ViewModel that provides settings. 
            // Let's use DashboardViewModel for now as it has the logic, but scoped to this Dialog?
            // Or MainViewModel? MainViewModel has userPreferencesRepository.
            // I'll use a specific `SettingsViewModel` if exists, or `DashboardViewModel`.
            // Let's try to minimalistically duplicate the binding for Dialog here using MainViewModel if possible or just DashboardViewModel.
            // `DashboardScreen` had `highThreshold`, `notificationsEnabled` etc.
            
            // To be safe and fast, I will rely on DashboardViewModel here.
            val settingsViewModel: com.cardio.fitbit.ui.screens.DashboardViewModel = hiltViewModel()
            val syncInterval by settingsViewModel.syncIntervalMinutes.collectAsState(initial = 15)
            val appLanguage by settingsViewModel.appLanguage.collectAsState(initial = "system")
            val currentTheme by settingsViewModel.appTheme.collectAsState(initial = "system")

            SettingsDialog(
                onDismiss = { showSettingsDialog = false },
                syncInterval = syncInterval,
                onSyncIntervalChange = settingsViewModel::updateSyncInterval,
                currentLanguage = appLanguage,
                onLanguageChange = settingsViewModel::updateAppLanguage,
                currentTheme = currentTheme,
                onThemeChange = settingsViewModel::updateAppTheme,
                onNavigateToBackup = {
                     showSettingsDialog = false
                     navController.navigate(Screen.Backup.route)
                },
                onShowAbout = {
                    showSettingsDialog = false
                    showAboutDialog = true
                }
            )
        }

        if (showHealthSettingsDialog) {
             val settingsViewModel: com.cardio.fitbit.ui.screens.DashboardViewModel = hiltViewModel()
             val notificationsEnabled by settingsViewModel.notificationsEnabled.collectAsState(initial = true)
             val highThreshold by settingsViewModel.highHrThreshold.collectAsState(initial = 120)
             val lowThreshold by settingsViewModel.lowHrThreshold.collectAsState(initial = 50)
             val sleepGoalMinutes by settingsViewModel.sleepGoalMinutes.collectAsState(initial = 480)
             val weeklyWorkoutGoal by settingsViewModel.weeklyWorkoutGoal.collectAsState(initial = 3)
             val dailyStepGoal by settingsViewModel.dailyStepGoal.collectAsState(initial = 10000)
             val dateOfBirth by settingsViewModel.dateOfBirth.collectAsState(initial = null)
 
              HealthSettingsDialog(
                 onDismiss = { showHealthSettingsDialog = false },
                 notificationsEnabled = notificationsEnabled,
                 onNotificationsChange = settingsViewModel::toggleNotifications,
                 highThreshold = highThreshold,
                 onHighThresholdChange = settingsViewModel::updateHighHrThreshold,
                 lowThreshold = lowThreshold,
                 onLowThresholdChange = settingsViewModel::updateLowHrThreshold,
                 sleepGoalMinutes = sleepGoalMinutes,
                 onSleepGoalChange = settingsViewModel::updateSleepGoalMinutes,
                 weeklyWorkoutGoal = weeklyWorkoutGoal,
                 onWeeklyWorkoutGoalChange = settingsViewModel::updateWeeklyWorkoutGoal,
                 dailyStepGoal = dailyStepGoal,
                 onDailyStepGoalChange = settingsViewModel::updateDailyStepGoal,
                 dateOfBirthState = dateOfBirth,
                 onDateOfBirthChange = settingsViewModel::setDateOfBirth,
                 onSyncToWear = settingsViewModel::forceSyncToWear
            )
        }

        if (showAboutDialog) {
            val context = LocalContext.current
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
                            Text(stringResource(R.string.about_app_label), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text("CardioLens")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.about_version_label), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(versionName)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.about_account_label), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(providerName)
                        }

                        HorizontalDivider()

                        // GitHub Links
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        TextButton(
                            onClick = { uriHandler.openUri("https://prog.dynag.co/CardioLens.html") },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(id = android.R.drawable.ic_menu_myplaces), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.about_website))
                            }
                        }
                        TextButton(
                            onClick = { uriHandler.openUri("https://github.com/Dynag1/CardioLens/blob/master/README.md") },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                             Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(id = android.R.drawable.ic_menu_help), contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.about_readme))
                            }
                        }
                        TextButton(
                            onClick = { uriHandler.openUri("https://github.com/Dynag1/CardioLens/releases") },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(id = android.R.drawable.ic_menu_info_details), contentDescription = null, modifier = Modifier.size(16.dp))
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

        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
        composable(Screen.Welcome.route) {
            com.cardio.fitbit.ui.screens.WelcomeScreen(
                onNavigateToProviderSelection = {
                    navController.navigate(Screen.ProviderSelection.route)
                }
            )
        }

        composable(Screen.ProviderSelection.route) {
            com.cardio.fitbit.ui.screens.ProviderSelectionScreen(
                onNavigateToGoogleFitSetup = {
                    navController.navigate(Screen.GoogleFitSetup.route)
                },
                onNavigateToHealthConnectPermissions = {
                    navController.navigate(Screen.HealthConnectPermissions.route)
                }
            )
        }
        
        composable(Screen.HealthConnectPermissions.route) {
            com.cardio.fitbit.ui.screens.HealthConnectPermissionsScreen(
                onPermissionsGranted = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onPermissionsDenied = {
                    // Show error or go back? For now navigate dashboard anyway but it might show empty
                     navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.GoogleFitSetup.route) {
            com.cardio.fitbit.ui.screens.GoogleFitSetupScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDashboard = {
                     navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ApiSetup.route) {
            ApiSetupScreen(
                onCredentialsSaved = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToWelcome = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = "${Screen.Dashboard.route}?date={date}",
            arguments = listOf(
                navArgument("date") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val dateArg = backStackEntry.arguments?.getLong("date")
            val initialDate = if (dateArg != null && dateArg != -1L) dateArg else null

            DashboardScreen(
                initialDate = initialDate,
                onLogout = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onNavigateToTrends = {
                    navController.navigate(Screen.Trends.route)
                },
                onNavigateToBackup = {
                    navController.navigate(Screen.Backup.route)
                },
                onNavigateToCalendar = {
                    navController.navigate(Screen.Calendar.route)
                },
                onNavigateToSleep = { date ->
                    val timestamp = date.time
                    navController.navigate("${Screen.Sleep.route}?date=$timestamp")
                },
                onNavigateToWorkouts = {
                    navController.navigate(Screen.Workouts.route)
                },
                openDrawer = { scope.launch { drawerState.open() } }
            )
        }
        
        composable(Screen.Trends.route) {
            com.cardio.fitbit.ui.screens.TrendsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDashboard = { date ->
                    navController.navigate("${Screen.Dashboard.route}?date=${date.time}") {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                openDrawer = { scope.launch { drawerState.open() } }
            )
        }

        composable(Screen.Calendar.route) {
            com.cardio.fitbit.ui.screens.CalendarScreen(
                onNavigateToDashboard = { date ->
                    navController.navigate("${Screen.Dashboard.route}?date=${date.time}") {
                        // Pop up to Dashboard to avoid back stack loop
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                openDrawer = { scope.launch { drawerState.open() } }
            )
        }

        composable(Screen.Backup.route) {
            com.cardio.fitbit.ui.screens.BackupScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToGoogleSetup = {
                    navController.navigate(Screen.GoogleFitSetup.route)
                }
            )
        }

        composable(
            route = "${Screen.Sleep.route}?date={date}",
            arguments = listOf(
                navArgument("date") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val dateArg = backStackEntry.arguments?.getLong("date") ?: -1L
            val date = if (dateArg != -1L) java.util.Date(dateArg) else java.util.Date()
            
            com.cardio.fitbit.ui.screens.SleepScreen(
                date = date,
                onNavigateBack = {
                    navController.popBackStack()
                },
                openDrawer = { scope.launch { drawerState.open() } }
            )
        }

        composable(Screen.Workouts.route) {
            com.cardio.fitbit.ui.screens.WorkoutsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                openDrawer = { scope.launch { drawerState.open() } }
            )
        }
    }
}
}


