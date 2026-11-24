package com.example.ui.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.core.config.CatMood
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRequestStatus
import com.example.data.model.ChatThreadPreview
import com.example.data.model.MessageContent
import com.example.data.model.MessageStatus
import com.example.data.model.MipStatus
import com.example.data.model.ReplyQuote
import com.example.data.model.ReportReason
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.MioEmptyState
import com.example.ui.components.MioErrorState
import com.example.ui.components.MioLoadingState
import com.example.ui.components.MoodCatArtwork
import com.example.ui.components.SealedLetterRevealDialog
import com.example.ui.components.WaxSealEmblem
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioGradients
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
  myProfile: UserProfile,
  chatState: UiState<ChatThreadPreview?>,
  messagesState: UiState<List<ChatMessage>>,
  replyQuote: ReplyQuote?,
  selectedMood: CatMood?,
  bannerNotice: String?,
  onBack: () -> Unit,
  onTextChanged: (String) -> Unit,
  onSendMessage: (ChatThreadPreview, String) -> Boolean,
  onRetryFailedMessage: (ChatThreadPreview, String) -> Unit,
  onSetReplyQuote: (ChatMessage, String) -> Unit,
  onClearReplyQuote: () -> Unit,
  onSelectMood: (CatMood?) -> Unit,
  onToggleReaction: (String, String) -> Unit,
  onDeleteForMe: (String) -> Unit,
  onDeleteForEveryone: (String) -> Unit,
  onRespondToChatInvite: (Boolean) -> Unit,
  onBlockPartner: (ChatThreadPreview) -> Unit,
  onUnblockPartner: (ChatThreadPreview) -> Unit,
  onReportPartner: (ChatThreadPreview, ReportReason, String?) -> Unit,
  onSendMipLetter: (ChatThreadPreview, String, () -> Unit) -> Unit,
  onRespondToMip: (ChatThreadPreview, Boolean) -> Unit,
  onDismissBanner: () -> Unit,
  onRetry: () -> Unit,
) {
  val clipboard = LocalClipboardManager.current
  val scope = rememberCoroutineScope()
  val listState = rememberLazyListState()

  var composerText by rememberSaveable { mutableStateOf("") }
  var showMoodPicker by rememberSaveable { mutableStateOf(false) }
  var showOverflowMenu by remember { mutableStateOf(false) }
  var selectedMessageIdForSheet by rememberSaveable { mutableStateOf<String?>(null) }
  var showReportDialog by rememberSaveable { mutableStateOf(false) }
  var reportExcerpt by rememberSaveable { mutableStateOf("") }
  var showComposeMipDialog by rememberSaveable { mutableStateOf(false) }
  var showSealRevealDialog by rememberSaveable { mutableStateOf(false) }

  val chat = (chatState as? UiState.Success)?.data
  val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
  val selectedMessageForSheet = selectedMessageIdForSheet?.let { id ->
    messages.firstOrNull { it.id == id }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  Scaffold(
    containerColor = MioColors.PlumBase,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MioColors.MidnightSlate,
          titleContentColor = MioColors.WarmOffWhite,
        ),
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("chat_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = MioColors.WarmOffWhite,
            )
          }
        },
        title = {
          if (chat != null) {
            Column {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
              ) {
                Text(
                  text = chat.partnerDisplayName,
                  style = MaterialTheme.typography.titleLarge,
                  color = MioColors.WarmOffWhite,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (chat.isMipActive) {
                  WaxSealEmblem(size = 20.dp)
                }
              }
              Text(
                text = if (chat.partnerTyping) MioCopy.TYPING_INDICATOR else "@${chat.partnerUsername}",
                style = MaterialTheme.typography.labelMedium,
                color = if (chat.partnerTyping) MioColors.BlushRose else MioColors.MutedRose,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          } else {
            Text("Conversation", style = MaterialTheme.typography.titleLarge)
          }
        },
        actions = {
          if (chat != null && chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny) {
            IconButton(
              onClick = {
                if (chat.mipStatus == MipStatus.PENDING || chat.mipStatus == MipStatus.ACCEPTED) {
                  showSealRevealDialog = true
                } else {
                  showComposeMipDialog = true
                }
              },
              modifier = Modifier.testTag("chat_mip_button"),
            ) {
              Icon(
                imageVector = Icons.Outlined.FavoriteBorder,
                contentDescription = "MIP Sealed Letter",
                tint = if (chat.isMipActive || chat.mipStatus == MipStatus.PENDING) MioColors.BlushRose else MioColors.MutedRose,
              )
            }
          }
          if (chat != null) {
            Box {
              IconButton(
                onClick = { showOverflowMenu = true },
                modifier = Modifier.testTag("chat_overflow_button"),
              ) {
                Icon(
                  imageVector = Icons.Outlined.MoreVert,
                  contentDescription = "Conversation actions",
                  tint = MioColors.WarmOffWhite,
                )
              }
              DropdownMenu(
                expanded = showOverflowMenu,
                onDismissRequest = { showOverflowMenu = false },
                containerColor = MioColors.BurgundySurface,
              ) {
                DropdownMenuItem(
                  text = {
                    Text(
                      text = if (chat.blockedByMe) "Unblock @${chat.partnerUsername}" else "Block @${chat.partnerUsername}",
                      color = MioColors.DangerEmber,
                    )
                  },
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Outlined.Block,
                      contentDescription = null,
                      tint = MioColors.DangerEmber,
                    )
                  },
                  onClick = {
                    showOverflowMenu = false
                    if (chat.blockedByMe) onUnblockPartner(chat) else onBlockPartner(chat)
                  },
                )
                DropdownMenuItem(
                  text = { Text("Report user", color = MioColors.DangerEmber) },
                  leadingIcon = {
                    Icon(Icons.Outlined.Shield, contentDescription = null, tint = MioColors.DangerEmber)
                  },
                  onClick = {
                    showOverflowMenu = false
                    reportExcerpt = ""
                    showReportDialog = true
                  },
                )
              }
            }
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(MioColors.PlumBase)
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
        .navigationBarsPadding()
        .imePadding(),
    ) {
      if (bannerNotice != null) {
        Surface(
          color = MioColors.BurgundySurface,
          border = BorderStroke(1.dp, MioColors.SubtleBorder),
          modifier = Modifier
            .fillMaxWidth()
            .clickable { onDismissBanner() },
        ) {
          Text(
            text = bannerNotice,
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.WarmOffWhite,
            modifier = Modifier.padding(horizontal = MioSpacing.md, vertical = MioSpacing.sm),
          )
        }
      }

      when (chatState) {
        is UiState.Loading -> MioLoadingState("Unlocking private room...")
        is UiState.Error -> MioErrorState(chatState.message, onRetry = onRetry)
        is UiState.Success -> {
          if (chat == null) {
            MioEmptyState(
              title = "Conversation unavailable",
              body = "This thread may have been removed.",
              actionLabel = "Go back",
              onAction = onBack,
            )
          } else {
            // Pending / Declined / Blocked / MIP Seal Banners
            if (chat.status == ChatRequestStatus.PENDING) {
              PendingChatBanner(
                chat = chat,
                myUid = myProfile.uid,
                onAccept = { onRespondToChatInvite(true) },
                onDecline = { onRespondToChatInvite(false) },
              )
            } else if (chat.status == ChatRequestStatus.DECLINED) {
              Surface(
                color = MioColors.BurgundySurface,
                border = BorderStroke(1.dp, MioColors.SubtleBorder),
                shape = RoundedCornerShape(MioRadii.md),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(MioSpacing.sm),
              ) {
                Text(
                  text = "This chat invitation was declined.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MioColors.MutedRose,
                  modifier = Modifier.padding(MioSpacing.md),
                )
              }
            } else if (chat.isBlockedAny) {
              Surface(
                color = MioColors.DangerEmber.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.45f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(MioSpacing.sm),
                shape = RoundedCornerShape(MioRadii.md),
              ) {
                Text(
                  text = MioCopy.BLOCKED_BANNER,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MioColors.DangerEmber,
                  modifier = Modifier.padding(MioSpacing.md),
                )
              }
            } else if (chat.mipStatus == MipStatus.PENDING && chat.mipSenderUid != myProfile.uid) {
              Surface(
                color = MioColors.BurgundySurface,
                border = BorderStroke(1.dp, MioColors.BlushRose.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(MioRadii.md),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = MioSpacing.md, vertical = MioSpacing.xs)
                  .clickable { showSealRevealDialog = true }
                  .testTag("pending_mip_banner"),
              ) {
                Row(
                  modifier = Modifier.padding(MioSpacing.sm),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
                ) {
                  WaxSealEmblem(size = 38.dp)
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "${chat.partnerDisplayName} sent you a sealed MIP letter",
                      style = MaterialTheme.typography.titleMedium,
                      color = MioColors.WarmOffWhite,
                    )
                    Text(
                      text = "Tap to break the wax seal",
                      style = MaterialTheme.typography.labelMedium,
                      color = MioColors.BlushRose,
                    )
                  }
                }
              }
            }

            // Messages area
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
              when (messagesState) {
                is UiState.Loading -> MioLoadingState("Decrypting messages...")
                is UiState.Error -> MioErrorState(messagesState.message, onRetry = onRetry)
                is UiState.Success -> {
                  if (messages.isEmpty()) {
                    MioEmptyState(
                      title = "Your private thread",
                      body = MioCopy.DEVICE_HISTORY_NOTICE,
                    )
                  } else {
                    val lastSentMessageId = messages.lastOrNull { it.senderUid == myProfile.uid }?.id
                    LazyColumn(
                      state = listState,
                      modifier = Modifier.fillMaxSize(),
                      contentPadding = PaddingValues(
                        horizontal = MioSpacing.md,
                        vertical = MioSpacing.sm,
                      ),
                      verticalArrangement = Arrangement.spacedBy(MioSpacing.xs),
                    ) {
                      itemsIndexed(messages, key = { _, msg -> msg.id }) { index, message ->
                        val prev = messages.getOrNull(index - 1)
                        val showTimeHeader = prev == null || (message.timestampMs - prev.timestampMs) > 15 * 60 * 1000L
                        val isMine = message.senderUid == myProfile.uid

                        val showStatusLabel = isMine && (
                          message.id == lastSentMessageId ||
                            message.status != MessageStatus.READ
                          )

                        if (showTimeHeader) {
                          Text(
                            text = formatMessageDayTime(message.timestampMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MioColors.MutedRose,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                              .fillMaxWidth()
                              .padding(vertical = MioSpacing.xs, horizontal = MioSpacing.xs),
                          )
                        }

                        SwipeableMessageRow(
                          message = message,
                          isMine = isMine,
                          showStatusLabel = showStatusLabel,
                          onSwipeToReply = {
                            if (!message.deletedForEveryone) {
                              onSetReplyQuote(message, chat.partnerDisplayName)
                            }
                          },
                          onOpenActions = { selectedMessageIdForSheet = message.id },
                          onRetryFailed = { onRetryFailedMessage(chat, message.id) },
                          onReplyQuoteClicked = { quotedId ->
                            val targetIdx = messages.indexOfFirst { it.id == quotedId }
                            if (targetIdx >= 0) {
                              scope.launch { listState.animateScrollToItem(targetIdx) }
                            }
                          },
                        )
                      }
                    }
                  }
                }
              }
            }

            // Composer & Mood Cat Picker (only when accepted & unblocked)
            if (chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny) {
              AnimatedVisibility(
                visible = replyQuote != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
              ) {
                replyQuote?.let { quote ->
                  ReplyPreviewBar(quote = quote, onClear = onClearReplyQuote)
                }
              }

              AnimatedVisibility(
                visible = showMoodPicker || selectedMood != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
              ) {
                MoodCatPickerRow(
                  selectedMood = selectedMood,
                  onSelectMood = { mood ->
                    onSelectMood(if (selectedMood == mood) null else mood)
                  },
                )
              }

              MessageComposerBar(
                text = composerText,
                selectedMood = selectedMood,
                moodPickerOpen = showMoodPicker,
                onTextChanged = { newText ->
                  composerText = newText.take(MioConstants.MAX_MESSAGE_LENGTH)
                  onTextChanged(composerText)
                },
                onToggleMoodPicker = { showMoodPicker = !showMoodPicker },
                onSend = {
                  if (onSendMessage(chat, composerText)) {
                    composerText = ""
                    showMoodPicker = false
                  }
                },
              )
            }
          }
        }
      }
    }
  }

  // Long-press / Double-tap Emoji Reaction & Message Action Bottom Sheet
  selectedMessageForSheet?.let { targetMsg ->
    val isMine = targetMsg.senderUid == myProfile.uid
    val canReactAndReply = !targetMsg.deletedForEveryone && targetMsg.status != MessageStatus.FAILED
    ModalBottomSheet(
      onDismissRequest = { selectedMessageIdForSheet = null },
      containerColor = MioColors.BurgundySurface,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MioSpacing.md)
          .padding(bottom = MioSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(MioSpacing.sm),
      ) {
        if (canReactAndReply) {
          Text(
            text = "React with feeling",
            style = MaterialTheme.typography.labelMedium,
            color = MioColors.MutedRose,
          )
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
          ) {
            itemsIndexed(MioConstants.QUICK_REACTIONS) { _, emoji ->
              val isSelected = targetMsg.reactions[myProfile.uid] == emoji
              Surface(
                shape = CircleShape,
                color = if (isSelected) MioColors.CrimsonPrimary else MioColors.MidnightSlate,
                border = BorderStroke(
                  1.dp,
                  if (isSelected) MioColors.BlushRose else MioColors.SlateBorder,
                ),
                modifier = Modifier
                  .size(48.dp)
                  .clip(CircleShape)
                  .clickable {
                    onToggleReaction(targetMsg.id, emoji)
                    selectedMessageIdForSheet = null
                  }
                  .testTag("reaction_pick_$emoji"),
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                }
              }
            }
          }
        }

        if (canReactAndReply && chat != null) {
          ActionSheetItem(
            icon = Icons.AutoMirrored.Outlined.Reply,
            label = "Reply",
            onClick = {
              onSetReplyQuote(targetMsg, chat.partnerDisplayName)
              selectedMessageIdForSheet = null
            },
          )
          val copyText = when (val c = targetMsg.content) {
            is MessageContent.Text -> c.body
            is MessageContent.Mood -> c.caption
          }
          if (copyText.isNotBlank()) {
            ActionSheetItem(
              icon = Icons.Outlined.ContentCopy,
              label = "Copy text",
              onClick = {
                clipboard.setText(AnnotatedString(copyText))
                selectedMessageIdForSheet = null
              },
            )
          }
          ActionSheetItem(
            icon = Icons.Outlined.Shield,
            label = "Report with this excerpt",
            tint = MioColors.DangerEmber,
            onClick = {
              reportExcerpt = copyText.take(MioConstants.MAX_REPORT_EXCERPT_LENGTH)
              selectedMessageIdForSheet = null
              showReportDialog = true
            },
          )
        }

        ActionSheetItem(
          icon = Icons.Outlined.DeleteOutline,
          label = "Delete for me",
          tint = MioColors.DangerEmber,
          onClick = {
            onDeleteForMe(targetMsg.id)
            selectedMessageIdForSheet = null
          },
        )

        if (isMine && !targetMsg.deletedForEveryone && targetMsg.status != MessageStatus.FAILED) {
          ActionSheetItem(
            icon = Icons.Outlined.DeleteOutline,
            label = "Delete for everyone",
            tint = MioColors.DangerEmber,
            onClick = {
              onDeleteForEveryone(targetMsg.id)
              selectedMessageIdForSheet = null
            },
          )
        }
      }
    }
  }

  // Compose Sealed MIP Letter Dialog
  if (showComposeMipDialog && chat != null) {
    var mipNote by rememberSaveable { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showComposeMipDialog = false },
      containerColor = MioColors.BurgundySurface,
      title = {
        Text("Seal a MIP Letter", style = MaterialTheme.typography.headlineMedium, color = MioColors.WarmOffWhite)
      },
      text = {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(MioSpacing.sm),
        ) {
          WaxSealEmblem(size = 56.dp)
          Text(
            text = "Invite ${chat.partnerDisplayName} to be your Most Important Person.",
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.MutedRose,
          )
          OutlinedTextField(
            value = mipNote,
            onValueChange = { mipNote = it.take(MioConstants.MAX_MIP_NOTE_LENGTH) },
            placeholder = { Text("Write a short private note...") },
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MioColors.BlushRose,
              unfocusedBorderColor = MioColors.SubtleBorder,
              focusedTextColor = MioColors.WarmOffWhite,
              unfocusedTextColor = MioColors.WarmOffWhite,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("mip_letter_note_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onSendMipLetter(chat, mipNote) { showComposeMipDialog = false }
          },
          enabled = mipNote.trim().isNotEmpty(),
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.CrimsonPrimary),
          modifier = Modifier
            .heightIn(min = MioSpacing.minTouchTarget)
            .testTag("send_mip_letter_button"),
        ) {
          Text("Seal & Send", color = MioColors.WarmOffWhite)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showComposeMipDialog = false },
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Cancel", color = MioColors.MutedRose)
        }
      },
    )
  }

  if (showSealRevealDialog && chat != null) {
    val isIncoming = chat.mipSenderUid != myProfile.uid && chat.mipStatus == MipStatus.PENDING
    SealedLetterRevealDialog(
      senderName = if (chat.mipSenderUid == myProfile.uid) myProfile.displayName else chat.partnerDisplayName,
      note = chat.mipNoteDecrypted,
      isIncoming = isIncoming,
      onAccept = {
        onRespondToMip(chat, true)
        showSealRevealDialog = false
      },
      onDecline = {
        onRespondToMip(chat, false)
        showSealRevealDialog = false
      },
      onDismiss = { showSealRevealDialog = false },
    )
  }

  if (showReportDialog && chat != null) {
    var selectedReason by rememberSaveable { mutableStateOf(ReportReason.HARASSMENT) }
    AlertDialog(
      onDismissRequest = { showReportDialog = false },
      containerColor = MioColors.BurgundySurface,
      title = { Text("Report @${chat.partnerUsername}", color = MioColors.DangerEmber) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
          ReportReason.entries.forEach { reason ->
            val selected = reason == selectedReason
            Surface(
              shape = RoundedCornerShape(MioRadii.sm),
              color = if (selected) MioColors.MidnightSlate else MioColors.PlumBase,
              border = BorderStroke(1.dp, if (selected) MioColors.BlushRose else MioColors.SubtleBorder),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MioSpacing.minTouchTarget)
                .clickable { selectedReason = reason },
            ) {
              Text(
                text = reason.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MioColors.WarmOffWhite,
                modifier = Modifier.padding(MioSpacing.sm),
              )
            }
          }
          OutlinedTextField(
            value = reportExcerpt,
            onValueChange = { reportExcerpt = it.take(MioConstants.MAX_REPORT_EXCERPT_LENGTH) },
            label = { Text("Optional excerpt to share") },
            minLines = 2,
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MioColors.BlushRose,
              unfocusedBorderColor = MioColors.SubtleBorder,
              focusedTextColor = MioColors.WarmOffWhite,
              unfocusedTextColor = MioColors.WarmOffWhite,
            ),
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            onReportPartner(chat, selectedReason, reportExcerpt.ifBlank { null })
            showReportDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber),
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            tint = MioColors.PlumBase,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(MioSpacing.xxs))
          Text("Submit Report", color = MioColors.PlumBase)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showReportDialog = false },
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Cancel", color = MioColors.MutedRose)
        }
      },
    )
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableMessageRow(
  message: ChatMessage,
  isMine: Boolean,
  showStatusLabel: Boolean,
  onSwipeToReply: () -> Unit,
  onOpenActions: () -> Unit,
  onRetryFailed: () -> Unit,
  onReplyQuoteClicked: (String) -> Unit,
) {
  val density = LocalDensity.current
  val thresholdPx = with(density) { MioConstants.SWIPE_REPLY_THRESHOLD_DP.dp.toPx() }
  val dragOffsetX = remember(message.id) { Animatable(0f) }
  val scope = rememberCoroutineScope()

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .pointerInput(message.id, message.deletedForEveryone) {
        if (message.deletedForEveryone) return@pointerInput
        detectHorizontalDragGestures(
          onHorizontalDrag = { change, dragAmount ->
            change.consume()
            val target = (dragOffsetX.value + dragAmount).coerceIn(0f, thresholdPx * 1.25f)
            scope.launch { dragOffsetX.snapTo(target) }
          },
          onDragEnd = {
            if (dragOffsetX.value >= thresholdPx * 0.85f) {
              onSwipeToReply()
            }
            scope.launch {
              dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
          },
          onDragCancel = {
            scope.launch {
              dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
          },
        )
      },
  ) {
    // Reply icon revealed during swipe
    val swipeProgress = (dragOffsetX.value / thresholdPx).coerceIn(0f, 1f)
    if (swipeProgress > 0.08f) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.Reply,
        contentDescription = null,
        tint = MioColors.BlushRose,
        modifier = Modifier
          .align(Alignment.CenterStart)
          .padding(start = MioSpacing.xs)
          .alpha(swipeProgress),
      )
    }

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) },
      horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
      Box(
        modifier = Modifier.widthIn(max = 300.dp),
      ) {
        val bubbleShape = RoundedCornerShape(
          topStart = MioRadii.bubble,
          topEnd = MioRadii.bubble,
          bottomStart = if (isMine) MioRadii.bubble else 6.dp,
          bottomEnd = if (isMine) 6.dp else MioRadii.bubble,
        )
        val bubbleModifier = Modifier
          .clip(bubbleShape)
          .then(
            if (message.deletedForEveryone) {
              Modifier.background(MioColors.BurgundySurface.copy(alpha = 0.65f))
            } else if (isMine) {
              Modifier.background(MioGradients.myBubble)
            } else {
              Modifier.background(MioColors.BurgundySurface)
            }
          )
          .combinedClickable(
            onClick = {},
            onLongClick = onOpenActions,
            onDoubleClick = { if (!message.deletedForEveryone) onOpenActions() },
          )
          .padding(horizontal = MioSpacing.md, vertical = MioSpacing.sm)
          .testTag("message_bubble_${message.id}")

        Column(modifier = bubbleModifier) {
          if (message.deletedForEveryone) {
            Text(
              text = MioCopy.DELETED_FOR_EVERYONE_LABEL,
              style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
              color = MioColors.MutedRose,
            )
          } else {
            message.replyQuote?.let { quote ->
              Surface(
                shape = RoundedCornerShape(MioRadii.sm),
                color = MioColors.PlumBase.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MioColors.BlushRose.copy(alpha = 0.35f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(bottom = MioSpacing.xs)
                  .clickable { onReplyQuoteClicked(quote.messageId) },
              ) {
                Column(modifier = Modifier.padding(horizontal = MioSpacing.xs, vertical = 6.dp)) {
                  Text(
                    text = quote.senderName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MioColors.BlushRose,
                  )
                  Text(
                    text = quote.snippet,
                    style = MaterialTheme.typography.labelMedium,
                    color = MioColors.MutedRose,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }

            when (val content = message.content) {
              is MessageContent.Text -> {
                Text(
                  text = content.body,
                  style = MaterialTheme.typography.bodyLarge,
                  color = MioColors.WarmOffWhite,
                )
              }
              is MessageContent.Mood -> {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
                ) {
                  MoodCatArtwork(mood = content.mood, size = 48.dp)
                  Column {
                    Text(
                      text = "${content.mood.label} Cat",
                      style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                      color = content.mood.accentColor,
                    )
                    Text(
                      text = content.caption.ifBlank { content.mood.tagline },
                      style = MaterialTheme.typography.bodyLarge,
                      color = MioColors.WarmOffWhite,
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Reactions row tucked cleanly below bubble
      if (!message.deletedForEveryone && message.reactions.isNotEmpty()) {
        val grouped = message.reactions.values.groupingBy { it }.eachCount()
        Row(
          modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          grouped.forEach { (emoji, count) ->
            Surface(
              shape = RoundedCornerShape(MioRadii.pill),
              color = MioColors.BurgundySurface,
              border = BorderStroke(1.dp, MioColors.SubtleBorder),
              modifier = Modifier.clickable { onOpenActions() },
            ) {
              Text(
                text = if (count > 1) "$emoji $count" else emoji,
                style = MaterialTheme.typography.labelSmall,
                color = MioColors.WarmOffWhite,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
        }
      }

      // Small status receipt text ("Sending...", "Sent", "Delivered", "Read", or tappable "Not sent · Tap to retry")
      if (showStatusLabel) {
        val isFailed = message.status == MessageStatus.FAILED
        Text(
          text = if (isFailed) "${message.status.label} · Tap to retry" else message.status.label,
          style = MaterialTheme.typography.labelSmall,
          color = when (message.status) {
            MessageStatus.READ -> MioColors.TwilightBlue
            MessageStatus.FAILED -> MioColors.DangerEmber
            else -> MioColors.MutedRose
          },
          modifier = Modifier
            .then(if (isFailed) Modifier.clickable { onRetryFailed() } else Modifier)
            .padding(top = 2.dp, end = 4.dp)
            .testTag("message_status_${message.id}"),
        )
      }
    }
  }
}

@Composable
private fun PendingChatBanner(
  chat: ChatThreadPreview,
  myUid: String,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
) {
  Surface(
    color = MioColors.BurgundySurface,
    border = BorderStroke(1.dp, MioColors.SubtleBorder),
    shape = RoundedCornerShape(MioRadii.md),
    modifier = Modifier
      .fillMaxWidth()
      .padding(MioSpacing.md),
  ) {
    Column(modifier = Modifier.padding(MioSpacing.md)) {
      if (chat.initiatorUid == myUid) {
        Text(
          text = MioCopy.PENDING_OUTGOING_BANNER,
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.MutedRose,
        )
      } else {
        Text(
          text = "@${chat.partnerUsername} ${MioCopy.PENDING_INCOMING_BANNER}",
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.WarmOffWhite,
        )
        Spacer(Modifier.height(MioSpacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm)) {
          OutlinedButton(
            onClick = onDecline,
            border = BorderStroke(1.dp, MioColors.SubtleBorder),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = MioSpacing.minTouchTarget),
          ) {
            Text("Decline", color = MioColors.MutedRose)
          }
          Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(containerColor = MioColors.CrimsonPrimary),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = MioSpacing.minTouchTarget),
          ) {
            Text("Accept", color = MioColors.WarmOffWhite)
          }
        }
      }
    }
  }
}

@Composable
private fun ReplyPreviewBar(
  quote: ReplyQuote,
  onClear: () -> Unit,
) {
  Surface(
    color = MioColors.BurgundySurface,
    border = BorderStroke(1.dp, MioColors.SubtleBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = MioSpacing.md, vertical = MioSpacing.xs),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.Reply,
        contentDescription = null,
        tint = MioColors.BlushRose,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(MioSpacing.xs))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Replying to ${quote.senderName}",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = MioColors.BlushRose,
        )
        Text(
          text = quote.snippet,
          style = MaterialTheme.typography.labelMedium,
          color = MioColors.MutedRose,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onClear) {
        Icon(Icons.Outlined.Close, contentDescription = "Cancel reply", tint = MioColors.MutedRose)
      }
    }
  }
}

@Composable
private fun MoodCatPickerRow(
  selectedMood: CatMood?,
  onSelectMood: (CatMood) -> Unit,
) {
  Surface(
    color = MioColors.MidnightSlate,
    border = BorderStroke(1.dp, MioColors.SlateBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    LazyRow(
      contentPadding = PaddingValues(horizontal = MioSpacing.md, vertical = MioSpacing.xs),
      horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
    ) {
      itemsIndexed(CatMood.entries) { _, mood ->
        val selected = selectedMood == mood
        Surface(
          shape = RoundedCornerShape(MioRadii.md),
          color = if (selected) MioColors.BurgundySurface else MioColors.PlumBase,
          border = BorderStroke(1.dp, if (selected) mood.accentColor else MioColors.SubtleBorder),
          modifier = Modifier
            .heightIn(min = MioSpacing.minTouchTarget)
            .clip(RoundedCornerShape(MioRadii.md))
            .clickable { onSelectMood(mood) }
            .testTag("mood_cat_${mood.id}"),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = MioSpacing.sm, vertical = MioSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            MoodCatArtwork(mood = mood, size = 36.dp)
            Spacer(Modifier.height(2.dp))
            Text(
              text = mood.label,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) mood.accentColor else MioColors.WarmOffWhite,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MessageComposerBar(
  text: String,
  selectedMood: CatMood?,
  moodPickerOpen: Boolean,
  onTextChanged: (String) -> Unit,
  onToggleMoodPicker: () -> Unit,
  onSend: () -> Unit,
) {
  val canSend = text.trim().isNotEmpty() || selectedMood != null
  Surface(
    color = MioColors.MidnightSlate,
    border = BorderStroke(1.dp, MioColors.SlateBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      if (text.length >= MioConstants.MESSAGE_COUNTER_THRESHOLD) {
        Text(
          text = "${text.length} / ${MioConstants.MAX_MESSAGE_LENGTH}",
          style = MaterialTheme.typography.labelSmall,
          color = if (text.length >= MioConstants.MAX_MESSAGE_LENGTH) MioColors.DangerEmber else MioColors.MutedRose,
          modifier = Modifier
            .align(Alignment.End)
            .padding(top = 4.dp, end = MioSpacing.md),
        )
      }
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MioSpacing.sm, vertical = MioSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
      ) {
        IconButton(
          onClick = onToggleMoodPicker,
          modifier = Modifier.testTag("toggle_mood_picker_button"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Mood,
            contentDescription = "Choose mood cat",
            tint = if (moodPickerOpen || selectedMood != null) MioColors.BlushRose else MioColors.MutedRose,
          )
        }

        Surface(
          shape = RoundedCornerShape(MioRadii.lg),
          color = MioColors.PlumBase,
          border = BorderStroke(1.dp, MioColors.SubtleBorder),
          modifier = Modifier.weight(1f),
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = MioSpacing.md, vertical = 12.dp),
          ) {
            if (text.isEmpty()) {
              Text(
                text = if (selectedMood != null) "Add note to ${selectedMood.label} cat..." else MioCopy.COMPOSER_HINT,
                style = MaterialTheme.typography.bodyLarge,
                color = MioColors.MutedRose,
              )
            }
            BasicTextField(
              value = text,
              onValueChange = onTextChanged,
              textStyle = MaterialTheme.typography.bodyLarge.copy(color = MioColors.WarmOffWhite),
              cursorBrush = SolidColor(MioColors.BlushRose),
              maxLines = 4,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("message_composer_input"),
            )
          }
        }

        Box(
          modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (canSend) MioGradients.sendButton else SolidColor(MioColors.BurgundySurface))
            .clickable(enabled = canSend, onClick = onSend)
            .testTag("send_message_button"),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Send,
            contentDescription = "Send",
            tint = if (canSend) MioColors.WarmOffWhite else MioColors.MutedRose,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ActionSheetItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tint: androidx.compose.ui.graphics.Color = MioColors.WarmOffWhite,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = MioSpacing.minTouchTarget)
      .clip(RoundedCornerShape(MioRadii.md))
      .clickable(onClick = onClick)
      .padding(horizontal = MioSpacing.sm, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
  ) {
    Icon(imageVector = icon, contentDescription = label, tint = tint)
    Text(text = label, style = MaterialTheme.typography.bodyLarge, color = tint)
  }
}

private fun formatMessageDayTime(timeMs: Long): String {
  return SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(timeMs))
}
