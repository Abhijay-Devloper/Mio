package com.example.ui.chats

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAddAlt1
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.data.model.ChatRequestStatus
import com.example.data.model.ChatThreadPreview
import com.example.data.model.MessageStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UsernameClaim
import com.example.ui.components.MioEmptyState
import com.example.ui.components.MioErrorState
import com.example.ui.components.MioLoadingState
import com.example.ui.components.WaxSealEmblem
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatListScreen(
  myProfile: UserProfile,
  chatsState: UiState<List<ChatThreadPreview>>,
  searchResult: UsernameClaim?,
  searchError: String?,
  isSearching: Boolean,
  actionMessage: String?,
  contentPadding: PaddingValues,
  onOpenChat: (String) -> Unit,
  onSearchExactUsername: (String) -> Unit,
  onClearSearch: () -> Unit,
  onSendChatRequest: (UsernameClaim) -> Unit,
  onRespondToRequest: (String, Boolean) -> Unit,
  onRetry: () -> Unit,
  onDismissActionMessage: () -> Unit,
) {
  var showConnectDialog by rememberSaveable { mutableStateOf(false) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MioColors.PlumBase)
      .padding(bottom = contentPadding.calculateBottomPadding()),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Header bar in MidnightSlate (#262F45)
      Surface(
        color = MioColors.MidnightSlate,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .statusBarsPadding()
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .padding(horizontal = MioSpacing.md, vertical = MioSpacing.md),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = MioCopy.CHATS_TITLE,
              style = MaterialTheme.typography.headlineMedium,
              color = MioColors.WarmOffWhite,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "@${myProfile.username}",
              style = MaterialTheme.typography.labelMedium,
              color = MioColors.BlushRose,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(Modifier.width(MioSpacing.sm))
          OutlinedButton(
            onClick = { showConnectDialog = true },
            border = BorderStroke(1.dp, MioColors.SubtleBorder),
            shape = RoundedCornerShape(MioRadii.pill),
            modifier = Modifier
              .heightIn(min = MioSpacing.minTouchTarget)
              .testTag("connect_header_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.PersonAddAlt1,
              contentDescription = MioCopy.CONNECT_CTA,
              tint = MioColors.BlushRose,
              modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(MioSpacing.xs))
            Text(MioCopy.CONNECT_CTA, color = MioColors.WarmOffWhite)
          }
        }
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .widthIn(max = 680.dp)
          .fillMaxWidth(),
      ) {
        if (actionMessage != null) {
          Surface(
            shape = RoundedCornerShape(MioRadii.md),
            color = MioColors.BurgundySurface,
            border = BorderStroke(1.dp, MioColors.SubtleBorder),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = MioSpacing.md, vertical = MioSpacing.xs)
              .clickable { onDismissActionMessage() },
          ) {
            Text(
              text = actionMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MioColors.WarmOffWhite,
              modifier = Modifier.padding(MioSpacing.sm),
            )
          }
        }

        when (chatsState) {
          is UiState.Loading -> MioLoadingState("Gathering your conversations...")
          is UiState.Error -> MioErrorState(chatsState.message, onRetry = onRetry)
          is UiState.Success -> {
            val allChats = chatsState.data
            val incomingRequests = allChats.filter {
              it.status == ChatRequestStatus.PENDING && it.initiatorUid != myProfile.uid
            }
            val activeAndOutgoing = allChats.filter {
              it.status == ChatRequestStatus.ACCEPTED ||
                (it.status == ChatRequestStatus.PENDING && it.initiatorUid == myProfile.uid)
            }

            if (incomingRequests.isEmpty() && activeAndOutgoing.isEmpty()) {
              MioEmptyState(
                title = MioCopy.EMPTY_CHATS_TITLE,
                body = MioCopy.EMPTY_CHATS_BODY,
                actionLabel = MioCopy.CONNECT_CTA,
                onAction = { showConnectDialog = true },
              )
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                  start = MioSpacing.md,
                  end = MioSpacing.md,
                  top = MioSpacing.sm,
                  bottom = 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(MioSpacing.sm),
              ) {
                if (incomingRequests.isNotEmpty()) {
                  item(key = "requests_header") {
                    Text(
                      text = MioCopy.REQUESTS_HEADER,
                      style = MaterialTheme.typography.labelMedium,
                      color = MioColors.BlushRose,
                      modifier = Modifier.padding(top = MioSpacing.xs, bottom = MioSpacing.xxs),
                    )
                  }
                  items(incomingRequests, key = { "req_${it.chatId}" }) { invite ->
                    IncomingRequestCard(
                      chat = invite,
                      onAccept = { onRespondToRequest(invite.chatId, true) },
                      onDecline = { onRespondToRequest(invite.chatId, false) },
                    )
                  }
                }

                items(activeAndOutgoing, key = { it.chatId }) { chat ->
                  ChatListRow(
                    chat = chat,
                    myUid = myProfile.uid,
                    onClick = { onOpenChat(chat.chatId) },
                  )
                }
              }
            }
          }
        }
      }
    }

    FloatingActionButton(
      onClick = { showConnectDialog = true },
      containerColor = MioColors.CrimsonPrimary,
      contentColor = MioColors.WarmOffWhite,
      shape = CircleShape,
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(MioSpacing.md)
        .testTag("connect_fab"),
    ) {
      Icon(
        imageVector = Icons.Outlined.PersonAddAlt1,
        contentDescription = MioCopy.CONNECT_CTA,
      )
    }
  }

  if (showConnectDialog) {
    ConnectExactUsernameDialog(
      searchResult = searchResult,
      searchError = searchError,
      isSearching = isSearching,
      onSearch = onSearchExactUsername,
      onSendRequest = { claim ->
        onSendChatRequest(claim)
        showConnectDialog = false
      },
      onDismiss = {
        onClearSearch()
        showConnectDialog = false
      },
    )
  }
}

