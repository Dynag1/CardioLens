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

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object ApiSetup : Screen("api_setup")
    object Login : Screen("login")
    object Dashboard : Screen("dashboard")
    object GoogleFitSetup : Screen("google_fit_setup")
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
                onNavigateToFitbitSetup = {
                    navController.navigate(Screen.ApiSetup.route)
                },
                onNavigateToGoogleFitSetup = {
                    navController.navigate(Screen.GoogleFitSetup.route)
                },
                onNavigateToDashboard = {
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

        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onLogout = {
                    // When logging out, we might want to clear keys? Likely not, just auth.
                    // If user wants to change keys, we'd need a settings option.
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
