package com.example.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.config.MioCopy
import com.example.data.local.MioUserSettings
import com.example.data.model.BlockRecord
import com.example.data.model.UserProfile
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing

enum class SettingsSectionKey(val title: String, val subtitle: String, val icon: ImageVector) {
  ACCOUNT("1. Account", "Profile, username, linked devices, deletion", Icons.Outlined.Person),
  PRIVACY("2. Privacy", "Last active, read receipts, typing, permissions", Icons.Outlined.Visibility),
  BLOCKED("3. Blocked users", "Manage blocked contacts & unblock", Icons.Outlined.Block),
  CHATS("4. Chats", "Disappearing messages, scheduled, wallpaper, backup", Icons.Outlined.ChatBubbleOutline),
  CALLS("5. Calls", "Call permissions, data saver, ringtone", Icons.Outlined.Call),
  MIP("6. MIP & Mood Cats", "Most Important Person, cat sounds & haptics", Icons.Outlined.Favorite),
  NOTIFICATIONS("7. Notifications", "Previews, call alerts, birthday alerts, mute", Icons.Outlined.NotificationsNone),
  SECURITY("8. Security", "E2EE verification, key fingerprint, app lock", Icons.Outlined.Security),
  HELP("9. Help and About", "Report problem via email, terms, policy, version", Icons.Outlined.HelpOutline),
}

@Composable
fun SettingsCategoryCard(
  section: SettingsSectionKey,
  onClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(MioRadii.lg),
    color = MioColors.CardSurface,
    border = BorderStroke(1.dp, MioColors.SubtleBorder),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("settings_cat_${section.name.lowercase()}"),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = MioSpacing.md, vertical = MioSpacing.md),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MioSpacing.md),
    ) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(MioColors.PlumBase),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = section.icon,
          contentDescription = null,
          tint = MioColors.RoseAccent,
          modifier = Modifier.size(22.dp),
        )
      }

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = section.title,
          style = MaterialTheme.typography.titleMedium,
          color = MioColors.PrimaryText,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = section.subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MioColors.MutedText,
        )
      }
    }
  }
}

