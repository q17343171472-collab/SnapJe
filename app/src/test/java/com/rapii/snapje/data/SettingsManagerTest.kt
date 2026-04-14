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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

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
 * Unit tests for SettingsManager.
 * Tests settings persistence and default values.
 */
class SettingsManagerTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setup() {
        // Mock context for testing
        context = mock(Context::class.java)
        settingsManager = SettingsManager(context)
    }

    @Test
    fun `default settings should have correct values`() = runTest {
        // Given: No settings have been saved
        // When: Reading settings for the first time
        val settings = settingsManager.settingsFlow.first()
        
        // Then: Default values should be returned
        assertEquals(3, settings.gridColumns)
        assertEquals("System", settings.theme)
        assertEquals("Date (Newest)", settings.defaultSort)
        assertFalse(settings.reverseSort)
        assertEquals(100, settings.cacheSizeMB)
    }

    @Test
    fun `setGridColumns should update grid columns setting`() = runTest {
        // Given: Initial settings
        val initialSettings = settingsManager.settingsFlow.first()
        assertEquals(3, initialSettings.gridColumns)
        
        // When: Setting grid columns to 4
        settingsManager.setGridColumns(4)
        
        // Then: Settings should reflect the change
        val updatedSettings = settingsManager.settingsFlow.first()
        assertEquals(4, updatedSettings.gridColumns)
    }

    @Test
    fun `setTheme should update theme setting`() = runTest {
        // Given: Initial theme is System
        val initialSettings = settingsManager.settingsFlow.first()
        assertEquals("System", initialSettings.theme)
        
        // When: Setting theme to Dark
        settingsManager.setTheme("Dark")
        
        // Then: Theme should be updated
        val updatedSettings = settingsManager.settingsFlow.first()
        assertEquals("Dark", updatedSettings.theme)
    }

    @Test
    fun `setReverseSort should toggle reverse sort setting`() = runTest {
        // Given: Reverse sort is initially false
        val initialSettings = settingsManager.settingsFlow.first()
        assertFalse(initialSettings.reverseSort)
        
        // When: Enabling reverse sort
        settingsManager.setReverseSort(true)
        
        // Then: Reverse sort should be enabled
        val updatedSettings = settingsManager.settingsFlow.first()
        assertTrue(updatedSettings.reverseSort)
    }

    @Test
    fun `setCacheSizeMB should update cache size limit`() = runTest {
        // Given: Initial cache size is 100 MB
        val initialSettings = settingsManager.settingsFlow.first()
        assertEquals(100, initialSettings.cacheSizeMB)
        
        // When: Setting cache size to 200 MB
        settingsManager.setCacheSizeMB(200)
        
        // Then: Cache size should be updated
        val updatedSettings = settingsManager.settingsFlow.first()
        assertEquals(200, updatedSettings.cacheSizeMB)
    }

    @Test
    fun `saveSettings should save all settings at once`() = runTest {
        // Given: A complete settings state
        val newState = SettingsState(
            gridColumns = 4,
            theme = "Light",
            defaultSort = "Name (A-Z)",
            reverseSort = true,
            cacheSizeMB = 500
        )
        
        // When: Saving all settings
        settingsManager.saveSettings(newState)
        
        // Then: All settings should be updated
        val updatedSettings = settingsManager.settingsFlow.first()
        assertEquals(4, updatedSettings.gridColumns)
        assertEquals("Light", updatedSettings.theme)
        assertEquals("Name (A-Z)", updatedSettings.defaultSort)
        assertTrue(updatedSettings.reverseSort)
        assertEquals(500, updatedSettings.cacheSizeMB)
    }

    @Test
    fun `clearAllSettings should reset to defaults`() = runTest {
        // Given: Custom settings
        settingsManager.setGridColumns(4)
        settingsManager.setTheme("Dark")
        settingsManager.setReverseSort(true)
        
        // Verify custom settings are applied
        val customSettings = settingsManager.settingsFlow.first()
        assertEquals(4, customSettings.gridColumns)
        assertEquals("Dark", customSettings.theme)
        assertTrue(customSettings.reverseSort)
        
        // When: Clearing all settings
        settingsManager.clearAllSettings()
        
        // Then: Settings should reset to defaults
        val defaultSettings = settingsManager.settingsFlow.first()
        assertEquals(3, defaultSettings.gridColumns)
        assertEquals("System", defaultSettings.theme)
        assertFalse(defaultSettings.reverseSort)
    }
}
