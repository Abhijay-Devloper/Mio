package com.example.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Key
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.data.local.MioUserSettings
import com.example.data.model.BlockRecord
import com.example.data.model.UserProfile
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
  profile: UserProfile,
  keyFingerprint: String,
  settings: MioUserSettings,
  blockedUsers: List<BlockRecord> = emptyList(),
  activeMipPartnerName: String? = null,
  isSubmitting: Boolean,
  actionError: String?,
  contentPadding: PaddingValues,
  onUpdateDisplayName: (String) -> Unit,
  onUpdateProfile: (displayName: String, bio: String, photoUrl: String) -> Unit = { _, _, _ -> },
  onChangeUsername: (oldName: String, newName: String, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
  onCheckUsername: (String, (Boolean) -> Unit) -> Unit = { _, cb -> cb(true) },
  onUnblockUser: (String) -> Unit = {},
  onUnlinkMip: () -> Unit = {},
  onToggleHaptics: (Boolean) -> Unit,
  onToggleSounds: (Boolean) -> Unit,
  onToggleNotifications: (Boolean) -> Unit,
  onToggleShowOnlineStatus: (Boolean) -> Unit,
  onSetWallpaperPath: (String?) -> Unit,
  onSetBio: (String) -> Unit = {},
  onSetProfilePhotoPath: (String?) -> Unit = {},
  onSetLastActiveEnabled: (Boolean) -> Unit = {},
  onSetActiveNowEnabled: (Boolean) -> Unit = {},
  onSetReadReceiptsEnabled: (Boolean) -> Unit = {},
  onSetTypingIndicatorEnabled: (Boolean) -> Unit = {},
  onSetWhoCanMessageMe: (String) -> Unit = {},
  onToggleRestrictedUsername: (String) -> Unit = {},
  onSetDisappearingTimer: (String) -> Unit = {},
  onSetScheduledMessagesEnabled: (Boolean) -> Unit = {},
  onSetMediaAutoDownload: (String) -> Unit = {},
  onSetWhoCanCallMe: (String) -> Unit = {},
  onSetCallDataSaverEnabled: (Boolean) -> Unit = {},
  onSetCallRingtone: (String) -> Unit = {},
  onSetMipNotificationSounds: (Boolean) -> Unit = {},
  onSetMipRingtoneSounds: (Boolean) -> Unit = {},
  onSetMipHelloTune: (Boolean) -> Unit = {},
  onSetMessagePreviewEnabled: (Boolean) -> Unit = {},
  onSetCallNotificationsEnabled: (Boolean) -> Unit = {},
  onSetBirthdayAlertsEnabled: (Boolean) -> Unit = {},
  onSetMuteAllUntilMs: (Long) -> Unit = {},
  onSetAppLockEnabled: (Boolean) -> Unit = {},
  onSetAppLockType: (String) -> Unit = {},
  onSetAppLockPin: (String) -> Unit = {},
  onOpenPrivacy: () -> Unit,
  onOpenTerms: () -> Unit,
  onSignOut: () -> Unit,
  onDeleteAccount: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  // Navigation mode within settings: null = Profile page; false = Settings list; true = Subcategory
  var inSettingsMode by rememberSaveable { mutableStateOf(false) }
  var activeCategory by rememberSaveable { mutableStateOf<SettingsSectionKey?>(null) }

  // Dialog states
  var showEditProfileDialog by rememberSaveable { mutableStateOf(false) }
  var showChangeUsernameDialog by rememberSaveable { mutableStateOf(false) }
  var pendingUnblockTarget by remember { mutableStateOf<BlockRecord?>(null) }
  var showMuteDurationDialog by rememberSaveable { mutableStateOf(false) }
  var showAppLockDialog by rememberSaveable { mutableStateOf(false) }
  var showDeleteStepOne by rememberSaveable { mutableStateOf(false) }
  var showDeleteStepTwo by rememberSaveable { mutableStateOf(false) }
  var deleteConfirmText by rememberSaveable { mutableStateOf("") }
  var pendingWallpaperUri by remember { mutableStateOf<Uri?>(null) }

  val wallpaperPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia(),
  ) { uri ->
    if (uri != null) {
      pendingWallpaperUri = uri
    }
  }

  // Handle system back navigation inside Settings
  BackHandler(enabled = inSettingsMode) {
    if (activeCategory != null) {
      activeCategory = null
    } else {
      inSettingsMode = false
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MioColors.PureBlack)
      .padding(bottom = contentPadding.calculateBottomPadding()),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Top App Bar
    Surface(
      color = MioColors.PureBlack,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier
          .statusBarsPadding()
          .widthIn(max = 600.dp)
          .fillMaxWidth()
          .padding(horizontal = MioSpacing.md, vertical = MioSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        if (inSettingsMode) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
          ) {
            IconButton(
              onClick = {
                if (activeCategory != null) activeCategory = null
                else inSettingsMode = false
              },
              modifier = Modifier.testTag("settings_back_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = MioColors.PrimaryText,
              )
            }
            Text(
              text = activeCategory?.title ?: "Settings",
              style = MaterialTheme.typography.titleLarge,
              color = MioColors.PrimaryText,
              fontWeight = FontWeight.Bold,
            )
          }
        } else {
          Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineMedium,
            color = MioColors.PrimaryText,
            fontWeight = FontWeight.Bold,
          )

          // Top Right Settings Button on Profile Page with requested SVG icon
          IconButton(
            onClick = { inSettingsMode = true },
            modifier = Modifier
              .size(44.dp)
              .background(MioColors.CardSurface, CircleShape)
              .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), CircleShape)
              .testTag("profile_top_right_settings_button"),
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_settings_sliders),
              contentDescription = "Settings",
              tint = MioColors.PrimaryText,
              modifier = Modifier.size(22.dp),
            )
          }
        }
      }
    }

    if (actionError != null) {
      Surface(
        shape = RoundedCornerShape(MioRadii.md),
        color = MioColors.DangerEmber.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.5f)),
        modifier = Modifier
          .widthIn(max = 600.dp)
          .fillMaxWidth()
          .padding(horizontal = MioSpacing.md, vertical = 4.dp),
      ) {
        Text(
          text = actionError,
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.DangerEmber,
          modifier = Modifier.padding(MioSpacing.sm),
        )
      }
    }

    // Main Content: Profile Page vs Settings Hub
    AnimatedContent(
      targetState = inSettingsMode to activeCategory,
      transitionSpec = {
        slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
      },
      modifier = Modifier
        .weight(1f)
        .widthIn(max = 600.dp)
        .fillMaxWidth(),
      label = "SettingsNavAnimation",
    ) { (isSettings, currentCategory) ->
      if (!isSettings) {
        // PROFILE PAGE
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MioSpacing.md, vertical = MioSpacing.md),
          verticalArrangement = Arrangement.spacedBy(MioSpacing.md),
        ) {
          // Profile Header Card with avatar, details, edit button
          ProfileHeaderCard(
            profile = profile,
            bio = if (profile.bio.isNotBlank()) profile.bio else settings.bio,
            onEditClick = { showEditProfileDialog = true },
          )
        }
      } else if (currentCategory == null) {
        // SETTINGS 9 CATEGORIES LIST
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MioSpacing.md, vertical = MioSpacing.md),
          verticalArrangement = Arrangement.spacedBy(MioSpacing.sm),
        ) {
          SettingsSectionKey.entries.forEach { section ->
            SettingsCategoryCard(
              section = section,
              onClick = { activeCategory = section },
            )
          }

          Spacer(Modifier.height(MioSpacing.md))

          // Quick Sign Out
          Button(
            onClick = onSignOut,
            colors = ButtonDefaults.buttonColors(containerColor = MioColors.BurgundySurface),
            shape = RoundedCornerShape(MioRadii.pill),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("settings_sign_out_button"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
            ) {
              Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MioColors.PrimaryText)
              Text("Sign Out", color = MioColors.PrimaryText, fontWeight = FontWeight.Bold)
            }
          }
        }
      } else {
        // SPECIFIC CATEGORY VIEW
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MioSpacing.md, vertical = MioSpacing.md),
          verticalArrangement = Arrangement.spacedBy(MioSpacing.md),
        ) {
          when (currentCategory) {
            SettingsSectionKey.ACCOUNT -> {
              AccountSectionContent(
                profile = profile,
                keyFingerprint = keyFingerprint,
                onOpenEditProfile = { showEditProfileDialog = true },
                onOpenChangeUsername = { showChangeUsernameDialog = true },
                onOpenWallpaperPicker = { wallpaperPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onDeactivateAccount = {
                  onToggleShowOnlineStatus(false)
                  onSignOut()
                },
                onDeleteAccount = { showDeleteStepOne = true },
              )
            }
            SettingsSectionKey.PRIVACY -> {
              PrivacySectionContent(
                settings = settings,
                restrictedCount = settings.restrictedUsernames.size,
                blockedCount = blockedUsers.size,
                onToggleLastActive = { onSetLastActiveEnabled(it) },
                onToggleActiveNow = {
                  onSetActiveNowEnabled(it)
                  onToggleShowOnlineStatus(it)
                },
                onToggleReadReceipts = { onSetReadReceiptsEnabled(it) },
                onToggleTypingIndicator = { onSetTypingIndicatorEnabled(it) },
                onSetWhoCanMessageMe = { onSetWhoCanMessageMe(it) },
                onOpenBlockedUsers = { activeCategory = SettingsSectionKey.BLOCKED },
              )
            }
            SettingsSectionKey.BLOCKED -> {
              BlockedUsersSectionContent(
                blockedUsers = blockedUsers,
                onUnblockClick = { pendingUnblockTarget = it },
              )
            }
            SettingsSectionKey.CHATS -> {
              ChatsSectionContent(
                settings = settings,
                onSetDisappearingTimer = { onSetDisappearingTimer(it) },
                onToggleScheduledMessages = { onSetScheduledMessagesEnabled(it) },
                onSetMediaAutoDownload = { onSetMediaAutoDownload(it) },
                onOpenWallpaperPicker = { wallpaperPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onRemoveWallpaper = { onSetWallpaperPath(null) },
              )
            }
            SettingsSectionKey.CALLS -> {
              CallsSectionContent(
                settings = settings,
                onSetWhoCanCallMe = { onSetWhoCanCallMe(it) },
                onToggleDataSaver = { onSetCallDataSaverEnabled(it) },
                onSetCallRingtone = { onSetCallRingtone(it) },
              )
            }
            SettingsSectionKey.MIP -> {
              MipSectionContent(
                settings = settings,
                activeMipPartnerName = activeMipPartnerName,
                onUnlinkMip = onUnlinkMip,
                onToggleNotifSounds = { onSetMipNotificationSounds(it) },
                onToggleRingtone = { onSetMipRingtoneSounds(it) },
                onToggleHelloTune = {
                  onSetMipHelloTune(it)
                  onToggleHaptics(it)
                },
              )
            }
            SettingsSectionKey.NOTIFICATIONS -> {
              NotificationsSectionContent(
                settings = settings,
                onToggleMessageNotifications = { onToggleNotifications(it) },
                onToggleMessagePreview = { onSetMessagePreviewEnabled(it) },
                onToggleCallNotifications = { onSetCallNotificationsEnabled(it) },
                onToggleBirthdayAlerts = { onSetBirthdayAlertsEnabled(it) },
                onOpenMuteDurationDialog = { showMuteDurationDialog = true },
              )
            }
            SettingsSectionKey.SECURITY -> {
              SecuritySectionContent(
                keyFingerprint = keyFingerprint,
                settings = settings,
                onOpenAppLockDialog = { showAppLockDialog = true },
                onToggleAppLock = { onSetAppLockEnabled(it) },
              )
            }
            SettingsSectionKey.HELP -> {
              HelpAboutSectionContent(
                currentUsername = profile.username,
                onOpenPrivacy = onOpenPrivacy,
                onOpenTerms = onOpenTerms,
              )
            }
          }
        }
      }
    }
  }

  // Dialogs
  if (showEditProfileDialog) {
    EditProfileDialog(
      profile = profile,
      initialBio = settings.bio,
      onDismiss = { showEditProfileDialog = false },
      onSave = { newName, newBio ->
        onUpdateProfile(newName, newBio, "")
        onSetBio(newBio)
      },
    )
  }

  if (showChangeUsernameDialog) {
    ChangeUsernameDialog(
      currentUsername = profile.username,
      onDismiss = { showChangeUsernameDialog = false },
      onCheckAvailability = onCheckUsername,
      onChange = { oldName, newName ->
        onChangeUsername(oldName, newName) {
          showChangeUsernameDialog = false
        }
      },
    )
  }

  pendingUnblockTarget?.let { target ->
    UnblockConfirmDialog(
      username = target.blockedUsername,
      onDismiss = { pendingUnblockTarget = null },
      onConfirm = {
        onUnblockUser(target.blockedUid)
        pendingUnblockTarget = null
      },
    )
  }

  if (showMuteDurationDialog) {
    MuteAllDurationDialog(
      onDismiss = { showMuteDurationDialog = false },
      onSelectDuration = onSetMuteAllUntilMs,
    )
  }

  if (showAppLockDialog) {
    AppLockSetupDialog(
      initialType = settings.appLockType,
      onDismiss = { showAppLockDialog = false },
      onApply = { type, pin ->
        onSetAppLockType(type)
        onSetAppLockPin(pin)
        onSetAppLockEnabled(true)
      },
    )
  }

  pendingWallpaperUri?.let { uri ->
    WallpaperCropDialog(
      imageUri = uri,
      onDismiss = { pendingWallpaperUri = null },
      onApply = { path ->
        onSetWallpaperPath(path)
        pendingWallpaperUri = null
      },
    )
  }

  if (showDeleteStepOne) {
    AlertDialog(
      onDismissRequest = { showDeleteStepOne = false },
      containerColor = MioColors.CardSurface,
      title = { Text("Delete Account?", color = MioColors.DangerEmber) },
      text = {
        Text(
          text = "This will permanently delete your account profile, release your username, and remove all your data. This action cannot be undone.",
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.PrimaryText,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showDeleteStepOne = false
            showDeleteStepTwo = true
          },
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber),
          modifier = Modifier.testTag("confirm_delete_step_one"),
        ) {
          Text("Continue", color = MioColors.PureBlack)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeleteStepOne = false }) {
          Text("Cancel", color = MioColors.MutedText)
        }
      },
    )
  }

  if (showDeleteStepTwo) {
    AlertDialog(
      onDismissRequest = {
        showDeleteStepTwo = false
        deleteConfirmText = ""
      },
      containerColor = MioColors.CardSurface,
      title = { Text("Final Confirmation", color = MioColors.DangerEmber) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
          Text(
            text = "Type your username (${profile.username}) to confirm final account deletion:",
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.PrimaryText,
          )
          OutlinedTextField(
            value = deleteConfirmText,
            onValueChange = { deleteConfirmText = it },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MioColors.DangerEmber,
              unfocusedBorderColor = MioColors.SubtleBorder,
              focusedTextColor = MioColors.PrimaryText,
              unfocusedTextColor = MioColors.PrimaryText,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("delete_confirm_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (deleteConfirmText.trim().lowercase() == profile.username.lowercase()) {
              showDeleteStepTwo = false
              deleteConfirmText = ""
              onDeleteAccount()
            }
          },
          enabled = deleteConfirmText.trim().lowercase() == profile.username.lowercase() && !isSubmitting,
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber),
          modifier = Modifier.testTag("confirm_delete_final_button"),
        ) {
          Text("Delete Permanently", color = MioColors.PureBlack)
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            showDeleteStepTwo = false
            deleteConfirmText = ""
          },
        ) {
          Text("Cancel", color = MioColors.MutedText)
        }
      },
    )
  }
}

