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
        val APP_LANGUAGE = androidx.datastore.preferences.core.stringPreferencesKey("app_language")
        val APP_THEME = androidx.datastore.preferences.core.stringPreferencesKey("app_theme")
        // BYOK Credentials
        val CLIENT_ID = androidx.datastore.preferences.core.stringPreferencesKey("client_id")
        val CLIENT_SECRET = androidx.datastore.preferences.core.stringPreferencesKey("client_secret")
        
        // Google Credentials
        val GOOGLE_CLIENT_ID = androidx.datastore.preferences.core.stringPreferencesKey("google_client_id")
        val GOOGLE_CLIENT_SECRET = androidx.datastore.preferences.core.stringPreferencesKey("google_client_secret")
        val GOOGLE_ACCESS_TOKEN = androidx.datastore.preferences.core.stringPreferencesKey("google_access_token")
        val GOOGLE_REFRESH_TOKEN = androidx.datastore.preferences.core.stringPreferencesKey("google_refresh_token")
        
        val USE_HEALTH_CONNECT = booleanPreferencesKey("use_health_connect")
        val LAST_SYNC_TIMESTAMP = androidx.datastore.preferences.core.longPreferencesKey("last_sync_timestamp")
        val DATE_OF_BIRTH = androidx.datastore.preferences.core.longPreferencesKey("date_of_birth")
        
        val GOOGLE_DRIVE_BACKUP_ENABLED = booleanPreferencesKey("google_drive_backup_enabled")
        val BACKUP_URI = androidx.datastore.preferences.core.stringPreferencesKey("backup_uri")
        val SLEEP_GOAL_MINUTES = intPreferencesKey("sleep_goal_minutes")
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

    val appLanguage: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.APP_LANGUAGE] ?: "system" // Default system
    }

    val appTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.APP_THEME] ?: "system" // Default system
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

    suspend fun setAppLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_LANGUAGE] = languageCode
        }
    }

    suspend fun setAppTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_THEME] = theme
        }
    }

    suspend fun setApiCredentials(clientId: String, clientSecret: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIENT_ID] = clientId
            preferences[PreferencesKeys.CLIENT_SECRET] = clientSecret
        }
    }
    
    // Google Flows
    val googleClientId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GOOGLE_CLIENT_ID]
    }

    val googleClientSecret: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GOOGLE_CLIENT_SECRET]
    }
    
    suspend fun setGoogleCredentials(clientId: String, clientSecret: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GOOGLE_CLIENT_ID] = clientId
            preferences[PreferencesKeys.GOOGLE_CLIENT_SECRET] = clientSecret
        }
    }

    suspend fun saveGoogleTokens(accessToken: String, refreshToken: String?) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GOOGLE_ACCESS_TOKEN] = accessToken
            if (refreshToken != null) {
                preferences[PreferencesKeys.GOOGLE_REFRESH_TOKEN] = refreshToken
            }
        }
    }
    
    val googleAccessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GOOGLE_ACCESS_TOKEN]
    }

    val googleRefreshToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GOOGLE_REFRESH_TOKEN]
    }

    // Health Connect Preferences
    val useHealthConnect: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USE_HEALTH_CONNECT] ?: false
    }

    suspend fun setUseHealthConnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_HEALTH_CONNECT] = enabled
        }
    }

    val lastSyncTimestamp: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP]
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    // Dynamic HR Zones Preferences
    val dateOfBirth: Flow<Long?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DATE_OF_BIRTH]
    }

    suspend fun setDateOfBirth(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DATE_OF_BIRTH] = timestamp
        }
    }

    val googleDriveBackupEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GOOGLE_DRIVE_BACKUP_ENABLED] ?: false
    }

    suspend fun setGoogleDriveBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GOOGLE_DRIVE_BACKUP_ENABLED] = enabled
        }
    }
    
    val backupUri: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BACKUP_URI]
    }

    suspend fun setBackupUri(uri: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKUP_URI] = uri
        }
    }
    val sleepGoalMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SLEEP_GOAL_MINUTES] ?: 480 // Default 8 hours (480 min)
    }

    suspend fun setSleepGoalMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SLEEP_GOAL_MINUTES] = minutes
        }
    }
}
