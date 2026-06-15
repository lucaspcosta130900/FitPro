package com.fitpro.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fitpro_prefs")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val ANTHROPIC_API_KEY = stringPreferencesKey("anthropic_api_key")
        val USER_NAME = stringPreferencesKey("user_name")
        val HEIGHT_CM = floatPreferencesKey("height_cm")
        val AGE_YEARS = intPreferencesKey("age_years")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences())
            else throw e
        }
        .map { prefs ->
            UserPreferences(
                anthropicApiKey = prefs[ANTHROPIC_API_KEY] ?: "",
                userName = prefs[USER_NAME] ?: "Lucas",
                heightCm = prefs[HEIGHT_CM] ?: 177f,
                ageYears = prefs[AGE_YEARS] ?: 25,
                darkMode = prefs[DARK_MODE] ?: true,
                onboardingDone = prefs[ONBOARDING_DONE] ?: false,
                notificationEnabled = prefs[NOTIFICATION_ENABLED] ?: true
            )
        }

    suspend fun setApiKey(key: String) {
        context.dataStore.edit { it[ANTHROPIC_API_KEY] = key }
    }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { it[USER_NAME] = name }
    }

    suspend fun setHeight(cm: Float) {
        context.dataStore.edit { it[HEIGHT_CM] = cm }
    }

    suspend fun setAge(years: Int) {
        context.dataStore.edit { it[AGE_YEARS] = years }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[DARK_MODE] = enabled }
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[ONBOARDING_DONE] = true }
    }
}

data class UserPreferences(
    val anthropicApiKey: String,
    val userName: String,
    val heightCm: Float,
    val ageYears: Int,
    val darkMode: Boolean,
    val onboardingDone: Boolean,
    val notificationEnabled: Boolean
)
