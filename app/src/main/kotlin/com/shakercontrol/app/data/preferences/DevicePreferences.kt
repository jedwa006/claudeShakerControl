package com.shakercontrol.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