// -------------------------------------------------------------
// 1. Account Section
// -------------------------------------------------------------
@Composable
fun AccountSectionContent(
  profile: UserProfile,
  keyFingerprint: String,
  onOpenEditProfile: () -> Unit,
  onOpenChangeUsername: () -> Unit,
  onOpenWallpaperPicker: () -> Unit,
  onDeactivateAccount: () -> Unit,
  onDeleteAccount: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.md)) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Profile Information", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        Text("Display Name: ${profile.displayName.ifBlank { "Mio User" }}", color = MioColors.PrimaryText)
        Text("Username: @${profile.username}", color = MioColors.RoseAccent, fontWeight = FontWeight.SemiBold)
        if (profile.bio.isNotBlank()) {
          Text("Bio: ${profile.bio}", color = MioColors.MutedText)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
          Button(
            onClick = onOpenEditProfile,
            colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
            shape = RoundedCornerShape(MioRadii.pill),
            modifier = Modifier.testTag("account_edit_profile_button"),
          ) {
            Text("Edit profile", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
          }

          OutlinedButton(
            onClick = onOpenChangeUsername,
            border = BorderStroke(1.dp, MioColors.SubtleBorder),
            shape = RoundedCornerShape(MioRadii.pill),
            modifier = Modifier.testTag("account_change_username_button"),
          ) {
            Text("Change username", color = MioColors.PrimaryText)
          }
        }
      }
    }

    // Linked Devices
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
          Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = MioColors.TwilightBlue)
          Text("Linked Devices", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        }
        Text("• 1 Active Secure Device (This Android Phone)", color = MioColors.PrimaryText, style = MaterialTheme.typography.bodyMedium)
        Text("• End-to-End Encryption Key Active (NaCl Box / ECDH)", color = MioColors.MutedText, style = MaterialTheme.typography.labelMedium)
        Text("Key ID: ${keyFingerprint.take(16)}...", color = MioColors.TwilightBlue, style = MaterialTheme.typography.labelSmall)
      }
    }

    // Deactivate & Delete
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Account Management", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        Text(
          "Deactivating pauses your active status and alerts. Permanent deletion releases your username and wipes all keys and messages.",
          color = MioColors.MutedText,
          style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
          OutlinedButton(
            onClick = onDeactivateAccount,
            border = BorderStroke(1.dp, MioColors.SubtleBorder),
            shape = RoundedCornerShape(MioRadii.pill),
          ) {
            Text("Deactivate account", color = MioColors.PrimaryText)
          }

          Button(
            onClick = onDeleteAccount,
            colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber),
            shape = RoundedCornerShape(MioRadii.pill),
            modifier = Modifier.testTag("account_delete_button"),
          ) {
            Text("Delete account", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// 2. Privacy Section
// -------------------------------------------------------------
@Composable
fun PrivacySectionContent(
  settings: MioUserSettings,
  restrictedCount: Int,
  blockedCount: Int,
  onToggleLastActive: (Boolean) -> Unit,
  onToggleActiveNow: (Boolean) -> Unit,
  onToggleReadReceipts: (Boolean) -> Unit,
  onToggleTypingIndicator: (Boolean) -> Unit,
  onSetWhoCanMessageMe: (String) -> Unit,
  onOpenBlockedUsers: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.md)) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Online & Activity", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        SettingsToggleItem(
          title = "Last active status",
          subtitle = "Show when you were last active to your partner",
          checked = settings.lastActiveEnabled,
          onCheckedChange = onToggleLastActive,
        )
        SettingsToggleItem(
          title = "Active now",
          subtitle = "Display glowing green presence dot when connected",
          checked = settings.activeNowEnabled && settings.showOnlineStatus,
          onCheckedChange = {
            onToggleActiveNow(it)
          },
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Messaging Privacy", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        SettingsToggleItem(
          title = "Read receipts",
          subtitle = "Let your partner know when you've opened messages",
          checked = settings.readReceiptsEnabled,
          onCheckedChange = onToggleReadReceipts,
        )
        SettingsToggleItem(
          title = "Typing indicator",
          subtitle = "Show gentle 'typing...' notice in conversation",
          checked = settings.typingIndicatorEnabled,
          onCheckedChange = onToggleTypingIndicator,
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
        Text("Who can message me", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        Row(verticalAlignment = Alignment.CenterVertically) {
          RadioButton(
            selected = settings.whoCanMessageMe == "everyone",
            onClick = { onSetWhoCanMessageMe("everyone") },
            colors = RadioButtonDefaults.colors(selectedColor = MioColors.RoseAccent),
          )
          Text("Everyone", color = MioColors.PrimaryText)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          RadioButton(
            selected = settings.whoCanMessageMe == "accepted",
            onClick = { onSetWhoCanMessageMe("accepted") },
            colors = RadioButtonDefaults.colors(selectedColor = MioColors.RoseAccent),
          )
          Text("Accepted contacts only", color = MioColors.PrimaryText)
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Permissions & Restrictions", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        Text("Restricted users: $restrictedCount active", color = MioColors.MutedText, style = MaterialTheme.typography.bodyMedium)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MioRadii.sm))
            .clickable(onClick = onOpenBlockedUsers)
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text("Blocked users ($blockedCount)", color = MioColors.RoseAccent, fontWeight = FontWeight.SemiBold)
          Text("Manage >", color = MioColors.MutedText)
        }
      }
    }
  }
}