@Composable
private fun IncomingRequestCard(
  chat: ChatThreadPreview,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(MioRadii.lg),
    color = MioColors.BurgundySurface,
    border = BorderStroke(1.dp, MioColors.SubtleBorder),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("incoming_request_${chat.chatId}"),
  ) {
    Column(modifier = Modifier.padding(MioSpacing.md)) {
      Text(
        text = chat.partnerDisplayName,
        style = MaterialTheme.typography.titleMedium,
        color = MioColors.WarmOffWhite,
      )
      Text(
        text = "@${chat.partnerUsername} ${MioCopy.PENDING_INCOMING_BANNER}",
        style = MaterialTheme.typography.bodyMedium,
        color = MioColors.MutedRose,
      )
      Spacer(Modifier.height(MioSpacing.sm))
      Row(horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        OutlinedButton(
          onClick = onDecline,
          border = BorderStroke(1.dp, MioColors.SubtleBorder),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MioSpacing.minTouchTarget)
            .testTag("decline_request_${chat.chatId}"),
        ) {
          Text("Decline", color = MioColors.MutedRose)
        }
        Button(
          onClick = onAccept,
          colors = ButtonDefaults.buttonColors(
            containerColor = MioColors.CrimsonPrimary,
            contentColor = MioColors.WarmOffWhite,
          ),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MioSpacing.minTouchTarget)
            .testTag("accept_request_${chat.chatId}"),
        ) {
          Text("Accept")
        }
      }
    }
  }
}

