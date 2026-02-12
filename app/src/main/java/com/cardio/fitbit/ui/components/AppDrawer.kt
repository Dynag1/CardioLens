package com.cardio.fitbit.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cardio.fitbit.R

@Composable
fun AppDrawer(
    currentRoute: String?,
    onNavigateToDashboard: (Long) -> Unit, // Pass timestamp
    onNavigateToTrends: () -> Unit,
    onNavigateToWorkouts: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToHealthSettings: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    closeDrawer: () -> Unit,
    currentProviderId: String?
) {
    ModalDrawerSheet {
        Spacer(Modifier.height(12.dp))

        // Aujourd'hui (Dashboard with today)
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Today, contentDescription = null) },
            label = { Text("Aujourd'hui") },
            selected = currentRoute == "dashboard" && false, // Difficult to track "today" specifically in route, maybe just rely on click
            onClick = {
                closeDrawer()
                onNavigateToDashboard(java.util.Date().time)
            }
        )
        
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.TrendingUp, contentDescription = null) },
            label = { Text(stringResource(R.string.menu_trends)) },
            selected = currentRoute == "trends",
            onClick = {
                closeDrawer()
                onNavigateToTrends()
            }
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.DirectionsRun, contentDescription = null) },
            label = { Text("Entraînements") },
            selected = currentRoute == "workouts",
            onClick = {
                closeDrawer()
                onNavigateToWorkouts()
            }
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            label = { Text("Calendrier") },
            selected = currentRoute == "calendar",
            onClick = {
                closeDrawer()
                onNavigateToCalendar()
            }
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Bedtime, contentDescription = null) },
            label = { Text("Sommeil") },
            selected = currentRoute == "sleep",
            onClick = {
                closeDrawer()
                onNavigateToSleep()
            }
        )

        Spacer(Modifier.weight(1f))

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
            label = { Text("Paramètres Santé") },
            selected = false,
            onClick = {
                closeDrawer()
                onNavigateToHealthSettings()
            }
        )

        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(R.string.menu_settings)) },
            selected = false,
            onClick = {
                closeDrawer()
                onNavigateToSettings()
            }
        )

        // Provider Icon Helper
        val providerIcon = when (currentProviderId) {
            "GOOGLE_FIT" -> R.drawable.ic_google_fit_logo
            "health_connect" -> R.drawable.ic_health_connect_logo
            else -> R.drawable.ic_fitbit_logo
        }
        
        NavigationDrawerItem(
            icon = { 
                Icon(
                    painter = painterResource(id = providerIcon),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                ) 
            },
            label = { Text(stringResource(R.string.menu_logout)) },
            selected = false,
            onClick = {
                closeDrawer()
                onLogout()
            }
        )
        Spacer(Modifier.height(12.dp))
    }
}
