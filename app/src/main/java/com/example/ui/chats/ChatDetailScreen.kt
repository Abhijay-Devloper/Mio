package com.example.ui.chats

import android.graphics.BitmapFactory
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.core.config.CatMood
import com.example.core.config.FeatureFlags
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.core.feedback.ActiveChatTracker
import com.example.core.feedback.MioNotificationHelper
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
  useWallpaperMode: Boolean = false,
  chatWallpaperPath: String? = null,
  onBack: () -> Unit,
  onTextChanged: (String) -> Unit,
  onClearTyping: () -> Unit = {},
  onSendMessage: (ChatThreadPreview, String) -> Boolean,
  onRetryFailedMessage: (ChatThreadPreview, String) -> Unit,
  onSetReplyQuote: (ChatMessage, String) -> Unit,
  onClearReplyQuote: () -> Unit,
  onSelectMood: (CatMood?) -> Unit,
  onToggleReaction: (String, String) -> Unit,
  onDeleteForMe: (String) -> Unit,
  onDeleteForEveryone: (String) -> Unit,
  onClearChat: (ChatThreadPreview) -> Unit,
  onRespondToChatInvite: (Boolean) -> Unit,
  onBlockPartner: (ChatThreadPreview) -> Unit,
  onUnblockPartner: (ChatThreadPreview) -> Unit,
  onReportPartner: (ChatThreadPreview, ReportReason, String?) -> Unit,
  onSendMipLetter: (ChatThreadPreview, String, () -> Unit) -> Unit,
  onRespondToMip: (ChatThreadPreview, Boolean) -> Unit,
  onStartAudioCall: (ChatThreadPreview) -> Unit = {},
  onStartVideoCall: (ChatThreadPreview) -> Unit = {},
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
  var showClearChatDialog by rememberSaveable { mutableStateOf(false) }
  var reportExcerpt by rememberSaveable { mutableStateOf("") }
  var showComposeMipDialog by rememberSaveable { mutableStateOf(false) }
  var showSealRevealDialog by rememberSaveable { mutableStateOf(false) }

  val wallpaperBitmap = remember(chatWallpaperPath) {
    if (!chatWallpaperPath.isNullOrBlank()) {
      runCatching {
        val file = File(chatWallpaperPath)
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
      }.getOrNull()
    } else null
  }

  val context = LocalContext.current
  val chat = (chatState as? UiState.Success)?.data
  val chatId = chat?.chatId ?: ""
  val lifecycleOwner = LocalLifecycleOwner.current

  LaunchedEffect(chatId) {
    if (chatId.isNotBlank()) {
      MioNotificationHelper(context.applicationContext).onChatOpened(chatId)
    }
  }

  DisposableEffect(chatId) {
    if (chatId.isNotBlank()) {
      ActiveChatTracker.currentChatId = chatId
    }
    onDispose {
      if (ActiveChatTracker.currentChatId == chatId) {
        ActiveChatTracker.currentChatId = null
      }
    }
  }

  DisposableEffect(lifecycleOwner, chatId) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          if (chatId.isNotBlank()) {
            MioNotificationHelper(context.applicationContext).onChatOpened(chatId)
          }
        }
        Lifecycle.Event.ON_STOP -> {
          onClearTyping()
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val messages = (messagesState as? UiState.Success)?.data ?: emptyList()
  val selectedMessageForSheet = selectedMessageIdForSheet?.let { id ->
    messages.firstOrNull { it.id == id }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.lastIndex)
    }
  }

  // Root Box with Pure Black (#000000)
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MioColors.PureBlack),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(MioColors.PureBlack)
        .imePadding(),
    ) {
      // 1. Wallpaper card (custom photo or bluish gradient when opened from circle, pure black when opened from list)
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .then(
            if (useWallpaperMode) {
              Modifier.clip(
                RoundedCornerShape(
                  bottomStart = MioRadii.wallpaperCardBottom,
                  bottomEnd = MioRadii.wallpaperCardBottom,
                )
              )
            } else {
              Modifier.clip(RectangleShape)
            }
          )
          .background(MioColors.PureBlack),
      ) {
        if (useWallpaperMode) {
          if (wallpaperBitmap != null) {
            // Base background gradient matching image palette so any gaps look cohesive
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    listOf(
                      MioColors.ChatWallpaperTop,
                      MioColors.ChatWallpaperUpperMid,
                      MioColors.ChatWallpaperMid,
                      MioColors.ChatWallpaperLowerMid,
                      MioColors.ChatWallpaperBottom,
                    )
                  )
                )
            )

            // Blurred backdrop layer for soft ambient extension if image is short
            androidx.compose.foundation.Image(
              bitmap = wallpaperBitmap.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f)
                .then(
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(28.dp)
                  } else {
                    Modifier
                  }
                ),
            )

            // Main crisp wallpaper image
            androidx.compose.foundation.Image(
              bitmap = wallpaperBitmap.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )

            // Upper blur / soft vignette: fades top into soft dark/translucent so header & status bar are crystal clear
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      MioColors.PureBlack.copy(alpha = 0.70f),
                      MioColors.PureBlack.copy(alpha = 0.35f),
                      Color.Transparent,
                    )
                  )
                )
            )

            // Lower blur / soft vignette: fades bottom into pure black so if image is shorter it dissolves into pure black
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      Color.Transparent,
                      MioColors.PureBlack.copy(alpha = 0.35f),
                      MioColors.PureBlack.copy(alpha = 0.75f),
                      MioColors.PureBlack,
                    )
                  )
                )
            )
          } else {
            // Default bluish gradient wallpaper if no custom photo is chosen
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      MioColors.ChatWallpaperTop,
                      MioColors.ChatWallpaperUpperMid,
                      MioColors.ChatWallpaperMid,
                      MioColors.ChatWallpaperLowerMid,
                      MioColors.ChatWallpaperBottom,
                    ),
                  )
                )
            )

            // Upper soft vignette
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      MioColors.PureBlack.copy(alpha = 0.35f),
                      Color.Transparent,
                    )
                  )
                )
            )

            // Lower soft vignette transitioning to pure black
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.BottomCenter)
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      Color.Transparent,
                      MioColors.PureBlack.copy(alpha = 0.45f),
                      MioColors.PureBlack.copy(alpha = 0.85f),
                    )
                  )
                )
            )
          }
        } else {
          // Pure Black mode (opened from chat list)
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(MioColors.PureBlack)
          )
        }
        Column(modifier = Modifier.fillMaxSize()) {
          // Replicated Header matching Image 1
          ChatTopHeaderBar(
            chat = chat,
            useWallpaperMode = useWallpaperMode,
            onBack = onBack,
            onStartAudioCall = { chat?.let(onStartAudioCall) },
            onOpenProfileOrActions = {
              if (chat != null && (chat.mipStatus == MipStatus.PENDING || chat.mipStatus == MipStatus.ACCEPTED)) {
                showSealRevealDialog = true
              } else {
                showOverflowMenu = true
              }
            },
            onOpenOverflowMenu = { showOverflowMenu = true },
            showOverflowMenu = showOverflowMenu,
            onDismissOverflowMenu = { showOverflowMenu = false },
            onDeleteChats = { showClearChatDialog = true },
            onToggleBlock = {
              if (chat != null) {
                if (chat.blockedByMe) onUnblockPartner(chat) else onBlockPartner(chat)
              }
            },
            onReport = {
              reportExcerpt = ""
              showReportDialog = true
            },
            onOpenMipCompose = { showComposeMipDialog = true },
            onStartVideoCall = { chat?.let(onStartVideoCall) },
          )

          if (bannerNotice != null) {
            Surface(
              color = if (useWallpaperMode) MioColors.WallpaperHeaderPill else MioColors.CardSurface,
              border = BorderStroke(1.dp, MioColors.SubtleBorder),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { onDismissBanner() },
            ) {
              Text(
                text = bannerNotice,
                style = MaterialTheme.typography.bodyMedium,
                color = MioColors.PrimaryText,
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
                if (chat.status == ChatRequestStatus.PENDING) {
                  PendingChatBanner(
                    chat = chat,
                    myUid = myProfile.uid,
                    useWallpaperMode = useWallpaperMode,
                    onAccept = { onRespondToChatInvite(true) },
                    onDecline = { onRespondToChatInvite(false) },
                  )
                } else if (chat.status == ChatRequestStatus.DECLINED) {
                  Surface(
                    color = if (useWallpaperMode) MioColors.WallpaperHeaderPill else MioColors.CardSurface,
                    border = BorderStroke(1.dp, MioColors.SubtleBorder),
                    shape = RoundedCornerShape(MioRadii.md),
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(MioSpacing.sm),
                  ) {
                    Text(
                      text = "This chat invitation was declined.",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MioColors.MutedText,
                      modifier = Modifier.padding(MioSpacing.md),
                    )
                  }
                } else if (chat.isBlockedAny) {
                  Surface(
                    color = MioColors.DangerEmber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.5f)),
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
                    color = if (useWallpaperMode) MioColors.WallpaperHeaderPill else MioColors.CardSurface,
                    border = BorderStroke(1.dp, MioColors.RoseAccent.copy(alpha = 0.6f)),
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
                          color = MioColors.PrimaryText,
                        )
                        Text(
                          text = "Tap to break the wax seal",
                          style = MaterialTheme.typography.labelMedium,
                          color = MioColors.RoseAccent,
                        )
                      }
                    }
                  }
                }

                // Messages LazyColumn inside the wallpaper card
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
                          verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (useWallpaperMode) MioColors.BubbleTimestamp else MioColors.MutedText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                  .fillMaxWidth()
                                  .padding(vertical = MioSpacing.xs),
                              )
                            }

                            SwipeableMessageRow(
                              message = message,
                              isMine = isMine,
                              showStatusLabel = showStatusLabel,
                              useWallpaperMode = useWallpaperMode,
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
              }
            }
          }
        }
      }

      // 2. Pure Black (#000000) Bottom Composer Dock
      if (chat != null && chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(MioColors.PureBlack)
            .navigationBarsPadding(),
        ) {
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

  // Long-press / Double-tap Emoji Reaction & Action Sheet
  selectedMessageForSheet?.let { targetMsg ->
    val isMine = targetMsg.senderUid == myProfile.uid
    val canReactAndReply = !targetMsg.deletedForEveryone && targetMsg.status != MessageStatus.FAILED
    ModalBottomSheet(
      onDismissRequest = { selectedMessageIdForSheet = null },
      containerColor = MioColors.CardSurface,
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
            color = MioColors.MutedText,
          )
          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
            modifier = Modifier.fillMaxWidth(),
          ) {
            itemsIndexed(MioConstants.QUICK_REACTIONS) { _, emoji ->
              val isSelected = targetMsg.reactions[myProfile.uid] == emoji
              Surface(
                shape = CircleShape,
                color = if (isSelected) MioColors.RoseAccent else MioColors.PureBlack,
                border = BorderStroke(
                  1.dp,
                  if (isSelected) MioColors.RoseAccent else MioColors.SubtleBorder,
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
      containerColor = MioColors.CardSurface,
      title = {
        Text("Seal a MIP Letter", style = MaterialTheme.typography.headlineMedium, color = MioColors.PrimaryText)
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
            color = MioColors.MutedText,
          )
          OutlinedTextField(
            value = mipNote,
            onValueChange = { mipNote = it.take(MioConstants.MAX_MIP_NOTE_LENGTH) },
            placeholder = { Text("Write a short private note...") },
            minLines = 3,
            maxLines = 5,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MioColors.RoseAccent,
              unfocusedBorderColor = MioColors.SubtleBorder,
              focusedTextColor = MioColors.PrimaryText,
              unfocusedTextColor = MioColors.PrimaryText,
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
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
          modifier = Modifier
            .heightIn(min = MioSpacing.minTouchTarget)
            .testTag("send_mip_letter_button"),
        ) {
          Text("Seal & Send", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showComposeMipDialog = false },
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Cancel", color = MioColors.MutedText)
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
      containerColor = MioColors.CardSurface,
      title = { Text("Report @${chat.partnerUsername}", color = MioColors.DangerEmber) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(MioSpacing.xs)) {
          ReportReason.entries.forEach { reason ->
            val selected = reason == selectedReason
            Surface(
              shape = RoundedCornerShape(MioRadii.sm),
              color = if (selected) MioColors.PureBlack else MioColors.CardSurface,
              border = BorderStroke(1.dp, if (selected) MioColors.RoseAccent else MioColors.SubtleBorder),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MioSpacing.minTouchTarget)
                .clickable { selectedReason = reason },
            ) {
              Text(
                text = reason.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MioColors.PrimaryText,
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
              focusedBorderColor = MioColors.RoseAccent,
              unfocusedBorderColor = MioColors.SubtleBorder,
              focusedTextColor = MioColors.PrimaryText,
              unfocusedTextColor = MioColors.PrimaryText,
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
            tint = MioColors.PureBlack,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(MioSpacing.xxs))
          Text("Submit Report", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showReportDialog = false },
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Cancel", color = MioColors.MutedText)
        }
      },
    )
  }

  if (showClearChatDialog && chat != null) {
    AlertDialog(
      onDismissRequest = { showClearChatDialog = false },
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
            showClearChatDialog = false
            onClearChat(chat)
          },
          colors = ButtonDefaults.buttonColors(containerColor = MioColors.DangerEmber),
          modifier = Modifier
            .heightIn(min = MioSpacing.minTouchTarget)
            .testTag("confirm_delete_chats_button"),
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
          onClick = { showClearChatDialog = false },
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Cancel", color = MioColors.MutedText)
        }
      },
    )
  }
}

