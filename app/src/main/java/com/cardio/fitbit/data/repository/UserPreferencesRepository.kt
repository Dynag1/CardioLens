package com.cardio.fitbit.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val HIGH_HR_THRESHOLD = intPreferencesKey("high_hr_threshold")
        val LOW_HR_THRESHOLD = intPreferencesKey("low_hr_threshold")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SYNC_INTERVAL_MINUTES = intPreferencesKey("sync_interval_minutes")
        // BYOK Credentials
        val CLIENT_ID = androidx.datastore.preferences.core.stringPreferencesKey("client_id")
        val CLIENT_SECRET = androidx.datastore.preferences.core.stringPreferencesKey("client_secret")
    }

    val highHrThreshold: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HIGH_HR_THRESHOLD] ?: 120 // Default 120
    }

    val lowHrThreshold: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LOW_HR_THRESHOLD] ?: 50 // Default 50
    }

    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] ?: true // Default true
    }
    
    val syncIntervalMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SYNC_INTERVAL_MINUTES] ?: 15 // Default 15 min
    }

    // BYOK Flows
    val clientId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CLIENT_ID]
    }

    val clientSecret: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CLIENT_SECRET]
    }

    // Helper to check if API is configured
    val areKeysSet: Flow<Boolean> = context.dataStore.data.map { preferences ->
        !preferences[PreferencesKeys.CLIENT_ID].isNullOrBlank() && 
        !preferences[PreferencesKeys.CLIENT_SECRET].isNullOrBlank()
    }

    suspend fun setHighHrThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HIGH_HR_THRESHOLD] = threshold
        }
    }

    suspend fun setLowHrThreshold(threshold: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOW_HR_THRESHOLD] = threshold
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }
    
    suspend fun setSyncIntervalMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SYNC_INTERVAL_MINUTES] = minutes
        }
    }

    suspend fun setApiCredentials(clientId: String, clientSecret: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIENT_ID] = clientId
            preferences[PreferencesKeys.CLIENT_SECRET] = clientSecret
        }
    }
}
