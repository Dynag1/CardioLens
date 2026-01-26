package com.cardio.fitbit.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.cardio.fitbit.ui.navigation.AppNavigation
import com.cardio.fitbit.ui.theme.CardioTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import android.graphics.Color
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.cardio.fitbit.data.repository.UserPreferencesRepository
import com.cardio.fitbit.workers.SyncWorker
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Status bar handled in Theme.kt

        checkNotificationPermission()
        setupPeriodicSync()
        observeAppLanguage()

        setContent {
            val appTheme = userPreferencesRepository.appTheme.collectAsState(initial = "system")
            val themeMode by appTheme
            CardioTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Request to disable battery optimization for background sync
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val packageName = packageName
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = android.content.Intent().apply {
                        action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        data = android.net.Uri.parse("package:$packageName")
                    }
                    startActivity(intent)

                } catch (e: Exception) {

                }
            }
        }
    }
    
    private fun setupPeriodicSync() {
        MainScope().launch {
            // Observe sync interval changes
            userPreferencesRepository.syncIntervalMinutes.collect { intervalMinutes ->
                scheduleSync(intervalMinutes)
            }
        }
    }
    
    private fun scheduleSync(intervalMinutes: Int) {
        // If interval is 0, disable periodic sync
        if (intervalMinutes == 0) {
            WorkManager.getInstance(applicationContext).cancelUniqueWork("CardioSyncWork")

            return
        }
        
        // WorkManager requires minimum 15 minutes for PeriodicWorkRequest
        val actualInterval = kotlin.math.max(intervalMinutes, 15)
        
        if (intervalMinutes < 15) {

        }
        
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false) // Allow sync even on low battery
            .build()
        
        val workRequest = PeriodicWorkRequest.Builder(
            SyncWorker::class.java,
            actualInterval.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "CardioSyncWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
        


    }

    private fun observeAppLanguage() {
        MainScope().launch {
            userPreferencesRepository.appLanguage.collect { languageCode ->
                val localeList = if (languageCode == "system") {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(languageCode)
                }
                
                // Only act if different to avoid loops/recreation if flow emits same
                val current = AppCompatDelegate.getApplicationLocales()
                if (current != localeList) {

                   AppCompatDelegate.setApplicationLocales(localeList)
                }
            }
        }
    }
}
