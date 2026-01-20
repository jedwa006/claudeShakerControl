package com.shakercontrol.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_preferences")

data class LastConnectedDevice(
    val address: String,
    val name: String
)

@Singleton
class DevicePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        val AUTO_RECONNECT_ENABLED = booleanPreferencesKey("auto_reconnect_enabled")
        val LAZY_POLLING_ENABLED = booleanPreferencesKey("lazy_polling_enabled")
        val LAZY_POLLING_IDLE_TIMEOUT_MINUTES = intPreferencesKey("lazy_polling_idle_timeout_minutes")
    }

    companion object {
        /** Available idle timeout options in minutes */
        val IDLE_TIMEOUT_OPTIONS = listOf(1, 2, 3, 5, 10, 15, 30, 60)
        /** Default idle timeout in minutes */
        const val DEFAULT_IDLE_TIMEOUT_MINUTES = 3
    }

    val lastConnectedDevice: Flow<LastConnectedDevice?> = context.dataStore.data.map { prefs ->
        val address = prefs[Keys.LAST_DEVICE_ADDRESS]
        val name = prefs[Keys.LAST_DEVICE_NAME]
        if (address != null && name != null) {
            LastConnectedDevice(address, name)
        } else {
            null
        }
    }

    val autoReconnectEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.AUTO_RECONNECT_ENABLED] ?: true
    }

    suspend fun saveLastConnectedDevice(address: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_DEVICE_ADDRESS] = address
            prefs[Keys.LAST_DEVICE_NAME] = name
        }
    }

    suspend fun clearLastConnectedDevice() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.LAST_DEVICE_ADDRESS)
            prefs.remove(Keys.LAST_DEVICE_NAME)
        }
    }

    suspend fun setAutoReconnectEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_RECONNECT_ENABLED] = enabled
        }
    }

    // ==========================================
    // Lazy Polling Settings
    // ==========================================

    /**
     * Whether lazy polling is enabled.
     * When enabled, firmware reduces PID polling frequency when idle.
     */
    val lazyPollingEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAZY_POLLING_ENABLED] ?: false
    }

    /**
     * Idle timeout before lazy polling activates (in minutes).
     */
    val lazyPollingIdleTimeoutMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAZY_POLLING_IDLE_TIMEOUT_MINUTES] ?: DEFAULT_IDLE_TIMEOUT_MINUTES
    }

    suspend fun setLazyPollingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAZY_POLLING_ENABLED] = enabled
        }
    }

    suspend fun setLazyPollingIdleTimeoutMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAZY_POLLING_IDLE_TIMEOUT_MINUTES] = minutes
        }
    }
}