// -------------------------------------------------------------
// 3. Blocked Users Section
// -------------------------------------------------------------
@Composable
fun BlockedUsersSectionContent(
  blockedUsers: List<BlockRecord>,
  onUnblockClick: (BlockRecord) -> Unit,
) {
  var searchQuery by rememberSaveable { mutableStateOf("") }
  val filtered = remember(blockedUsers, searchQuery) {
    if (searchQuery.isBlank()) blockedUsers
    else blockedUsers.filter { it.blockedUsername.contains(searchQuery.trim().lowercase(), ignoreCase = true) }
  }

  Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.md)) {
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search blocked users...", color = MioColors.MutedText) },
      leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = MioColors.MutedText) },
      trailingIcon = {
        if (searchQuery.isNotBlank()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(Icons.Outlined.Close, contentDescription = "Clear", tint = MioColors.MutedText)
          }
        }
      },
      singleLine = true,
      colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MioColors.RoseAccent,
        unfocusedBorderColor = MioColors.SubtleBorder,
        focusedTextColor = MioColors.PrimaryText,
        unfocusedTextColor = MioColors.PrimaryText,
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("blocked_users_search_bar"),
    )

    if (filtered.isEmpty()) {
      Surface(
        shape = RoundedCornerShape(MioRadii.lg),
        color = MioColors.CardSurface,
        border = BorderStroke(1.dp, MioColors.SubtleBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(
          modifier = Modifier.padding(MioSpacing.xl),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(MioSpacing.xs),
        ) {
          Icon(Icons.Outlined.Block, contentDescription = null, tint = MioColors.MutedText, modifier = Modifier.size(32.dp))
          Text(
            text = if (searchQuery.isBlank()) "No blocked users" else "No matching blocked users",
            color = MioColors.PrimaryText,
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Users you block won't be able to send you messages or MIP letters.",
            color = MioColors.MutedText,
            style = MaterialTheme.typography.bodySmall,
          )
        }
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
        filtered.forEach { record ->
          Surface(
            shape = RoundedCornerShape(MioRadii.md),
            color = MioColors.CardSurface,
            border = BorderStroke(1.dp, MioColors.SubtleBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MioSpacing.md, vertical = MioSpacing.sm),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
              ) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MioColors.PlumBase),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    text = record.blockedUsername.take(1).uppercase(),
                    color = MioColors.RoseAccent,
                    fontWeight = FontWeight.Bold,
                  )
                }
                Column {
                  Text(
                    text = "@${record.blockedUsername}",
                    color = MioColors.PrimaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                  )
                  Text("Blocked contact", color = MioColors.MutedText, style = MaterialTheme.typography.labelSmall)
                }
              }

              Button(
                onClick = { onUnblockClick(record) },
                colors = ButtonDefaults.buttonColors(containerColor = MioColors.BurgundySurface),
                shape = RoundedCornerShape(MioRadii.pill),
                modifier = Modifier.testTag("unblock_btn_${record.blockedUsername}"),
              ) {
                Text("Unblock", color = MioColors.RoseAccent, style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// 4. Chats Section
// -------------------------------------------------------------
@Composable
fun ChatsSectionContent(
  settings: MioUserSettings,
  onSetDisappearingTimer: (String) -> Unit,
  onToggleScheduledMessages: (Boolean) -> Unit,
  onSetMediaAutoDownload: (String) -> Unit,
  onOpenWallpaperPicker: () -> Unit,
  onRemoveWallpaper: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.md)) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
        Text("Disappearing Messages (Default Timer)", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        val timers = listOf("off" to "Off", "24h" to "24 Hours", "7d" to "7 Days", "30d" to "30 Days")
        timers.forEach { (code, label) ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
              selected = settings.disappearingTimer == code,
              onClick = { onSetDisappearingTimer(code) },
              colors = RadioButtonDefaults.colors(selectedColor = MioColors.RoseAccent),
            )
            Text(label, color = MioColors.PrimaryText)
          }
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Scheduled Messages", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        SettingsToggleItem(
          title = "Enable romantic message scheduling",
          subtitle = "Compose letters or reminders to be sent automatically at a future moment",
          checked = settings.scheduledMessagesEnabled,
          onCheckedChange = onToggleScheduledMessages,
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Chats Screen Wallpaper", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        Text("Set a local wallpaper photo for your chat list header. Stored locally on your device only for privacy.", color = MioColors.MutedText, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
          Button(
            onClick = onOpenWallpaperPicker,
            colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
            shape = RoundedCornerShape(MioRadii.pill),
          ) {
            Text("Choose wallpaper", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
          }
          if (!settings.chatWallpaperPath.isNullOrBlank()) {
            OutlinedButton(
              onClick = onRemoveWallpaper,
              border = BorderStroke(1.dp, MioColors.SubtleBorder),
              shape = RoundedCornerShape(MioRadii.pill),
            ) {
              Text("Remove", color = MioColors.DangerEmber)
            }
          }
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
        Text("Media Auto-Download", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        listOf("wifi_and_cellular" to "Wi-Fi and Cellular", "wifi_only" to "Wi-Fi Only", "never" to "Never").forEach { (code, label) ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
              selected = settings.mediaAutoDownload == code,
              onClick = { onSetMediaAutoDownload(code) },
              colors = RadioButtonDefaults.colors(selectedColor = MioColors.RoseAccent),
            )
            Text(label, color = MioColors.PrimaryText)
          }
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
        Text("Chat Backup", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        Text(
          "Mio follows zero-cloud key architecture. Your conversation history is stored strictly on this device's encrypted internal storage.",
          color = MioColors.MutedText,
          style = MaterialTheme.typography.bodySmall,
        )
      }
    }
  }
}

