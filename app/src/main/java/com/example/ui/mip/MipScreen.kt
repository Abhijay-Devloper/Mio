package com.example.ui.mip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.core.config.MioCopy
import com.example.data.model.ChatThreadPreview
import com.example.data.model.MipStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.MioEmptyState
import com.example.ui.components.MioErrorState
import com.example.ui.components.MioLoadingState
import com.example.ui.components.SealedLetterRevealDialog
import com.example.ui.components.WaxSealEmblem
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioGradients
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing

@Composable
fun MipScreen(
  myProfile: UserProfile,
  chatsState: UiState<List<ChatThreadPreview>>,
  mipError: String?,
  contentPadding: PaddingValues,
  onOpenChat: (String) -> Unit,
  onRespondToMip: (chat: ChatThreadPreview, accept: Boolean) -> Unit,
  onUnlinkMip: (chat: ChatThreadPreview) -> Unit,
  onDismissMipError: () -> Unit,
  onRetry: () -> Unit,
) {
  var letterChatId by rememberSaveable { mutableStateOf<String?>(null) }
  var unlinkChatId by rememberSaveable { mutableStateOf<String?>(null) }

  val chatsList = (chatsState as? UiState.Success)?.data.orEmpty()
  val showLetterDialogFor = letterChatId?.let { id -> chatsList.firstOrNull { it.chatId == id } }
  val showUnlinkConfirmFor = unlinkChatId?.let { id -> chatsList.firstOrNull { it.chatId == id } }

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
          text = MioCopy.MIP_TITLE,
          style = MaterialTheme.typography.headlineMedium,
          color = MioColors.WarmOffWhite,
        )
        Text(
          text = MioCopy.MIP_SUBTITLE,
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.MutedRose,
        )
      }
    }

    if (mipError != null) {
      Surface(
        shape = RoundedCornerShape(MioRadii.md),
        color = MioColors.DangerEmber.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.55f)),
        modifier = Modifier
          .widthIn(max = 600.dp)
          .fillMaxWidth()
          .padding(horizontal = MioSpacing.md, vertical = MioSpacing.xs)
          .clickable { onDismissMipError() },
      ) {
        Text(
          text = "$mipError (Tap to dismiss)",
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.DangerEmber,
          modifier = Modifier.padding(MioSpacing.sm),
        )
      }
    }

    when (chatsState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          MioLoadingState("Checking your wax seal...")
        }
      }
      is UiState.Error -> {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          MioErrorState(chatsState.message, onRetry = onRetry)
        }
      }
      is UiState.Success -> {
        val activeMipChat = chatsState.data.firstOrNull { it.mipStatus == MipStatus.ACCEPTED }
        val pendingMipChats = chatsState.data.filter { it.mipStatus == MipStatus.PENDING && !it.isBlockedAny }

        Column(
          modifier = Modifier
            .weight(1f)
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(MioSpacing.md),
          verticalArrangement = Arrangement.spacedBy(MioSpacing.md),
        ) {
          val cooldownUntilMs = myProfile.mipCooldownUntil?.toDate()?.time ?: 0L
          if (cooldownUntilMs > System.currentTimeMillis()) {
            val hoursLeft = ((cooldownUntilMs - System.currentTimeMillis()) / 3_600_000L).coerceAtLeast(1L)
            Surface(
              shape = RoundedCornerShape(MioRadii.md),
              color = MioColors.MidnightSlate,
              border = BorderStroke(1.dp, MioColors.SlateBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "Quiet cooldown active: about ${hoursLeft}h remaining before sealing a new MIP letter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MioColors.TwilightBlue,
                modifier = Modifier.padding(MioSpacing.md),
              )
            }
          }

          if (activeMipChat != null) {
            Surface(
              shape = RoundedCornerShape(MioRadii.lg),
              border = BorderStroke(1.dp, MioColors.BlushRose.copy(alpha = 0.55f)),
              color = MioColors.BurgundySurface,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(MioGradients.sunsetVertical),
              ) {
                Column(
                  modifier = Modifier
                    .fillMaxWidth()
                    .background(MioColors.PlumBase.copy(alpha = 0.72f))
                    .padding(MioSpacing.lg),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  WaxSealEmblem(size = 84.dp, broken = false)
                  Spacer(Modifier.height(MioSpacing.md))
                  Text(
                    text = activeMipChat.partnerDisplayName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MioColors.WarmOffWhite,
                  )
                  Text(
                    text = "@${activeMipChat.partnerUsername}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MioColors.BlushRose,
                  )
                  if (activeMipChat.mipNoteDecrypted.isNotBlank()) {
                    Spacer(Modifier.height(MioSpacing.md))
                    Surface(
                      shape = RoundedCornerShape(MioRadii.md),
                      color = MioColors.BurgundySurface.copy(alpha = 0.92f),
                      border = BorderStroke(1.dp, MioColors.SubtleBorder),
                      modifier = Modifier.fillMaxWidth(),
                    ) {
                      Text(
                        text = "“${activeMipChat.mipNoteDecrypted}”",
                        style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic),
                        color = MioColors.WarmOffWhite,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(MioSpacing.md),
                      )
                    }
                  }
                  Spacer(Modifier.height(MioSpacing.lg))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
                  ) {
                    OutlinedButton(
                      onClick = { unlinkChatId = activeMipChat.chatId },
                      border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.6f)),
                      modifier = Modifier
                        .weight(1f)
                        .heightIn(min = MioSpacing.minTouchTarget)
                        .testTag("mip_unlink_button"),
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.LinkOff,
                        contentDescription = "Unlink MIP",
                        tint = MioColors.DangerEmber,
                        modifier = Modifier.size(18.dp),
                      )
                      Spacer(Modifier.width(MioSpacing.xxs))
                      Text("Unlink MIP", color = MioColors.DangerEmber)
                    }
                    Button(
                      onClick = { onOpenChat(activeMipChat.chatId) },
                      colors = ButtonDefaults.buttonColors(containerColor = MioColors.CrimsonPrimary),
                      modifier = Modifier
                        .weight(1f)
                        .heightIn(min = MioSpacing.minTouchTarget)
                        .testTag("mip_open_chat_button"),
                    ) {
                      Text("Open Chat", color = MioColors.WarmOffWhite)
                    }
                  }
                }
              }
            }
          } else if (pendingMipChats.isNotEmpty()) {
            pendingMipChats.forEach { pending ->
              val isIncoming = pending.mipSenderUid != myProfile.uid
              Surface(
                shape = RoundedCornerShape(MioRadii.lg),
                color = MioColors.BurgundySurface,
                border = BorderStroke(1.dp, MioColors.SubtleBorder),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Column(
                  modifier = Modifier.padding(MioSpacing.lg),
                  horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                  WaxSealEmblem(size = 64.dp, broken = false)
                  Spacer(Modifier.height(MioSpacing.sm))
                  Text(
                    text = if (isIncoming) {
                      "Sealed MIP Letter from ${pending.partnerDisplayName}"
                    } else {
                      "Sealed MIP Letter sent to ${pending.partnerDisplayName}"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MioColors.WarmOffWhite,
                    textAlign = TextAlign.Center,
                  )
                  Spacer(Modifier.height(MioSpacing.xs))
                  Text(
                    text = if (isIncoming) {
                      "Tap below to break the wax seal and read your invitation."
                    } else {
                      "Waiting for ${pending.partnerDisplayName} to break the wax seal."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MioColors.MutedRose,
                    textAlign = TextAlign.Center,
                  )
                  Spacer(Modifier.height(MioSpacing.md))
                  Button(
                    onClick = { letterChatId = pending.chatId },
                    colors = ButtonDefaults.buttonColors(containerColor = MioColors.CrimsonPrimary),
                    modifier = Modifier
                      .heightIn(min = MioSpacing.minTouchTarget)
                      .testTag("mip_open_seal_button"),
                  ) {
                    Text(if (isIncoming) "Open Sealed Letter" else "Preview Letter", color = MioColors.WarmOffWhite)
                  }
                }
              }
            }
          } else {
            MioEmptyState(
              title = MioCopy.MIP_EMPTY_TITLE,
              body = "${MioCopy.MIP_EMPTY_BODY}\n\n${MioCopy.MIP_COOLDOWN_NOTICE}",
              icon = Icons.Outlined.FavoriteBorder,
            )
          }
        }
      }
    }
  }

  showLetterDialogFor?.let { chat ->
    val isIncoming = chat.mipSenderUid != myProfile.uid
    SealedLetterRevealDialog(
      senderName = if (isIncoming) chat.partnerDisplayName else myProfile.displayName,
      note = chat.mipNoteDecrypted,
      isIncoming = isIncoming,
      onAccept = {
        onRespondToMip(chat, true)
        letterChatId = null
      },
      onDecline = {
        onRespondToMip(chat, false)
        letterChatId = null
      },
      onDismiss = { letterChatId = null },
    )
  }

  showUnlinkConfirmFor?.let { chat ->
    AlertDialog(
      onDismissRequest = { unlinkChatId = null },
      containerColor = MioColors.BurgundySurface,
      title = { Text("Unlink MIP?", color = MioColors.DangerEmber) },
      text = {
        Text(
          text = "Unlinking removes the MIP seal with ${chat.partnerDisplayName} and begins a 24-hour quiet cooldown before you can seal a new MIP.",
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.WarmOffWhite,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onUnlinkMip(chat)
            unlinkChatId = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber),
          modifier = Modifier
            .heightIn(min = MioSpacing.minTouchTarget)
            .testTag("mip_confirm_unlink_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.LinkOff,
            contentDescription = "Unlink",
            tint = MioColors.PlumBase,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(MioSpacing.xxs))
          Text("Unlink", color = MioColors.PlumBase)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { unlinkChatId = null },
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Cancel", color = MioColors.MutedRose)
        }
      },
    )
  }
}
