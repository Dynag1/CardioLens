package com.cardio.fitbit.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cardio.fitbit.ui.MainViewModel
import com.cardio.fitbit.ui.screens.ApiSetupScreen
import com.cardio.fitbit.ui.screens.DashboardScreen
import com.cardio.fitbit.ui.screens.LoginScreen
import androidx.navigation.navArgument
import androidx.navigation.NavType

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

    // Note: In a real app we might want to show a Splash screen while startDestination is determined
    // But since DataStore is fast, this might be okay.

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
                onNavigateToFitbitSetup = {
                    navController.navigate(Screen.ApiSetup.route)
                },
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
                    navController.navigate(Screen.Login.route) {
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
                }
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
                }
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
                }
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
                }
            )
        }

        composable(Screen.Workouts.route) {
            com.cardio.fitbit.ui.screens.WorkoutsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