// -------------------------------------------------------------
// 5. Calls Section
// -------------------------------------------------------------
@Composable
fun CallsSectionContent(
  settings: MioUserSettings,
  onSetWhoCanCallMe: (String) -> Unit,
  onToggleDataSaver: (Boolean) -> Unit,
  onSetCallRingtone: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.md)) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
        Text("Who can call me", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        listOf("accepted" to "Accepted contacts only", "mip_only" to "MIP Partner only", "nobody" to "Nobody").forEach { (code, label) ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
              selected = settings.whoCanCallMe == code,
              onClick = { onSetWhoCanCallMe(code) },
              colors = RadioButtonDefaults.colors(selectedColor = MioColors.RoseAccent),
            )
            Text(label, color = MioColors.PrimaryText)
          }
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Call Quality & Network", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        SettingsToggleItem(
          title = "Data saver mode for calls",
          subtitle = "Adapts WebRTC peer stream to reduce cellular data usage",
          checked = settings.callDataSaverEnabled,
          onCheckedChange = onToggleDataSaver,
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
        Text("Ringtone", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        listOf(
          "romantic_chime" to "Mio Romantic Chime (Default)",
          "gentle_melody" to "Gentle Melody",
          "soft_purr" to "Soft Cat Purr",
          "system_default" to "System Default",
        ).forEach { (code, label) ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
              selected = settings.callRingtone == code,
              onClick = { onSetCallRingtone(code) },
              colors = RadioButtonDefaults.colors(selectedColor = MioColors.RoseAccent),
            )
            Text(label, color = MioColors.PrimaryText)
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// 6. MIP Section
// -------------------------------------------------------------
@Composable
fun MipSectionContent(
  settings: MioUserSettings,
  activeMipPartnerName: String?,
  onUnlinkMip: () -> Unit,
  onToggleNotifSounds: (Boolean) -> Unit,
  onToggleRingtone: (Boolean) -> Unit,
  onToggleHelloTune: (Boolean) -> Unit,
) {
  var showUnlinkConfirm by rememberSaveable { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.md)) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Current MIP (Most Important Person)", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        if (activeMipPartnerName.isNullOrBlank()) {
          Text("No MIP sealed yet. Open a chat and tap the wax seal to dedicate your seal.", color = MioColors.MutedText)
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
              Icon(Icons.Outlined.Favorite, contentDescription = null, tint = MioColors.RoseAccent)
              Text("Sealed with $activeMipPartnerName", color = MioColors.PrimaryText, fontWeight = FontWeight.Bold)
            }
            Button(
              onClick = { showUnlinkConfirm = true },
              colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber.copy(alpha = 0.2f)),
              shape = RoundedCornerShape(MioRadii.pill),
            ) {
              Text("Unlink MIP", color = MioColors.DangerEmber)
            }
          }
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Cat Sounds & Haptics", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        SettingsToggleItem(
          title = "Notification cat sounds",
          subtitle = "Play gentle meows and chimes when moods arrive",
          checked = settings.mipNotificationSounds,
          onCheckedChange = onToggleNotifSounds,
        )
        SettingsToggleItem(
          title = "Cat call ringtone",
          subtitle = "Play romantic cat tune during incoming calls",
          checked = settings.mipRingtoneSounds,
          onCheckedChange = onToggleRingtone,
        )
        SettingsToggleItem(
          title = "Hello tune & Mood tactile haptics",
          subtitle = "Vibrate with distinct feline pulse patterns",
          checked = settings.mipHelloTune,
          onCheckedChange = onToggleHelloTune,
        )
      }
    }
  }

  if (showUnlinkConfirm) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { showUnlinkConfirm = false },
      containerColor = MioColors.CardSurface,
      title = { Text("Unlink MIP?", color = MioColors.DangerEmber) },
      text = {
        Text(
          "Unlink Most Important Person? Note: A 24-hour quiet cooldown applies before you can seal a new MIP.",
          color = MioColors.PrimaryText,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showUnlinkConfirm = false
            onUnlinkMip()
          },
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber),
        ) {
          Text("Unlink", color = MioColors.PureBlack)
        }
      },
      dismissButton = {
        TextButton(onClick = { showUnlinkConfirm = false }) {
          Text("Cancel", color = MioColors.MutedText)
        }
      },
    )
  }
}

