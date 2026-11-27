package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

private val Context.settingsDataStore by preferencesDataStore(name = "mio_user_settings")

data class MioUserSettings(
  val hapticsEnabled: Boolean = true,
  val soundsEnabled: Boolean = true,
  val notificationsEnabled: Boolean = true,
  val showOnlineStatus: Boolean = true,
  val chatWallpaperPath: String? = null,
  val keepSignedIn: Boolean = true,
  // Account
  val bio: String = "",
  val profilePhotoPath: String? = null,
  // Privacy
  val lastActiveEnabled: Boolean = true,
  val activeNowEnabled: Boolean = true,
  val readReceiptsEnabled: Boolean = true,
  val typingIndicatorEnabled: Boolean = true,
  val whoCanMessageMe: String = "accepted", // "everyone" or "accepted"
  val restrictedUsernames: Set<String> = emptySet(),
  // Chats
  val disappearingTimer: String = "off", // "off", "24h", "7d", "30d"
  val scheduledMessagesEnabled: Boolean = true,
  val mediaAutoDownload: String = "wifi_and_cellular", // "wifi_and_cellular", "wifi_only", "never"
  // Calls
  val whoCanCallMe: String = "accepted", // "accepted", "nobody", "mip_only"
  val callDataSaverEnabled: Boolean = false,
  val callRingtone: String = "romantic_chime", // "romantic_chime", "gentle_melody", "soft_purr", "system_default"
  // MIP & Cats
  val mipNotificationSounds: Boolean = true,
  val mipRingtoneSounds: Boolean = true,
  val mipHelloTune: Boolean = true,
  // Notifications
  val messagePreviewEnabled: Boolean = true,
  val callNotificationsEnabled: Boolean = true,
  val birthdayAlertsEnabled: Boolean = true,
  val muteAllUntilMs: Long = 0L,
  // Security
  val appLockEnabled: Boolean = false,
  val appLockType: String = "biometric", // "biometric" or "pin"
  val appLockPin: String = "",
)

interface UserSettingsStore {
  val settings: Flow<MioUserSettings>
  fun observeClearedChatTimestamps(uid: String): Flow<Map<String, Long>> = flowOf(emptyMap())
  suspend fun setChatClearedAt(uid: String, chatId: String, timestampMs: Long) {}
}

class MioPreferences(private val context: Context) : UserSettingsStore {
  private val hapticsKey = booleanPreferencesKey("haptics_enabled")
  private val soundsKey = booleanPreferencesKey("sounds_enabled")
  private val notificationsKey = booleanPreferencesKey("notifications_enabled")
  private val showOnlineStatusKey = booleanPreferencesKey("show_online_status")
  private val chatWallpaperPathKey = stringPreferencesKey("chat_wallpaper_path")
  private val keepSignedInKey = booleanPreferencesKey("keep_signed_in")

  private val bioKey = stringPreferencesKey("user_bio")
  private val profilePhotoPathKey = stringPreferencesKey("profile_photo_path")
  private val lastActiveKey = booleanPreferencesKey("privacy_last_active")
  private val activeNowKey = booleanPreferencesKey("privacy_active_now")
  private val readReceiptsKey = booleanPreferencesKey("privacy_read_receipts")
  private val typingIndicatorKey = booleanPreferencesKey("privacy_typing_indicator")
  private val whoCanMessageMeKey = stringPreferencesKey("privacy_who_can_message")
  private val restrictedUsernamesKey = stringSetPreferencesKey("privacy_restricted_usernames")

  private val disappearingTimerKey = stringPreferencesKey("chats_disappearing_timer")
  private val scheduledMessagesKey = booleanPreferencesKey("chats_scheduled_messages")
  private val mediaAutoDownloadKey = stringPreferencesKey("chats_media_auto_download")

  private val whoCanCallMeKey = stringPreferencesKey("calls_who_can_call")
  private val callDataSaverKey = booleanPreferencesKey("calls_data_saver")
  private val callRingtoneKey = stringPreferencesKey("calls_ringtone")

  private val mipNotifSoundsKey = booleanPreferencesKey("mip_notif_sounds")
  private val mipRingtoneKey = booleanPreferencesKey("mip_ringtone_sounds")
  private val mipHelloTuneKey = booleanPreferencesKey("mip_hello_tune")

