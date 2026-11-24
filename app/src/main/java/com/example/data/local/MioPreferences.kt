package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "mio_user_settings")

data class MioUserSettings(
  val hapticsEnabled: Boolean = true,
  val soundsEnabled: Boolean = true,
  val notificationsEnabled: Boolean = true,
)

interface UserSettingsStore {
  val settings: Flow<MioUserSettings>
}

class MioPreferences(private val context: Context) : UserSettingsStore {
  private val hapticsKey = booleanPreferencesKey("haptics_enabled")
  private val soundsKey = booleanPreferencesKey("sounds_enabled")
  private val notificationsKey = booleanPreferencesKey("notifications_enabled")

  override val settings: Flow<MioUserSettings> = context.settingsDataStore.data.map { prefs ->
    MioUserSettings(
      hapticsEnabled = prefs[hapticsKey] ?: true,
      soundsEnabled = prefs[soundsKey] ?: true,
      notificationsEnabled = prefs[notificationsKey] ?: true,
    )
  }

  suspend fun setHapticsEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[hapticsKey] = enabled }
  }

  suspend fun setSoundsEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[soundsKey] = enabled }
  }

  suspend fun setNotificationsEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[notificationsKey] = enabled }
  }
}
