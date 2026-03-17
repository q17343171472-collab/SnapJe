package com.rapii.snapje.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rapii.snapje.ui.SettingsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore instance for app settings.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "snapje_settings")

/**
 * Settings keys.
 */
object SettingsKeys {
    val GRID_COLUMNS = intPreferencesKey("grid_columns")
    val THEME = stringPreferencesKey("theme")
    val DEFAULT_SORT = stringPreferencesKey("default_sort")
    val REVERSE_SORT = booleanPreferencesKey("reverse_sort")
    val CACHE_SIZE_MB = intPreferencesKey("cache_size_mb")
}

/**
 * Manager for app settings using Jetpack DataStore.
 * Provides type-safe, async settings persistence.
 */
@Singleton
class SettingsManager @Inject constructor(
    private val context: Context
) {
    /**
     * Get settings as a Flow for reactive UI updates.
     */
    val settingsFlow: Flow<SettingsState> = context.dataStore.data.map { preferences ->
        SettingsState(
            gridColumns = preferences[SettingsKeys.GRID_COLUMNS] ?: 3,
            theme = preferences[SettingsKeys.THEME] ?: "System",
            defaultSort = preferences[SettingsKeys.DEFAULT_SORT] ?: "Date (Newest)",
            reverseSort = preferences[SettingsKeys.REVERSE_SORT] ?: false,
            cacheSizeMB = preferences[SettingsKeys.CACHE_SIZE_MB] ?: 100
        )
    }

    /**
     * Update grid columns setting.
     */
    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { preferences ->
            preferences[SettingsKeys.GRID_COLUMNS] = columns
        }
    }

    /**
     * Update theme setting.
     */
    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[SettingsKeys.THEME] = theme
        }
    }

    /**
     * Update default sort setting.
     */
    suspend fun setDefaultSort(sort: String) {
        context.dataStore.edit { preferences ->
            preferences[SettingsKeys.DEFAULT_SORT] = sort
        }
    }

    /**
     * Update reverse sort setting.
     */
    suspend fun setReverseSort(reverse: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SettingsKeys.REVERSE_SORT] = reverse
        }
    }

    /**
     * Update cache size limit.
     */
    suspend fun setCacheSizeMB(size: Int) {
        context.dataStore.edit { preferences ->
            preferences[SettingsKeys.CACHE_SIZE_MB] = size
        }
    }

    /**
     * Save complete settings state.
     */
    suspend fun saveSettings(state: SettingsState) {
        context.dataStore.edit { preferences ->
            preferences[SettingsKeys.GRID_COLUMNS] = state.gridColumns
            preferences[SettingsKeys.THEME] = state.theme
            preferences[SettingsKeys.DEFAULT_SORT] = state.defaultSort
            preferences[SettingsKeys.REVERSE_SORT] = state.reverseSort
            preferences[SettingsKeys.CACHE_SIZE_MB] = state.cacheSizeMB
        }
    }

    /**
     * Clear all settings (reset to defaults).
     */
    suspend fun clearAllSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