@Composable
private fun ChatListRow(
  chat: ChatThreadPreview,
  myUid: String,
  onClick: () -> Unit,
) {
  val isUnread = chat.unreadCount > 0
  Surface(
    shape = RoundedCornerShape(MioRadii.lg),
    color = MioColors.BurgundySurface,
    border = BorderStroke(
      width = 1.dp,
      color = if (chat.isMipActive || isUnread) MioColors.BlushRose.copy(alpha = 0.5f) else MioColors.SubtleBorder,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MioSpacing.minTouchTarget)
      .clip(RoundedCornerShape(MioRadii.lg))
      .clickable(onClick = onClick)
      .testTag("chat_row_${chat.chatId}"),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = MioSpacing.md, vertical = MioSpacing.sm),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
    ) {
      // Avatar monogram or Wax Seal for MIP
      Box(
        modifier = Modifier
          .size(50.dp)
          .clip(CircleShape)
          .background(MioColors.CrimsonPrimary),
        contentAlignment = Alignment.Center,
      ) {
        if (chat.isMipActive) {
          WaxSealEmblem(size = 38.dp)
        } else {
          Text(
            text = chat.partnerDisplayName.take(1).uppercase(),
            style = MaterialTheme.typography.titleLarge,
            color = MioColors.WarmOffWhite,
          )
        }
      }

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
            modifier = Modifier.weight(1f),
          ) {
            Text(
              text = chat.partnerDisplayName,
              style = MaterialTheme.typography.titleMedium,
              color = MioColors.WarmOffWhite,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (chat.isMipActive) {
              Surface(
                shape = RoundedCornerShape(MioRadii.pill),
                color = MioColors.CrimsonPrimary,
              ) {
                Text(
                  text = "MIP",
                  style = MaterialTheme.typography.labelSmall,
                  color = MioColors.WarmOffWhite,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
              }
            }
          }
          if (chat.lastMessageTimeMs > 0L) {
            Text(
              text = formatChatTimestamp(chat.lastMessageTimeMs),
              style = MaterialTheme.typography.labelSmall,
              color = if (isUnread) MioColors.BlushRose else MioColors.MutedRose,
            )
          }
        }

        Spacer(Modifier.height(2.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val previewText = when {
            chat.partnerTyping -> MioCopy.TYPING_INDICATOR
            chat.lastMessageSenderUid == myUid && chat.lastMessageStatus != null ->
              "${chat.lastMessageStatus.label} · ${chat.lastMessagePreview}"
            else -> chat.lastMessagePreview
          }
          Text(
            text = previewText,
            style = MaterialTheme.typography.bodyMedium.copy(
              fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = when {
              chat.partnerTyping -> MioColors.BlushRose
              chat.lastMessageSenderUid == myUid && chat.lastMessageStatus == MessageStatus.READ -> MioColors.TwilightBlue
              isUnread -> MioColors.WarmOffWhite
              else -> MioColors.MutedRose
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )

          if (isUnread) {
            Spacer(Modifier.width(MioSpacing.xs))
            Surface(
              shape = CircleShape,
              color = MioColors.BlushRose,
              modifier = Modifier.testTag("unread_badge_${chat.chatId}"),
            ) {
              Text(
                text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MioColors.PlumBase,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ConnectExactUsernameDialog(
  searchResult: UsernameClaim?,
  searchError: String?,
  isSearching: Boolean,
  onSearch: (String) -> Unit,
  onSendRequest: (UsernameClaim) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MioColors.BurgundySurface,
    title = {
      Text(
        text = MioCopy.CONNECT_CTA,
        style = MaterialTheme.typography.titleLarge,
        color = MioColors.WarmOffWhite,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text(
          text = MioCopy.SEARCH_USERNAME_PROMPT,
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.MutedRose,
        )
        OutlinedTextField(
          value = query,
          onValueChange = { input ->
            query = input.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
              .take(MioConstants.MAX_USERNAME_LENGTH)
          },
          prefix = { Text("@", color = MioColors.BlushRose) },
          placeholder = { Text("exact_username") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MioColors.BlushRose,
            unfocusedBorderColor = MioColors.SubtleBorder,
            focusedTextColor = MioColors.WarmOffWhite,
            unfocusedTextColor = MioColors.WarmOffWhite,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("connect_username_input"),
        )

        if (isSearching) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
          ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MioColors.BlushRose, strokeWidth = 2.dp)
            Text("Looking up handle...", style = MaterialTheme.typography.labelMedium, color = MioColors.MutedRose)
          }
        }

        if (searchError != null) {
          Text(
            text = searchError,
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.MutedRose,
          )
        }

        if (searchResult != null) {
          Surface(
            shape = RoundedCornerShape(MioRadii.md),
            color = MioColors.PlumBase,
            border = BorderStroke(1.dp, MioColors.SubtleBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(MioSpacing.sm),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = searchResult.displayName,
                  style = MaterialTheme.typography.titleMedium,
                  color = MioColors.WarmOffWhite,
                )
                Text(
                  text = "@${searchResult.username}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MioColors.BlushRose,
                )
              }
              Button(
                onClick = { onSendRequest(searchResult) },
                colors = ButtonDefaults.buttonColors(containerColor = MioColors.CrimsonPrimary),
                modifier = Modifier
                  .heightIn(min = MioSpacing.minTouchTarget)
                  .testTag("send_chat_request_button"),
              ) {
                Text("Invite", color = MioColors.WarmOffWhite)
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSearch(query) },
        enabled = query.length >= MioConstants.MIN_USERNAME_LENGTH && !isSearching,
        colors = ButtonDefaults.buttonColors(containerColor = MioColors.CrimsonPrimary),
        modifier = Modifier
          .heightIn(min = MioSpacing.minTouchTarget)
          .testTag("search_username_button"),
      ) {
        Icon(Icons.Outlined.Search, contentDescription = "Find", modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Find", color = MioColors.WarmOffWhite)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
      ) {
        Text("Close", color = MioColors.MutedRose)
      }
    },
  )
}

fun formatChatTimestamp(timeMs: Long): String {
  if (timeMs <= 0L) return ""
  val now = System.currentTimeMillis()
  val diff = now - timeMs
  val dayMs = 86_400_000L
  return if (diff < dayMs) {
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timeMs))
  } else if (diff < 7 * dayMs) {
    SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timeMs))
  } else {
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timeMs))
  }
}