/**
 * Replicated Top Header Bar matching Image 1:
 * Back arrow, circular avatar, bold 2-line name ("James\nVetrovs"), and two squircle action buttons.
 */
@Composable
private fun ChatTopHeaderBar(
  chat: ChatThreadPreview?,
  useWallpaperMode: Boolean,
  onBack: () -> Unit,
  onStartAudioCall: () -> Unit,
  onOpenProfileOrActions: () -> Unit,
  onOpenOverflowMenu: () -> Unit,
  showOverflowMenu: Boolean,
  onDismissOverflowMenu: () -> Unit,
  onDeleteChats: () -> Unit,
  onToggleBlock: () -> Unit,
  onReport: () -> Unit,
  onOpenMipCompose: () -> Unit,
  onStartVideoCall: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Back arrow button
    IconButton(
      onClick = onBack,
      modifier = Modifier
        .size(40.dp)
        .testTag("chat_back_button"),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
        contentDescription = "Back",
        tint = Color.White,
        modifier = Modifier.size(22.dp),
      )
    }

    Spacer(Modifier.width(4.dp))

    // Circular Avatar
    Box(
      modifier = Modifier
        .size(42.dp)
        .clip(CircleShape)
        .background(MioColors.RoseAccent),
      contentAlignment = Alignment.Center,
    ) {
      if (chat != null && chat.isMipActive) {
        WaxSealEmblem(size = 32.dp)
      } else {
        Text(
          text = chat?.partnerDisplayName?.take(1)?.uppercase() ?: "?",
          style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
          ),
          color = MioColors.PureBlack,
        )
      }
    }

    Spacer(Modifier.width(12.dp))

    // Name (Bold 2 lines like "James\nVetrovs" in Image 1) & Status
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.Center,
    ) {
      if (chat != null) {
        val name = chat.partnerDisplayName
        val nameParts = name.trim().split(" ", limit = 2)
        if (nameParts.size > 1 && nameParts[0].length <= 12 && nameParts[1].length <= 12) {
          Text(
            text = "${nameParts[0]}\n${nameParts[1]}",
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp,
              lineHeight = 22.sp,
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        } else {
          Text(
            text = name,
            style = MaterialTheme.typography.titleLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 19.sp,
              lineHeight = 23.sp,
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        when {
          chat.partnerTyping && chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny -> {
            Text(
              text = MioCopy.TYPING_INDICATOR,
              style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
              color = MioColors.RoseAccent,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          chat.partnerOnline && chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(7.dp)
                  .clip(CircleShape)
                  .background(MioColors.OnlineGreen),
              )
              Text(
                text = MioCopy.ACTIVE_NOW_LABEL,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = if (useWallpaperMode) MioColors.HeaderSubtitleMuted else MioColors.MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          chat.partnerLastActiveMs > 0L && chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny -> {
            Text(
              text = formatLastActive(chat.partnerLastActiveMs),
              style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
              color = if (useWallpaperMode) MioColors.HeaderSubtitleMuted else MioColors.MutedText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          else -> {
            Text(
              text = "@${chat.partnerUsername}",
              style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
              color = if (useWallpaperMode) MioColors.HeaderSubtitleMuted else MioColors.MutedText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      } else {
        Text("Conversation", style = MaterialTheme.typography.titleLarge, color = Color.White)
      }
    }

    Spacer(Modifier.width(8.dp))

    // Right Action Buttons (Two rounded squircles matching Image 1)
    if (chat != null && chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny) {
      // 1. Call Button squircle
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (useWallpaperMode) MioColors.WallpaperHeaderPill else MioColors.PureBlackHeaderPill,
        border = BorderStroke(1.dp, if (useWallpaperMode) Color(0x33FFFFFF) else MioColors.SubtleBorder),
        modifier = Modifier
          .size(42.dp)
          .clip(RoundedCornerShape(14.dp))
          .clickable(onClick = onStartAudioCall)
          .testTag("chat_voice_call_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.Outlined.Call,
            contentDescription = MioCopy.CALL_TYPE_VOICE,
            tint = Color.White,
            modifier = Modifier.size(19.dp),
          )
        }
      }

      Spacer(Modifier.width(8.dp))

      // 2. Profile / Action Button squircle
      Box {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = if (useWallpaperMode) MioColors.WallpaperHeaderPill else MioColors.PureBlackHeaderPill,
          border = BorderStroke(1.dp, if (useWallpaperMode) Color(0x33FFFFFF) else MioColors.SubtleBorder),
          modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenProfileOrActions)
            .testTag("chat_overflow_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.AccountBox,
              contentDescription = "Contact details and options",
              tint = Color.White,
              modifier = Modifier.size(20.dp),
            )
          }
        }

        DropdownMenu(
          expanded = showOverflowMenu,
          onDismissRequest = onDismissOverflowMenu,
          containerColor = MioColors.CardSurface,
        ) {
          if (FeatureFlags.AUDIO_VIDEO_CALLS) {
            DropdownMenuItem(
              text = { Text(MioCopy.CALL_TYPE_VIDEO, color = MioColors.PrimaryText) },
              leadingIcon = {
                Icon(Icons.Outlined.Videocam, contentDescription = null, tint = MioColors.PrimaryText)
              },
              onClick = {
                onDismissOverflowMenu()
                onStartVideoCall()
              },
            )
          }
          DropdownMenuItem(
            text = {
              Text(
                if (chat.isMipActive || chat.mipStatus == MipStatus.PENDING) "View Sealed MIP Letter" else "Send MIP Letter",
                color = MioColors.RoseAccent,
              )
            },
            leadingIcon = {
              Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, tint = MioColors.RoseAccent)
            },
            onClick = {
              onDismissOverflowMenu()
              onOpenProfileOrActions()
            },
          )
          DropdownMenuItem(
            text = { Text(MioCopy.DELETE_CHATS_OPTION, color = MioColors.DangerEmber) },
            leadingIcon = {
              Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MioColors.DangerEmber)
            },
            onClick = {
              onDismissOverflowMenu()
              onDeleteChats()
            },
            modifier = Modifier.testTag("menu_delete_chats"),
          )
          DropdownMenuItem(
            text = {
              Text(
                text = if (chat.blockedByMe) "Unblock @${chat.partnerUsername}" else "Block @${chat.partnerUsername}",
                color = MioColors.DangerEmber,
              )
            },
            leadingIcon = {
              Icon(Icons.Outlined.Block, contentDescription = null, tint = MioColors.DangerEmber)
            },
            onClick = {
              onDismissOverflowMenu()
              onToggleBlock()
            },
          )
          DropdownMenuItem(
            text = { Text("Report user", color = MioColors.DangerEmber) },
            leadingIcon = {
              Icon(Icons.Outlined.Shield, contentDescription = null, tint = MioColors.DangerEmber)
            },
            onClick = {
              onDismissOverflowMenu()
              onReport()
            },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableMessageRow(
  message: ChatMessage,
  isMine: Boolean,
  showStatusLabel: Boolean,
  useWallpaperMode: Boolean,
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
        tint = MioColors.RoseAccent,
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
      Box(modifier = Modifier.widthIn(max = 310.dp)) {
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
              Modifier.background(MioColors.CardSurface.copy(alpha = 0.7f))
            } else if (isMine) {
              if (useWallpaperMode) {
                Modifier.background(MioColors.WallpaperSentBubble)
              } else {
                Modifier
                  .background(MioColors.PureBlackSentBubble)
                  .then(Modifier.background(Color(0xFF1E1E24)))
              }
            } else {
              if (useWallpaperMode) {
                Modifier.background(MioColors.WallpaperReceivedBubble)
              } else {
                Modifier.background(MioColors.PureBlackReceivedBubble)
              }
            }
          )
          .then(
            if (!useWallpaperMode && !message.deletedForEveryone) {
              Modifier
            } else {
              Modifier
            }
          )
          .combinedClickable(
            onClick = {},
            onLongClick = onOpenActions,
            onDoubleClick = { if (!message.deletedForEveryone) onOpenActions() },
          )
          .testTag("message_bubble_${message.id}")

        Column(modifier = bubbleModifier) {
          if (message.deletedForEveryone) {
            Text(
              text = MioCopy.DELETED_FOR_EVERYONE_LABEL,
              style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
              color = MioColors.MutedText,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
          } else {
            message.replyQuote?.let { quote ->
              Surface(
                shape = RoundedCornerShape(MioRadii.sm),
                color = Color.Black.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(top = 8.dp, start = 8.dp, end = 8.dp)
                  .clickable { onReplyQuoteClicked(quote.messageId) },
              ) {
                Column(modifier = Modifier.padding(horizontal = MioSpacing.xs, vertical = 6.dp)) {
                  Text(
                    text = quote.senderName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MioColors.RoseAccent,
                  )
                  Text(
                    text = quote.snippet,
                    style = MaterialTheme.typography.labelMedium,
                    color = MioColors.PrimaryText.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }

            when (val content = message.content) {
              is MessageContent.Text -> {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.Bottom,
                  horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                  Text(
                    text = content.body,
                    style = MaterialTheme.typography.bodyLarge.copy(
                      fontSize = 15.sp,
                      lineHeight = 21.sp,
                    ),
                    color = Color.White,
                    modifier = Modifier.weight(1f, fill = false),
                  )
                  Text(
                    text = formatMessageTimeOnly(message.timestampMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 11.sp,
                      color = if (useWallpaperMode) MioColors.BubbleTimestamp else MioColors.MutedText,
                    ),
                    modifier = Modifier.padding(bottom = 1.dp),
                  )
                }
              }
              is MessageContent.Mood -> {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
                  ) {
                    MoodCatArtwork(mood = content.mood, size = 44.dp)
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                      Text(
                        text = "${content.mood.label} Cat",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = content.mood.accentColor,
                      )
                      Text(
                        text = content.caption.ifBlank { content.mood.tagline },
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = Color.White,
                      )
                    }
                  }
                  Spacer(Modifier.height(4.dp))
                  Text(
                    text = formatMessageTimeOnly(message.timestampMs),
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontSize = 11.sp,
                      color = if (useWallpaperMode) MioColors.BubbleTimestamp else MioColors.MutedText,
                    ),
                    modifier = Modifier.align(Alignment.End),
                  )
                }
              }
            }
          }
        }
      }

      // Reactions row
      if (!message.deletedForEveryone && message.reactions.isNotEmpty()) {
        val grouped = message.reactions.values.groupingBy { it }.eachCount()
        Row(
          modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          grouped.forEach { (emoji, count) ->
            Surface(
              shape = RoundedCornerShape(MioRadii.pill),
              color = MioColors.CardSurface,
              border = BorderStroke(1.dp, MioColors.SubtleBorder),
              modifier = Modifier.clickable { onOpenActions() },
            ) {
              Text(
                text = if (count > 1) "$emoji $count" else emoji,
                style = MaterialTheme.typography.labelSmall,
                color = MioColors.PrimaryText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
        }
      }

      // Status receipt text
      if (showStatusLabel) {
        val isFailed = message.status == MessageStatus.FAILED
        Text(
          text = if (isFailed) "${message.status.label} · Tap to retry" else message.status.label,
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
          color = when (message.status) {
            MessageStatus.READ -> MioColors.TwilightBlue
            MessageStatus.FAILED -> MioColors.DangerEmber
            else -> if (useWallpaperMode) MioColors.BubbleTimestamp else MioColors.MutedText
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

/**
 * Replicated Message Composer Bar matching Image 1:
 * Capsule/Pill dark container with "Your Message..." and squircle send button in pure black dock.
 */
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

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(MioColors.PureBlack)
      .padding(horizontal = 16.dp, vertical = 10.dp),
  ) {
    if (text.length >= MioConstants.MESSAGE_COUNTER_THRESHOLD) {
      Text(
        text = "${text.length} / ${MioConstants.MAX_MESSAGE_LENGTH}",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        color = if (text.length >= MioConstants.MAX_MESSAGE_LENGTH) MioColors.DangerEmber else MioColors.MutedText,
        modifier = Modifier
          .align(Alignment.End)
          .padding(bottom = 4.dp, end = 4.dp),
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Main pill container matching Image 1
      Surface(
        shape = RoundedCornerShape(MioRadii.pill),
        color = MioColors.ComposerPillFill,
        border = BorderStroke(1.dp, MioColors.ComposerPillBorder),
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 52.dp),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onToggleMoodPicker,
            modifier = Modifier
              .size(28.dp)
              .testTag("toggle_mood_picker_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Mood,
              contentDescription = "Choose mood cat",
              tint = if (moodPickerOpen || selectedMood != null) MioColors.RoseAccent else Color(0xFF6E6E75),
              modifier = Modifier.size(21.dp),
            )
          }

          Spacer(Modifier.width(8.dp))

          Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
          ) {
            if (text.isEmpty()) {
              Text(
                text = if (selectedMood != null) "Add note to ${selectedMood.label} cat..." else MioCopy.COMPOSER_HINT,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = Color(0xFF6E6E75),
              )
            }
            BasicTextField(
              value = text,
              onValueChange = onTextChanged,
              textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                fontSize = 15.sp,
              ),
              cursorBrush = SolidColor(MioColors.RoseAccent),
              maxLines = 4,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("message_composer_input"),
            )
          }
        }
      }

      // Rounded squircle Send Button matching Image 1
      Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (canSend) MioColors.RoseAccent else MioColors.ComposerSendIdle,
        modifier = Modifier
          .size(52.dp)
          .clip(RoundedCornerShape(22.dp))
          .clickable(enabled = canSend, onClick = onSend)
          .testTag("send_message_button"),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Outlined.Send,
            contentDescription = "Send",
            tint = if (canSend) MioColors.PureBlack else Color(0xFF8A8A92),
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun PendingChatBanner(
  chat: ChatThreadPreview,
  myUid: String,
  useWallpaperMode: Boolean,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
) {
  Surface(
    color = if (useWallpaperMode) MioColors.WallpaperHeaderPill else MioColors.CardSurface,
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
          color = MioColors.MutedText,
        )
      } else {
        Text(
          text = "@${chat.partnerUsername} ${MioCopy.PENDING_INCOMING_BANNER}",
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.PrimaryText,
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
            Text("Decline", color = MioColors.MutedText)
          }
          Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = MioSpacing.minTouchTarget),
          ) {
            Text("Accept", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
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
    color = MioColors.CardSurface,
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
        tint = MioColors.RoseAccent,
        modifier = Modifier.size(18.dp),
      )
      Spacer(Modifier.width(MioSpacing.xs))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = "Replying to ${quote.senderName}",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = MioColors.RoseAccent,
        )
        Text(
          text = quote.snippet,
          style = MaterialTheme.typography.labelMedium,
          color = MioColors.MutedText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onClear) {
        Icon(Icons.Outlined.Close, contentDescription = "Cancel reply", tint = MioColors.MutedText)
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
    color = MioColors.PureBlack,
    border = BorderStroke(1.dp, MioColors.SubtleBorder),
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
          color = if (selected) MioColors.CardSurface else MioColors.PureBlack,
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
              color = if (selected) mood.accentColor else MioColors.PrimaryText,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ActionSheetItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  tint: Color = MioColors.PrimaryText,
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

private fun formatMessageTimeOnly(timeMs: Long): String {
  return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timeMs))
}

private fun formatMessageDayTime(timeMs: Long): String {
  return SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(timeMs))
}

private fun formatLastActive(lastActiveMs: Long): String {
  if (lastActiveMs <= 0L) return ""
  val diffMs = (System.currentTimeMillis() - lastActiveMs).coerceAtLeast(0L)
  return when {
    diffMs < 60_000L -> "Active just now"
    diffMs < 3_600_000L -> "Active ${diffMs / 60_000L}m ago"
    diffMs < 86_400_000L -> "Active ${diffMs / 3_600_000L}h ago"
    else -> "Last active ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(lastActiveMs))}"
  }
}
