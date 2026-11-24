package com.example.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.data.local.MioUserSettings
import com.example.data.model.UserProfile
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing

@Composable
fun SettingsScreen(
  profile: UserProfile,
  keyFingerprint: String,
  settings: MioUserSettings,
  isSubmitting: Boolean,
  actionError: String?,
  contentPadding: PaddingValues,
  onUpdateDisplayName: (String) -> Unit,
  onToggleHaptics: (Boolean) -> Unit,
  onToggleSounds: (Boolean) -> Unit,
  onToggleNotifications: (Boolean) -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenTerms: () -> Unit,
  onSignOut: () -> Unit,
  onDeleteAccount: () -> Unit,
) {
  val context = LocalContext.current
  var showEditNameDialog by rememberSaveable { mutableStateOf(false) }
  var showDeleteStepOne by rememberSaveable { mutableStateOf(false) }
  var showDeleteStepTwo by rememberSaveable { mutableStateOf(false) }
  var deleteConfirmText by rememberSaveable { mutableStateOf("") }

  val notificationLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    onToggleNotifications(granted)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MioColors.PlumBase)
      .padding(bottom = contentPadding.calculateBottomPadding()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Header bar in MidnightSlate (#262F45)
    Surface(
      color = MioColors.MidnightSlate,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier
          .statusBarsPadding()
          .widthIn(max = 600.dp)
          .fillMaxWidth()
          .padding(horizontal = MioSpacing.md, vertical = MioSpacing.md),
      ) {
        Text(
          text = "Settings",
          style = MaterialTheme.typography.headlineMedium,
          color = MioColors.WarmOffWhite,
        )
      }
    }

    Column(
      modifier = Modifier
        .weight(1f)
        .widthIn(max = 600.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = MioSpacing.md, vertical = MioSpacing.md),
      verticalArrangement = Arrangement.spacedBy(MioSpacing.md),
    ) {
      if (actionError != null) {
        Surface(
          shape = RoundedCornerShape(MioRadii.md),
          color = MioColors.DangerEmber.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = actionError,
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.DangerEmber,
            modifier = Modifier.padding(MioSpacing.sm),
          )
        }
      }

      // Identity Card
      Surface(
        shape = RoundedCornerShape(MioRadii.lg),
        color = MioColors.BurgundySurface,
        border = BorderStroke(1.dp, MioColors.SubtleBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.padding(MioSpacing.md),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = profile.displayName,
              style = MaterialTheme.typography.titleLarge,
              color = MioColors.WarmOffWhite,
            )
            Text(
              text = "@${profile.username}",
              style = MaterialTheme.typography.bodyMedium,
              color = MioColors.BlushRose,
            )
          }
          IconButton(
            onClick = { showEditNameDialog = true },
            modifier = Modifier.testTag("settings_edit_name_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Edit,
              contentDescription = "Edit display name",
              tint = MioColors.WarmOffWhite,
            )
          }
        }
      }

      // Device-only E2EE Key Card
      Surface(
        shape = RoundedCornerShape(MioRadii.lg),
        color = MioColors.MidnightSlate,
        border = BorderStroke(1.dp, MioColors.SlateBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(MioSpacing.md)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
          ) {
            Icon(
              imageVector = Icons.Outlined.Key,
              contentDescription = "Device encryption key",
              tint = MioColors.TwilightBlue,
            )
            Text(
              text = "End-to-End Encryption",
              style = MaterialTheme.typography.titleMedium,
              color = MioColors.WarmOffWhite,
            )
          }
          Spacer(Modifier.height(MioSpacing.xs))
          Text(
            text = MioCopy.DEVICE_HISTORY_NOTICE,
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.MutedRose,
          )
          Spacer(Modifier.height(MioSpacing.xs))
          Text(
            text = "Device key fingerprint: $keyFingerprint",
            style = MaterialTheme.typography.labelMedium,
            color = MioColors.TwilightBlue,
          )
        }
      }

      // Preferences
      Surface(
        shape = RoundedCornerShape(MioRadii.lg),
        color = MioColors.BurgundySurface,
        border = BorderStroke(1.dp, MioColors.SubtleBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(MioSpacing.md), verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
          SettingsToggleRow(
            icon = Icons.Outlined.Vibration,
            title = "Mood Cat Haptics",
            subtitle = "Feel tactile patterns when moods arrive",
            checked = settings.hapticsEnabled,
            onCheckedChange = onToggleHaptics,
            testTag = "settings_haptics_switch",
          )
          SettingsToggleRow(
            icon = Icons.AutoMirrored.Outlined.VolumeUp,
            title = "Mood Cat Sounds",
            subtitle = "Play gentle purr & chime cues",
            checked = settings.soundsEnabled,
            onCheckedChange = onToggleSounds,
            testTag = "settings_sounds_switch",
          )
          SettingsToggleRow(
            icon = Icons.Outlined.NotificationsNone,
            title = "Message Alerts",
            subtitle = "Show local alerts without plaintext previews",
            checked = settings.notificationsEnabled,
            onCheckedChange = { enable ->
              if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                  context,
                  Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) onToggleNotifications(true)
                else notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              } else {
                onToggleNotifications(enable)
              }
            },
            testTag = "settings_notifications_switch",
          )
        }
      }

      // Legal & Trust
      Surface(
        shape = RoundedCornerShape(MioRadii.lg),
        color = MioColors.BurgundySurface,
        border = BorderStroke(1.dp, MioColors.SubtleBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column {
          SettingsNavRow(
            icon = Icons.Outlined.Shield,
            title = "Privacy Policy",
            subtitle = MioCopy.PRIVACY_DRAFT_BADGE,
            onClick = onOpenPrivacy,
            testTag = "settings_privacy_row",
          )
          SettingsNavRow(
            icon = Icons.Outlined.Description,
            title = "Terms of Service",
            subtitle = MioCopy.PRIVACY_DRAFT_BADGE,
            onClick = onOpenTerms,
            testTag = "settings_terms_row",
          )
        }
      }

      // Sign Out & Delete Account
      OutlinedButton(
        onClick = onSignOut,
        border = BorderStroke(1.dp, MioColors.SubtleBorder),
        shape = RoundedCornerShape(MioRadii.pill),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("settings_sign_out_button"),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Outlined.Logout,
          contentDescription = "Sign out",
          tint = MioColors.WarmOffWhite,
        )
        Spacer(Modifier.width(MioSpacing.xs))
        Text("Sign out", color = MioColors.WarmOffWhite)
      }

      OutlinedButton(
        onClick = { showDeleteStepOne = true },
        enabled = !isSubmitting,
        border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(MioRadii.pill),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("settings_delete_account_button"),
      ) {
        Icon(
          imageVector = Icons.Outlined.DeleteOutline,
          contentDescription = "Delete account",
          tint = MioColors.DangerEmber,
        )
        Spacer(Modifier.width(MioSpacing.xs))
        Text("Delete account", color = MioColors.DangerEmber)
      }
      Spacer(Modifier.height(MioSpacing.md))
    }
  }

  if (showEditNameDialog) {
    var draftName by rememberSaveable(profile.displayName) { mutableStateOf(profile.displayName) }
    AlertDialog(
      onDismissRequest = { showEditNameDialog = false },
      containerColor = MioColors.BurgundySurface,
      title = { Text("Update display name", color = MioColors.WarmOffWhite) },
      text = {
        OutlinedTextField(
          value = draftName,
          onValueChange = { draftName = it.take(MioConstants.MAX_DISPLAY_NAME_LENGTH) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MioColors.BlushRose,
            unfocusedBorderColor = MioColors.SubtleBorder,
            focusedTextColor = MioColors.WarmOffWhite,
            unfocusedTextColor = MioColors.WarmOffWhite,
          ),
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (draftName.trim().isNotEmpty()) {
              onUpdateDisplayName(draftName.trim())
              showEditNameDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.CrimsonPrimary),
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Save", color = MioColors.WarmOffWhite)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showEditNameDialog = false },
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Cancel", color = MioColors.MutedRose)
        }
      },
    )
  }

  if (showDeleteStepOne) {
    AlertDialog(
      onDismissRequest = { showDeleteStepOne = false },
      containerColor = MioColors.BurgundySurface,
      title = { Text("Delete your Mio account?", color = MioColors.DangerEmber) },
      text = {
        Text(
          text = MioCopy.DELETE_ACCOUNT_WARNING,
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.WarmOffWhite,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteStepOne = false
            deleteConfirmText = ""
            showDeleteStepTwo = true
          },
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber),
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = null,
            tint = MioColors.PlumBase,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(MioSpacing.xxs))
          Text("Continue", color = MioColors.PlumBase)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDeleteStepOne = false },
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Keep account", color = MioColors.MutedRose)
        }
      },
    )
  }

  if (showDeleteStepTwo) {
    AlertDialog(
      onDismissRequest = { showDeleteStepTwo = false },
      containerColor = MioColors.BurgundySurface,
      title = { Text("Confirm permanent deletion", color = MioColors.DangerEmber) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
          Text(
            text = "Type DELETE below to permanently erase @${profile.username} and all local history.",
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.WarmOffWhite,
          )
          OutlinedTextField(
            value = deleteConfirmText,
            onValueChange = { deleteConfirmText = it },
            placeholder = { Text("DELETE") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MioColors.DangerEmber,
              unfocusedBorderColor = MioColors.SubtleBorder,
              focusedTextColor = MioColors.WarmOffWhite,
              unfocusedTextColor = MioColors.WarmOffWhite,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("delete_account_confirm_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteStepTwo = false
            onDeleteAccount()
          },
          enabled = deleteConfirmText.trim() == "DELETE" && !isSubmitting,
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber),
          modifier = Modifier
            .heightIn(min = MioSpacing.minTouchTarget)
            .testTag("delete_account_final_confirm_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = "Delete Forever",
            tint = MioColors.PlumBase,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(MioSpacing.xxs))
          Text("Delete Forever", color = MioColors.PlumBase)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDeleteStepTwo = false },
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Cancel", color = MioColors.MutedRose)
        }
      },
    )
  }
}

@Composable
private fun SettingsToggleRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MioSpacing.minTouchTarget),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
  ) {
    Icon(imageVector = icon, contentDescription = title, tint = MioColors.BlushRose)
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.titleMedium, color = MioColors.WarmOffWhite)
      Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = MioColors.MutedRose)
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      colors = SwitchDefaults.colors(
        checkedThumbColor = MioColors.WarmOffWhite,
        checkedTrackColor = MioColors.CrimsonPrimary,
        uncheckedThumbColor = MioColors.MutedRose,
        uncheckedTrackColor = MioColors.PlumBase,
      ),
      modifier = Modifier.testTag(testTag),
    )
  }
}

@Composable
private fun SettingsNavRow(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MioSpacing.minTouchTarget)
      .clickable(onClick = onClick)
      .padding(MioSpacing.md)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
  ) {
    Icon(imageVector = icon, contentDescription = title, tint = MioColors.TwilightBlue)
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.titleMedium, color = MioColors.WarmOffWhite)
      Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = MioColors.MutedRose)
    }
  }
}
