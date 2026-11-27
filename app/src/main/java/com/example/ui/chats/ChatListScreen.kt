package com.example.ui.chats

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
  myProfile: UserProfile,
  chatsState: UiState<List<ChatThreadPreview>>,
  searchResult: UsernameClaim?,
  searchError: String?,
  isSearching: Boolean,
  actionMessage: String?,
  chatWallpaperPath: String?,
  contentPadding: PaddingValues,
  initialOpenConnectDialog: Boolean = false,
  onConnectDialogDismissed: () -> Unit = {},
  onOpenChat: (String, Boolean) -> Unit,
  onDeleteChatHistory: (ChatThreadPreview) -> Unit,
  onSearchExactUsername: (String) -> Unit,
  onClearSearch: () -> Unit,
  onSendChatRequest: (UsernameClaim) -> Unit,
  onRespondToRequest: (String, Boolean) -> Unit,
  onRetry: () -> Unit,
  onDismissActionMessage: () -> Unit,
) {
  var showConnectDialog by rememberSaveable(initialOpenConnectDialog) { mutableStateOf(initialOpenConnectDialog) }
  var searchQuery by rememberSaveable { mutableStateOf("") }
  var longPressedChatId by rememberSaveable { mutableStateOf<String?>(null) }
  var confirmDeleteChatId by rememberSaveable { mutableStateOf<String?>(null) }
  val listState = rememberLazyListState()

  LaunchedEffect(initialOpenConnectDialog) {
    if (initialOpenConnectDialog) {
      showConnectDialog = true
    }
  }

  val wallpaperBitmap = remember(chatWallpaperPath) {
    if (!chatWallpaperPath.isNullOrBlank()) {
      runCatching {
        val file = File(chatWallpaperPath)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
      }.getOrNull()
    } else null
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MioColors.PureBlack)
      .padding(bottom = contentPadding.calculateBottomPadding()),
  ) {
    Column(
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier
          .weight(1f)
          .widthIn(max = 680.dp)
          .fillMaxWidth(),
      ) {
        if (actionMessage != null) {
          Surface(
            shape = RoundedCornerShape(MioRadii.md),
            color = MioColors.CardSurface,
            border = BorderStroke(1.dp, MioColors.SubtleBorder),
            modifier = Modifier
              .fillMaxWidth()
              .statusBarsPadding()
              .padding(horizontal = MioSpacing.md, vertical = MioSpacing.xs)
              .clickable { onDismissActionMessage() },
          ) {
            Text(
              text = actionMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MioColors.PrimaryText,
              modifier = Modifier.padding(MioSpacing.sm),
            )
          }
        }

        when (chatsState) {
          is UiState.Loading -> MioLoadingState("Gathering your conversations...")
          is UiState.Error -> MioErrorState(chatsState.message, onRetry = onRetry)
          is UiState.Success -> {
            val allChats = chatsState.data
            val filteredChats = if (searchQuery.isBlank()) {
              allChats
            } else {
              allChats.filter {
                it.partnerDisplayName.contains(searchQuery, ignoreCase = true) ||
                  it.partnerUsername.contains(searchQuery, ignoreCase = true) ||
                  it.lastMessagePreview.contains(searchQuery, ignoreCase = true)
              }
            }

            val incomingRequests = filteredChats.filter {
              it.status == ChatRequestStatus.PENDING && it.initiatorUid != myProfile.uid
            }
            val acceptedChats = filteredChats.filter { it.status == ChatRequestStatus.ACCEPTED }
            val activeAndOutgoing = filteredChats.filter {
              it.status == ChatRequestStatus.ACCEPTED ||
                (it.status == ChatRequestStatus.PENDING && it.initiatorUid == myProfile.uid)
            }

            LazyColumn(
              state = listState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = 88.dp),
            ) {
              // Top Rounded Header Card matching reference design ("Let's Stay Connected")
              item(key = "top_header_card") {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(MioColors.CardSurface),
                ) {
                  if (wallpaperBitmap != null) {
                    androidx.compose.foundation.Image(
                      bitmap = wallpaperBitmap.asImageBitmap(),
                      contentDescription = null,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.matchParentSize(),
                    )
                    // Upper blur/soft fade
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .align(Alignment.TopCenter)
                        .background(
                          Brush.verticalGradient(
                            colors = listOf(MioColors.PureBlack.copy(alpha = 0.65f), Color.Transparent)
                          )
                        )
                    )
                    // Lower blur/soft fade into card edge
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                          Brush.verticalGradient(
                            colors = listOf(Color.Transparent, MioColors.CardSurface.copy(alpha = 0.65f), MioColors.CardSurface)
                          )
                        )
                    )
                  }

                  Column(
                    modifier = Modifier
                      .fillMaxWidth()
                      .statusBarsPadding()
                      .padding(horizontal = MioSpacing.md, vertical = MioSpacing.md)
                      .padding(bottom = MioSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(MioSpacing.md),
                  ) {
                    // Top Bar: Full-width rounded search pill (BasicTextField so text is centered and never clipped)
                    Surface(
                      shape = RoundedCornerShape(MioRadii.pill),
                      color = MioColors.PureBlack.copy(alpha = 0.45f),
                      border = BorderStroke(1.dp, MioColors.SubtleBorder.copy(alpha = 0.5f)),
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MioSpacing.minTouchTarget),
                    ) {
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .padding(horizontal = MioSpacing.md, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Icon(
                          imageVector = Icons.Outlined.Search,
                          contentDescription = "Search",
                          tint = MioColors.MutedText,
                          modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(MioSpacing.sm))
                        Box(
                          modifier = Modifier.weight(1f),
                          contentAlignment = Alignment.CenterStart,
                        ) {
                          if (searchQuery.isEmpty()) {
                            Text(
                              text = "Search...",
                              style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                              color = MioColors.MutedText,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis,
                            )
                          }
                          BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                              fontSize = 16.sp,
                              color = MioColors.PrimaryText,
                            ),
                            cursorBrush = SolidColor(MioColors.RoseAccent),
                            modifier = Modifier
                              .fillMaxWidth()
                              .testTag("conversations_search_input"),
                          )
                        }
                        if (searchQuery.isNotEmpty()) {
                          IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp),
                          ) {
                            Icon(
                              imageVector = Icons.Outlined.Close,
                              contentDescription = "Clear search",
                              tint = MioColors.MutedText,
                              modifier = Modifier.size(18.dp),
                            )
                          }
                        }
                      }
                    }

                    // Large Bold Heading: "Let's Stay Connected"
                    Text(
                      text = "Let's Stay\nConnected",
                      style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 36.sp,
                      ),
                      color = MioColors.PrimaryText,
                    )

                    // Horizontal Contact Circles Row
                    LazyRow(
                      horizontalArrangement = Arrangement.spacedBy(MioSpacing.md),
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MioSpacing.sm),
                    ) {
                      // First item: Add circle
                      item(key = "add_circle") {
                        Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          modifier = Modifier
                            .clickable { showConnectDialog = true }
                            .testTag("add_contact_circle"),
                        ) {
                          Box(
                            modifier = Modifier
                              .size(56.dp)
                              .clip(CircleShape)
                              .background(MioColors.PureBlack.copy(alpha = 0.3f))
                              .border(
                                BorderStroke(1.5.dp, MioColors.RoseAccent.copy(alpha = 0.6f)),
                                CircleShape,
                              ),
                            contentAlignment = Alignment.Center,
                          ) {
                            Icon(
                              imageVector = Icons.Outlined.Add,
                              contentDescription = MioCopy.CONNECT_CTA,
                              tint = MioColors.RoseAccent,
                              modifier = Modifier.size(24.dp),
                            )
                          }
                          Spacer(Modifier.height(6.dp))
                          Text(
                            text = "Add",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            color = MioColors.PrimaryText,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                          )
                        }
                      }

                      // Accepted contacts with username under avatar circle
                      items(acceptedChats, key = { it.chatId }) { chat ->
                        Column(
                          horizontalAlignment = Alignment.CenterHorizontally,
                          modifier = Modifier
                            .clickable { onOpenChat(chat.chatId, true) }
                            .testTag("accepted_contact_${chat.chatId}"),
                        ) {
                          Box(modifier = Modifier.size(56.dp)) {
                            Box(
                              modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MioColors.RoseAccent),
                              contentAlignment = Alignment.Center,
                            ) {
                              Text(
                                text = chat.partnerDisplayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MioColors.PureBlack,
                                fontWeight = FontWeight.Bold,
                              )
                            }
                            if (chat.isMipActive) {
                              Surface(
                                shape = CircleShape,
                                color = MioColors.PureBlack,
                                modifier = Modifier
                                  .align(Alignment.BottomEnd)
                                  .size(18.dp),
                              ) {
                                Box(contentAlignment = Alignment.Center) {
                                  Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = "MIP",
                                    tint = MioColors.RoseAccent,
                                    modifier = Modifier.size(12.dp),
                                  )
                                }
                              }
                            }
                          }
                          Spacer(Modifier.height(6.dp))
                          Text(
                            text = if (chat.partnerUsername.isNotBlank()) "@${chat.partnerUsername}" else chat.partnerDisplayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                            color = MioColors.PrimaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.widthIn(max = 68.dp),
                          )
                        }
                      }
                    }
                  }
                }
              }

              // Spacer below top card
              item(key = "spacer_below_card") {
                Spacer(Modifier.height(MioSpacing.md))
              }

              // 4. Chat List / Requests on Pure Black Background
              if (incomingRequests.isEmpty() && activeAndOutgoing.isEmpty()) {
                item(key = "empty_state") {
                  Box(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = MioSpacing.xxl),
                    contentAlignment = Alignment.Center,
                  ) {
                    MioEmptyState(
                      title = MioCopy.EMPTY_CHATS_TITLE,
                      body = MioCopy.EMPTY_CHATS_BODY,
                      actionLabel = MioCopy.CONNECT_CTA,
                      onAction = { showConnectDialog = true },
                    )
                  }
                }
              } else {
                if (incomingRequests.isNotEmpty()) {
                  item(key = "requests_header") {
                    Text(
                      text = MioCopy.REQUESTS_HEADER,
                      style = MaterialTheme.typography.labelMedium,
                      color = MioColors.RoseAccent,
                      modifier = Modifier.padding(start = MioSpacing.md, end = MioSpacing.md, top = MioSpacing.sm, bottom = MioSpacing.xxs),
                    )
                  }
                  items(incomingRequests, key = { "req_${it.chatId}" }) { invite ->
                    Box(modifier = Modifier.padding(start = MioSpacing.md, end = MioSpacing.md, top = 4.dp, bottom = 4.dp)) {
                      IncomingRequestCard(
                        chat = invite,
                        onAccept = { onRespondToRequest(invite.chatId, true) },
                        onDecline = { onRespondToRequest(invite.chatId, false) },
                      )
                    }
                  }
                }

                items(activeAndOutgoing, key = { it.chatId }) { chat ->
                  ChatListRow(
                    chat = chat,
                    myUid = myProfile.uid,
                    onClick = { onOpenChat(chat.chatId, false) },
                    onLongClick = { longPressedChatId = chat.chatId },
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  val allSuccessChats = (chatsState as? UiState.Success)?.data.orEmpty()
  val longPressedChat = longPressedChatId?.let { id -> allSuccessChats.firstOrNull { it.chatId == id } }
  val confirmDeleteChat = confirmDeleteChatId?.let { id -> allSuccessChats.firstOrNull { it.chatId == id } }

  if (longPressedChat != null) {
    ModalBottomSheet(
      onDismissRequest = { longPressedChatId = null },
      containerColor = MioColors.CardSurface,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MioSpacing.md)
          .padding(bottom = MioSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(MioSpacing.sm),
      ) {
        Column {
          Text(
            text = longPressedChat.partnerDisplayName,
            style = MaterialTheme.typography.titleMedium,
            color = MioColors.PrimaryText,
          )
          Text(
            text = "@${longPressedChat.partnerUsername}",
            style = MaterialTheme.typography.labelMedium,
            color = MioColors.MutedText,
          )
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MioSpacing.minTouchTarget)
            .clip(RoundedCornerShape(MioRadii.md))
            .clickable {
              val targetId = longPressedChat.chatId
              longPressedChatId = null
              confirmDeleteChatId = targetId
            }
            .padding(horizontal = MioSpacing.sm, vertical = 12.dp)
            .testTag("sheet_delete_chats_${longPressedChat.chatId}"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
        ) {
          Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = MioCopy.DELETE_CHATS_OPTION,
            tint = MioColors.DangerEmber,
          )
          Column {
            Text(
              text = MioCopy.DELETE_CHATS_OPTION,
              style = MaterialTheme.typography.bodyLarge,
              color = MioColors.DangerEmber,
              fontWeight = FontWeight.Medium,
            )
            Text(
              text = "Delete messages from internal storage",
              style = MaterialTheme.typography.labelSmall,
              color = MioColors.MutedText,
            )
          }
        }
      }
    }
  }

  if (confirmDeleteChat != null) {
    AlertDialog(
      onDismissRequest = { confirmDeleteChatId = null },
      containerColor = MioColors.CardSurface,
      title = {
        Text(
          text = MioCopy.DELETE_CHATS_TITLE,
          style = MaterialTheme.typography.titleLarge,
          color = MioColors.PrimaryText,
        )
      },
      text = {
        Text(
          text = MioCopy.DELETE_CHATS_BODY,
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.MutedText,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteChatHistory(confirmDeleteChat)
            confirmDeleteChatId = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber),
          modifier = Modifier
            .heightIn(min = MioSpacing.minTouchTarget)
            .testTag("confirm_list_delete_chats_button"),
        ) {
          Text(
            text = MioCopy.DELETE_CHATS_CONFIRM,
            color = MioColors.PureBlack,
            fontWeight = FontWeight.Bold,
          )
        }
      },
      dismissButton = {
        TextButton(
          onClick = { confirmDeleteChatId = null },
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Cancel", color = MioColors.MutedText)
        }
      },
    )
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
        onConnectDialogDismissed()
      },
      onDismiss = {
        onClearSearch()
        showConnectDialog = false
        onConnectDialogDismissed()
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
    color = MioColors.CardSurface,
    border = BorderStroke(1.dp, MioColors.SubtleBorder),
    shadowElevation = 6.dp,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("incoming_request_${chat.chatId}"),
  ) {
    Column(modifier = Modifier.padding(MioSpacing.md)) {
      Text(
        text = chat.partnerDisplayName,
        style = MaterialTheme.typography.titleMedium,
        color = MioColors.PrimaryText,
      )
      Text(
        text = "@${chat.partnerUsername} ${MioCopy.PENDING_INCOMING_BANNER}",
        style = MaterialTheme.typography.bodyMedium,
        color = MioColors.MutedText,
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
          Text("Decline", color = MioColors.MutedText)
        }
        Button(
          onClick = onAccept,
          colors = ButtonDefaults.buttonColors(
            containerColor = MioColors.RoseAccent,
            contentColor = MioColors.PureBlack,
          ),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = MioSpacing.minTouchTarget)
            .testTag("accept_request_${chat.chatId}"),
        ) {
          Text("Accept", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListRow(
  chat: ChatThreadPreview,
  myUid: String,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val isUnread = chat.unreadCount > 0
  val isMe = chat.lastMessageSenderUid == myUid
  val rawPreview = chat.lastMessagePreview
  val previewText = when {
    chat.partnerTyping -> MioCopy.TYPING_INDICATOR
    isMe && rawPreview.isNotBlank() -> "You: $rawPreview"
    else -> rawPreview
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 72.dp)
      .background(MioColors.PureBlack)
      .combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
      )
      .padding(horizontal = MioSpacing.md)
      .testTag("chat_row_${chat.chatId}"),
    verticalArrangement = Arrangement.Center,
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(MioSpacing.md),
    ) {
      // 48dp Avatar circle with green online presence dot
      Box(modifier = Modifier.size(48.dp)) {
        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MioColors.RoseAccent),
          contentAlignment = Alignment.Center,
        ) {
          if (chat.isMipActive) {
            WaxSealEmblem(size = 36.dp)
          } else {
            Text(
              text = chat.partnerDisplayName.take(1).uppercase(),
              style = MaterialTheme.typography.titleMedium,
              color = MioColors.PureBlack,
              fontWeight = FontWeight.Bold,
            )
          }
        }
        // Green online status dot from RTDB /status/{partnerUid}
        if (chat.partnerOnline && chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny) {
          Surface(
            shape = CircleShape,
            color = MioColors.OnlineGreen,
            border = BorderStroke(1.5.dp, MioColors.PureBlack),
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .size(12.dp)
              .testTag("online_dot_${chat.chatId}"),
          ) {}
        }
      }

      // Name & preview
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = chat.partnerDisplayName,
            style = MaterialTheme.typography.bodyLarge.copy(
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
            ),
            color = MioColors.PrimaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(Modifier.height(2.dp))

        Text(
          text = previewText,
          style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
          ),
          color = if (chat.partnerTyping) MioColors.RoseAccent else MioColors.MutedText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      // Right-aligned column: timestamp top, unread pill below
      Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.Center,
      ) {
        if (chat.lastMessageTimeMs > 0L) {
          Text(
            text = formatChatTimestamp(chat.lastMessageTimeMs),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            color = if (isUnread) MioColors.RoseAccent else MioColors.MutedText,
          )
        }
        if (isUnread) {
          Spacer(Modifier.height(4.dp))
          Surface(
            shape = CircleShape,
            color = MioColors.RoseAccent,
          ) {
            Text(
              text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
              color = MioColors.PureBlack,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            )
          }
        }
      }
    }

    // 0.5dp bottom divider in faint tint
    Spacer(
      modifier = Modifier
        .fillMaxWidth()
        .height(0.5.dp)
        .background(MioColors.SubtleBorder.copy(alpha = 0.3f))
    )
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
    containerColor = MioColors.CardSurface,
    title = {
      Text(
        text = MioCopy.CONNECT_CTA,
        style = MaterialTheme.typography.titleLarge,
        color = MioColors.PrimaryText,
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
        Text(
          text = MioCopy.SEARCH_USERNAME_PROMPT,
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.MutedText,
        )
        OutlinedTextField(
          value = query,
          onValueChange = { input ->
            query = input.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
              .take(MioConstants.MAX_USERNAME_LENGTH)
          },
          prefix = { Text("@", color = MioColors.RoseAccent) },
          placeholder = { Text("exact_username") },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MioColors.RoseAccent,
            unfocusedBorderColor = MioColors.SubtleBorder,
            focusedTextColor = MioColors.PrimaryText,
            unfocusedTextColor = MioColors.PrimaryText,
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
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MioColors.RoseAccent, strokeWidth = 2.dp)
            Text("Looking up handle...", style = MaterialTheme.typography.labelMedium, color = MioColors.MutedText)
          }
        }

        if (searchError != null) {
          Text(
            text = searchError,
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.MutedText,
          )
        }

        if (searchResult != null) {
          Surface(
            shape = RoundedCornerShape(MioRadii.md),
            color = MioColors.PureBlack,
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
                  color = MioColors.PrimaryText,
                )
                Text(
                  text = "@${searchResult.username}",
                  style = MaterialTheme.typography.labelMedium,
                  color = MioColors.RoseAccent,
                )
              }
              Button(
                onClick = { onSendRequest(searchResult) },
                colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
                modifier = Modifier
                  .heightIn(min = MioSpacing.minTouchTarget)
                  .testTag("send_chat_request_button"),
              ) {
                Text("Invite", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
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
        colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
        modifier = Modifier
          .heightIn(min = MioSpacing.minTouchTarget)
          .testTag("search_username_button"),
      ) {
        Icon(Icons.Outlined.Search, contentDescription = "Find", modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Find", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
      ) {
        Text("Close", color = MioColors.MutedText)
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