  private val messagePreviewKey = booleanPreferencesKey("notif_message_preview")
  private val callNotifsKey = booleanPreferencesKey("notif_call_alerts")
  private val birthdayAlertsKey = booleanPreferencesKey("notif_birthday_alerts")
  private val muteAllUntilKey = longPreferencesKey("notif_mute_all_until")

  private val appLockEnabledKey = booleanPreferencesKey("sec_app_lock_enabled")
  private val appLockTypeKey = stringPreferencesKey("sec_app_lock_type")
  private val appLockPinKey = stringPreferencesKey("sec_app_lock_pin")
  private val pendingFcmTokenKey = stringPreferencesKey("pending_fcm_token")

  private fun clearedChatsKey(uid: String) = stringPreferencesKey("chat_cleared_map_$uid")

  override val settings: Flow<MioUserSettings> = context.settingsDataStore.data.map { prefs ->
    MioUserSettings(
      hapticsEnabled = prefs[hapticsKey] ?: true,
      soundsEnabled = prefs[soundsKey] ?: true,
      notificationsEnabled = prefs[notificationsKey] ?: true,
      showOnlineStatus = prefs[showOnlineStatusKey] ?: true,
      chatWallpaperPath = prefs[chatWallpaperPathKey],
      keepSignedIn = prefs[keepSignedInKey] ?: true,
      bio = prefs[bioKey] ?: "",
      profilePhotoPath = prefs[profilePhotoPathKey],
      lastActiveEnabled = prefs[lastActiveKey] ?: true,
      activeNowEnabled = prefs[activeNowKey] ?: true,
      readReceiptsEnabled = prefs[readReceiptsKey] ?: true,
      typingIndicatorEnabled = prefs[typingIndicatorKey] ?: true,
      whoCanMessageMe = prefs[whoCanMessageMeKey] ?: "accepted",
      restrictedUsernames = prefs[restrictedUsernamesKey] ?: emptySet(),
      disappearingTimer = prefs[disappearingTimerKey] ?: "off",
      scheduledMessagesEnabled = prefs[scheduledMessagesKey] ?: true,
      mediaAutoDownload = prefs[mediaAutoDownloadKey] ?: "wifi_and_cellular",
      whoCanCallMe = prefs[whoCanCallMeKey] ?: "accepted",
      callDataSaverEnabled = prefs[callDataSaverKey] ?: false,
      callRingtone = prefs[callRingtoneKey] ?: "romantic_chime",
      mipNotificationSounds = prefs[mipNotifSoundsKey] ?: true,
      mipRingtoneSounds = prefs[mipRingtoneKey] ?: true,
      mipHelloTune = prefs[mipHelloTuneKey] ?: true,
      messagePreviewEnabled = prefs[messagePreviewKey] ?: true,
      callNotificationsEnabled = prefs[callNotifsKey] ?: true,
      birthdayAlertsEnabled = prefs[birthdayAlertsKey] ?: true,
      muteAllUntilMs = prefs[muteAllUntilKey] ?: 0L,
      appLockEnabled = prefs[appLockEnabledKey] ?: false,
      appLockType = prefs[appLockTypeKey] ?: "biometric",
      appLockPin = prefs[appLockPinKey] ?: "",
    )
  }

  override fun observeClearedChatTimestamps(uid: String): Flow<Map<String, Long>> {
    if (uid.isBlank()) return flowOf(emptyMap())
    val key = clearedChatsKey(uid)
    return context.settingsDataStore.data.map { prefs ->
      val raw = prefs[key] ?: return@map emptyMap()
      try {
        val obj = JSONObject(raw)
        val result = mutableMapOf<String, Long>()
        val keys = obj.keys()
        while (keys.hasNext()) {
          val k = keys.next()
          val v = obj.optLong(k, 0L)
          if (v > 0L) result[k] = v
        }
        result
      } catch (_: Exception) {
        emptyMap()
      }
    }
  }