// -------------------------------------------------------------
// 7. Notifications Section
// -------------------------------------------------------------
@Composable
fun NotificationsSectionContent(
  settings: MioUserSettings,
  onToggleMessageNotifications: (Boolean) -> Unit,
  onToggleMessagePreview: (Boolean) -> Unit,
  onToggleCallNotifications: (Boolean) -> Unit,
  onToggleBirthdayAlerts: (Boolean) -> Unit,
  onOpenMuteDurationDialog: () -> Unit,
) {
  val isMuted = settings.muteAllUntilMs > System.currentTimeMillis()

  Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.md)) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Message & Call Alerts", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        SettingsToggleItem(
          title = "Message notifications",
          subtitle = "Receive notifications when new messages arrive",
          checked = settings.notificationsEnabled,
          onCheckedChange = onToggleMessageNotifications,
        )
        SettingsToggleItem(
          title = "Message preview",
          subtitle = "Show sender name and message body in notification",
          checked = settings.messagePreviewEnabled,
          onCheckedChange = onToggleMessagePreview,
        )
        SettingsToggleItem(
          title = "Call notifications",
          subtitle = "Alert on incoming voice and video calls",
          checked = settings.callNotificationsEnabled,
          onCheckedChange = onToggleCallNotifications,
        )
        SettingsToggleItem(
          title = "Birthday & Anniversary alerts",
          subtitle = "Celebrate special couple milestones",
          checked = settings.birthdayAlertsEnabled,
          onCheckedChange = onToggleBirthdayAlerts,
        )
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(MioSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column {
          Text("Mute All Notifications", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
          Text(
            if (isMuted) "Currently muted" else "Active (Alerts enabled)",
            color = if (isMuted) MioColors.DangerEmber else MioColors.RoseAccent,
            style = MaterialTheme.typography.labelMedium,
          )
        }
        Button(
          onClick = onOpenMuteDurationDialog,
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.BurgundySurface),
          shape = RoundedCornerShape(MioRadii.pill),
        ) {
          Text("Set Duration", color = MioColors.PrimaryText)
        }
      }
    }

    BackgroundDeliveryStatusCard()
  }
}

