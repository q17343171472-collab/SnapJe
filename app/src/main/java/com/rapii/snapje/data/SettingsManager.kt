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
    val PIN_HASH = stringPreferencesKey("pin_hash")
    val PIN_SALT = stringPreferencesKey("pin_salt")
    val PIN_LENGTH = intPreferencesKey("pin_length")
    val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
    val AUTO_DELETE_ORIGINAL = booleanPreferencesKey("auto_delete_original")
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
            theme = preferences[SettingsKeys.THEME] ?: "跟随系统",
            defaultSort = preferences[SettingsKeys.DEFAULT_SORT] ?: "最新优先",
            reverseSort = preferences[SettingsKeys.REVERSE_SORT] ?: false,
            cacheSizeMB = preferences[SettingsKeys.CACHE_SIZE_MB] ?: 100,
            pinEnabled = preferences[SettingsKeys.PIN_ENABLED] ?: true,
            autoDeleteOriginal = preferences[SettingsKeys.AUTO_DELETE_ORIGINAL] ?: false
        )
    }

    /**
     * 是否启用启动密码验证（默认开启，可在设置中关闭）。
     */
    suspend fun isPinEnabled(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[SettingsKeys.PIN_ENABLED] ?: true
        }.first()
    }

    /**
     * 开启/关闭启动密码验证。
     */
    suspend fun setPinEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.PIN_ENABLED] = enabled
        }
    }

    /**
     * 是否开启"导入后自动删除相册原图"（默认关闭；关闭时导入后询问）。
     */
    suspend fun isAutoDeleteOriginal(): Boolean {
        return context.dataStore.data.map { prefs ->
            prefs[SettingsKeys.AUTO_DELETE_ORIGINAL] ?: false
        }.first()
    }

    /**
     * 开启/关闭"导入后自动删除相册原图"。
     */
    suspend fun setAutoDeleteOriginal(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.AUTO_DELETE_ORIGINAL] = enabled
        }
    }

    /**
     * 是否已设置过 PIN 密码。
     */
    suspend fun hasPin(): Boolean {
        return context.dataStore.data.map { prefs ->
            !prefs[SettingsKeys.PIN_HASH].isNullOrEmpty()
        }.first()
    }

    /**
     * 设置 PIN 密码（加盐 SHA-256 哈希存储，不保存明文）。
     */
    suspend fun setPin(pin: String) {
        val salt = generateSalt()
        val hash = hashPin(pin, salt)
        context.dataStore.edit { preferences ->
            preferences[SettingsKeys.PIN_SALT] = salt
            preferences[SettingsKeys.PIN_HASH] = hash
            preferences[SettingsKeys.PIN_LENGTH] = pin.length
        }
    }

    /**
     * 已设置的密码位数（默认 4）。
     */
    suspend fun pinLength(): Int {
        return context.dataStore.data.map { prefs ->
            prefs[SettingsKeys.PIN_LENGTH] ?: 4
        }.first()
    }

    /**
     * 验证 PIN 密码是否正确。
     */
    suspend fun verifyPin(pin: String): Boolean {
        return context.dataStore.data.map { prefs ->
            val salt = prefs[SettingsKeys.PIN_SALT] ?: return@map false
            val stored = prefs[SettingsKeys.PIN_HASH] ?: return@map false
            stored == hashPin(pin, salt)
        }.first()
    }

    /**
     * 修改密码：旧密码正确才允许设置新密码。
     * @return true=修改成功，false=旧密码错误
     */
    suspend fun changePin(oldPin: String, newPin: String): Boolean {
        if (!verifyPin(oldPin)) return false
        setPin(newPin)
        return true
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPin(pin: String, salt: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 读取当前网格列数（默认 3）。
     */
    suspend fun getGridColumns(): Int {
        return context.dataStore.data.map { prefs ->
            prefs[SettingsKeys.GRID_COLUMNS] ?: 3
        }.first()
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
            preferences[SettingsKeys.PIN_ENABLED] = state.pinEnabled
            preferences[SettingsKeys.AUTO_DELETE_ORIGINAL] = state.autoDeleteOriginal
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