  override suspend fun setChatClearedAt(uid: String, chatId: String, timestampMs: Long) {
    if (uid.isBlank() || chatId.isBlank()) return
    val key = clearedChatsKey(uid)
    context.settingsDataStore.edit { prefs ->
      val obj = try {
        prefs[key]?.let { JSONObject(it) } ?: JSONObject()
      } catch (_: Exception) {
        JSONObject()
      }
      if (timestampMs <= 0L) {
        obj.remove(chatId)
      } else {
        obj.put(chatId, timestampMs)
      }
      if (obj.length() == 0) {
        prefs.remove(key)
      } else {
        prefs[key] = obj.toString()
      }
    }
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

  suspend fun setShowOnlineStatus(enabled: Boolean) {
    context.settingsDataStore.edit { it[showOnlineStatusKey] = enabled }
  }

  suspend fun setChatWallpaperPath(path: String?) {
    context.settingsDataStore.edit { prefs ->
      if (path.isNullOrBlank()) {
        prefs.remove(chatWallpaperPathKey)
      } else {
        prefs[chatWallpaperPathKey] = path
      }
    }
  }

  suspend fun setKeepSignedIn(enabled: Boolean) {
    context.settingsDataStore.edit { it[keepSignedInKey] = enabled }
  }

  suspend fun setBio(bio: String) {
    context.settingsDataStore.edit { it[bioKey] = bio }
  }

  suspend fun setProfilePhotoPath(path: String?) {
    context.settingsDataStore.edit { prefs ->
      if (path.isNullOrBlank()) prefs.remove(profilePhotoPathKey) else prefs[profilePhotoPathKey] = path
    }
  }

  suspend fun setLastActiveEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[lastActiveKey] = enabled }
  }

  suspend fun setActiveNowEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[activeNowKey] = enabled }
  }

  suspend fun setReadReceiptsEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[readReceiptsKey] = enabled }
  }

  suspend fun setTypingIndicatorEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[typingIndicatorKey] = enabled }
  }

  suspend fun setWhoCanMessageMe(value: String) {
    context.settingsDataStore.edit { it[whoCanMessageMeKey] = value }
  }

  suspend fun setRestrictedUsernames(set: Set<String>) {
    context.settingsDataStore.edit { it[restrictedUsernamesKey] = set }
  }

  suspend fun toggleRestrictedUsername(username: String) {
    context.settingsDataStore.edit { prefs ->
      val current = prefs[restrictedUsernamesKey] ?: emptySet()
      val updated = if (username in current) current - username else current + username
      prefs[restrictedUsernamesKey] = updated
    }
  }

  suspend fun setDisappearingTimer(timer: String) {
    context.settingsDataStore.edit { it[disappearingTimerKey] = timer }
  }

  suspend fun setScheduledMessagesEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[scheduledMessagesKey] = enabled }
  }

  suspend fun setMediaAutoDownload(mode: String) {
    context.settingsDataStore.edit { it[mediaAutoDownloadKey] = mode }
  }

  suspend fun setWhoCanCallMe(mode: String) {
    context.settingsDataStore.edit { it[whoCanCallMeKey] = mode }
  }

  suspend fun setCallDataSaverEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[callDataSaverKey] = enabled }
  }

  suspend fun setCallRingtone(ringtone: String) {
    context.settingsDataStore.edit { it[callRingtoneKey] = ringtone }
  }

  suspend fun setMipNotificationSounds(enabled: Boolean) {
    context.settingsDataStore.edit { it[mipNotifSoundsKey] = enabled }
  }

  suspend fun setMipRingtoneSounds(enabled: Boolean) {
    context.settingsDataStore.edit { it[mipRingtoneKey] = enabled }
  }

  suspend fun setMipHelloTune(enabled: Boolean) {
    context.settingsDataStore.edit { it[mipHelloTuneKey] = enabled }
  }

  suspend fun setMessagePreviewEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[messagePreviewKey] = enabled }
  }

  suspend fun setCallNotificationsEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[callNotifsKey] = enabled }
  }

  suspend fun setBirthdayAlertsEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[birthdayAlertsKey] = enabled }
  }

  suspend fun setMuteAllUntilMs(timeMs: Long) {
    context.settingsDataStore.edit { it[muteAllUntilKey] = timeMs }
  }

  suspend fun setAppLockEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { it[appLockEnabledKey] = enabled }
  }

  suspend fun setAppLockType(type: String) {
    context.settingsDataStore.edit { it[appLockTypeKey] = type }
  }

  suspend fun setAppLockPin(pin: String) {
    context.settingsDataStore.edit { it[appLockPinKey] = pin }
  }

  val pendingFcmToken: Flow<String?> = context.settingsDataStore.data.map { prefs ->
    prefs[pendingFcmTokenKey]?.takeIf { it.isNotBlank() }
  }

  suspend fun setPendingFcmToken(token: String?) {
    context.settingsDataStore.edit { prefs ->
      if (token.isNullOrBlank()) prefs.remove(pendingFcmTokenKey) else prefs[pendingFcmTokenKey] = token
    }
  }
}