@Composable
private fun BackgroundDeliveryStatusCard() {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  fun checkBatteryUnrestricted(): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
  }

  fun checkFullScreenAllowed(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return true
    return nm.canUseFullScreenIntent()
  }

  var batteryUnrestricted by remember { mutableStateOf(checkBatteryUnrestricted()) }
  var fullScreenAllowed by remember { mutableStateOf(checkFullScreenAllowed()) }

  androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        batteryUnrestricted = checkBatteryUnrestricted()
        fullScreenAllowed = checkFullScreenAllowed()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  Surface(
    shape = RoundedCornerShape(MioRadii.lg),
    color = MioColors.CardSurface,
    border = BorderStroke(1.dp, MioColors.SubtleBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.padding(MioSpacing.md),
      verticalArrangement = Arrangement.spacedBy(MioSpacing.sm),
    ) {
      Text(
        text = MioCopy.NOTIF_RELIABILITY_TITLE,
        style = MaterialTheme.typography.titleMedium,
        color = MioColors.PrimaryText,
      )
      Text(
        text = MioCopy.NOTIF_RELIABILITY_SUBTITLE,
        style = MaterialTheme.typography.bodySmall,
        color = MioColors.MutedText,
      )

      Text(
        text = if (batteryUnrestricted) MioCopy.NOTIF_BATTERY_UNRESTRICTED else MioCopy.NOTIF_BATTERY_OPTIMIZED,
        style = MaterialTheme.typography.labelMedium,
        color = if (batteryUnrestricted) MioColors.OnlineGreen else MioColors.DangerEmber,
      )
      if (!batteryUnrestricted) {
        OutlinedButton(
          onClick = {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            runCatching { context.startActivity(intent) }
          },
          border = BorderStroke(1.dp, MioColors.RoseAccent.copy(alpha = 0.6f)),
          shape = RoundedCornerShape(MioRadii.pill),
          modifier = Modifier.testTag("notif_battery_settings_btn"),
        ) {
          Text(MioCopy.NOTIF_BATTERY_FIX_CTA, color = MioColors.RoseAccent)
        }
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Text(
          text = if (fullScreenAllowed) MioCopy.NOTIF_FULLSCREEN_GRANTED else MioCopy.NOTIF_FULLSCREEN_DENIED,
          style = MaterialTheme.typography.labelMedium,
          color = if (fullScreenAllowed) MioColors.OnlineGreen else MioColors.DangerEmber,
        )
        if (!fullScreenAllowed) {
          OutlinedButton(
            onClick = {
              val intent = Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}"),
              )
              runCatching { context.startActivity(intent) }
            },
            border = BorderStroke(1.dp, MioColors.RoseAccent.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(MioRadii.pill),
            modifier = Modifier.testTag("notif_fullscreen_settings_btn"),
          ) {
            Text(MioCopy.NOTIF_FULLSCREEN_FIX_CTA, color = MioColors.RoseAccent)
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// 8. Security Section
// -------------------------------------------------------------
@Composable
fun SecuritySectionContent(
  keyFingerprint: String,
  settings: MioUserSettings,
  onOpenAppLockDialog: () -> Unit,
  onToggleAppLock: (Boolean) -> Unit,
) {
  val clipboardManager = LocalClipboardManager.current
  var copiedNotice by rememberSaveable { mutableStateOf(false) }

  Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.md)) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
          Icon(Icons.Outlined.Key, contentDescription = null, tint = MioColors.TwilightBlue)
          Text("End-to-End Encryption", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        }
        Text(
          "Your keys stay on this device. Message history lives locally on your phone (no cloud key backup yet). Mio uses TweetNaCl Box (ECDH + AES-GCM 256).",
          color = MioColors.MutedText,
          style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        Text("Device Key Fingerprint:", color = MioColors.PrimaryText, style = MaterialTheme.typography.labelMedium)
        Text(keyFingerprint, color = MioColors.TwilightBlue, style = MaterialTheme.typography.labelSmall)

        Button(
          onClick = {
            clipboardManager.setText(AnnotatedString(keyFingerprint))
            copiedNotice = true
          },
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.BurgundySurface),
          shape = RoundedCornerShape(MioRadii.pill),
        ) {
          Text(if (copiedNotice) "Copied!" else "Copy Key Fingerprint", color = MioColors.PrimaryText)
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("App Lock", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        Text(
          "Require PIN or biometric verification every time Mio opens.",
          color = MioColors.MutedText,
          style = MaterialTheme.typography.bodySmall,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            if (settings.appLockEnabled) "App Lock Enabled (${settings.appLockType.uppercase()})" else "App Lock Disabled",
            color = if (settings.appLockEnabled) MioColors.RoseAccent else MioColors.MutedText,
            style = MaterialTheme.typography.labelMedium,
          )
          Button(
            onClick = onOpenAppLockDialog,
            colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
            shape = RoundedCornerShape(MioRadii.pill),
          ) {
            Text("Configure", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// 9. Help and About Section
// -------------------------------------------------------------
@Composable
fun HelpAboutSectionContent(
  currentUsername: String,
  onOpenPrivacy: () -> Unit,
  onOpenTerms: () -> Unit,
) {
  val context = LocalContext.current

  Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.md)) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text("Help & Support", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        Text(
          "Have questions or need technical support? Send a report directly to the engineering team.",
          color = MioColors.MutedText,
          style = MaterialTheme.typography.bodySmall,
        )

        Button(
          onClick = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
              data = Uri.parse("mailto:abhijayyadav37@gmail.com")
              putExtra(Intent.EXTRA_SUBJECT, "[Mio Support] Report a Problem - @$currentUsername")
              putExtra(
                Intent.EXTRA_TEXT,
                "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})\nApp Version: v1.0.0 (Phase 1 MVP)\nUsername: @$currentUsername\n\nPlease describe the issue you encountered:\n",
              )
            }
            runCatching { context.startActivity(intent) }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
          shape = RoundedCornerShape(MioRadii.pill),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("report_problem_email_btn"),
        ) {
          Text("Report a problem (Email Support)", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
        Text("Legal & Documents", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MioRadii.sm))
            .clickable(onClick = onOpenPrivacy)
            .padding(vertical = MioSpacing.xs),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
        ) {
          Icon(Icons.Outlined.Description, contentDescription = null, tint = MioColors.PrimaryText)
          Text("Privacy Policy (Draft for legal review)", color = MioColors.PrimaryText, modifier = Modifier.weight(1f))
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(MioRadii.sm))
            .clickable(onClick = onOpenTerms)
            .padding(vertical = MioSpacing.xs),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
        ) {
          Icon(Icons.Outlined.Description, contentDescription = null, tint = MioColors.PrimaryText)
          Text("Terms of Service (Draft for legal review)", color = MioColors.PrimaryText, modifier = Modifier.weight(1f))
        }
      }
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("App Version", style = MaterialTheme.typography.titleMedium, color = MioColors.PrimaryText)
        Text("Mio v1.0.0 (Phase 1 MVP - Couples E2EE Chat)", color = MioColors.RoseAccent, style = MaterialTheme.typography.bodyMedium)
        Text("Built with Kotlin & Jetpack Compose", color = MioColors.MutedText, style = MaterialTheme.typography.labelSmall)
      }
    }
  }
}

// -------------------------------------------------------------
// Common Toggle Row
// -------------------------------------------------------------
@Composable
fun SettingsToggleItem(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MioSpacing.minTouchTarget),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, color = MioColors.PrimaryText)
      Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MioColors.MutedText)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = MioColors.PureBlack,
        checkedTrackColor = MioColors.RoseAccent,
        uncheckedThumbColor = MioColors.MutedText,
        uncheckedTrackColor = MioColors.CardSurface,
      ),
    )
  }
}