@Composable
private fun ProfileHeaderCard(
  profile: UserProfile,
  bio: String,
  onEditClick: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(32.dp),
    color = MioColors.CardSurface,
    border = BorderStroke(1.dp, MioColors.SubtleBorder),
    modifier = Modifier
      .fillMaxWidth()
      .height(420.dp),
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      // Background with SVG default profile illustration
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Brush.verticalGradient(listOf(MioColors.PlumBase, MioColors.PureBlack))),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_default_profile),
          contentDescription = null,
          tint = MioColors.RoseAccent,
          modifier = Modifier.size(96.dp),
        )
      }

      // Bottom Blur / Gradient Overlay
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(240.dp)
          .align(Alignment.BottomCenter)
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.Transparent,
                MioColors.PureBlack.copy(alpha = 0.8f),
                MioColors.PureBlack.copy(alpha = 0.98f),
              )
            )
          ),
      )

      // Content at bottom
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .align(Alignment.BottomCenter)
          .padding(MioSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
          ) {
            Text(
              text = profile.displayName.ifBlank { "Mio User" },
              style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MioColors.PrimaryText,
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Icon(
              imageVector = Icons.Outlined.CheckCircle,
              contentDescription = "Verified",
              tint = MioColors.RoseAccent,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        Text(
          text = "@${profile.username} • End-to-End Encrypted",
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.MutedText,
        )

        if (bio.isNotBlank()) {
          Text(
            text = bio,
            style = MaterialTheme.typography.bodySmall,
            color = MioColors.PrimaryText.copy(alpha = 0.9f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(Modifier.height(2.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(MioSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Favorite,
                contentDescription = null,
                tint = MioColors.RoseAccent,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Mio Couple",
                style = MaterialTheme.typography.labelMedium,
                color = MioColors.PrimaryText,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }

          Button(
            onClick = onEditClick,
            colors = ButtonDefaults.buttonColors(containerColor = MioColors.PrimaryText),
            shape = RoundedCornerShape(MioRadii.pill),
            modifier = Modifier.testTag("profile_edit_button"),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = MioColors.PureBlack,
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = "Edit Profile",
                color = MioColors.PureBlack,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
              )
            }
          }
        }
      }
    }
  }
}
