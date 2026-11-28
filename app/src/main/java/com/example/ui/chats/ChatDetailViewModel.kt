package com.example.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.CatMood
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.core.feedback.MoodFeedbackManager
import com.example.data.local.UserSettingsStore
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRequestStatus
import com.example.data.model.ChatThreadPreview
import com.example.data.model.MessageContent
import com.example.data.model.MessageStatus
import com.example.data.model.PresenceStatus
import com.example.data.model.ReplyQuote
import com.example.data.model.ReportReason
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.remote.ChatService
import com.example.data.remote.MipService
import com.example.data.remote.PresenceService
import com.example.data.remote.toHumanMessage
import com.example.data.remote.unwrapFirestoreError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatDetailViewModel(
  private val chatService: ChatService,
  private val presenceService: PresenceService,
  private val mipService: MipService,
  private val moodFeedbackManager: MoodFeedbackManager?,
  private val preferences: UserSettingsStore,
  private val chatId: String,
  private val currentUserId: String,
) : ViewModel() {

  private var typingJob: Job? = null
  private var lastTypingWriteMs: Long = 0L
  private var isTypingSent: Boolean = false
  private var lastSendAtMs: Long = 0L
  private val lastMarkedReadIds = mutableSetOf<String>()
  private var lastMarkedUnreadCount: Int = -1
  private val seenMoodIds = mutableSetOf<String>()
  private var initialMessagesSynced = false

  private val baseChatFlow: Flow<ChatThreadPreview?> = chatService.observeChat(chatId, currentUserId)

  init {
    viewModelScope.launch {
      baseChatFlow
        .map { chat ->
          if (
            chat != null &&
            chat.lastMessageSenderUid == currentUserId &&
            (chat.lastMessageStatus == MessageStatus.DELIVERED || chat.lastMessageStatus == MessageStatus.READ)
          ) {
            Triple(chat.chatId, chat.lastMessageStatus, chat.lastMessageTimeMs)
          } else {
            null
          }
        }
        .distinctUntilChanged()
        .catch { /* Handled by chatState */ }
        .collect { receipt ->
          if (receipt != null) {
            chatService.syncOutgoingReceiptStatus(
              chatId = receipt.first,
              myUid = currentUserId,
              lastMessageStatus = receipt.second,
              lastMessageTimeMs = receipt.third,
            )
          }
        }
    }
  }

  private val rtdbOverlayFlow: Flow<Pair<PresenceStatus?, Boolean>> = baseChatFlow
    .map { chat ->
      if (chat != null && chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny) {
        chat.chatId to chat.partnerUid
      } else {
        null
      }
    }
    .distinctUntilChanged()
    .flatMapLatest { pair ->
      if (pair == null) {
        flowOf(null to false)
      } else {
        combine(
          presenceService.observeUserStatus(pair.second),
          presenceService.observeTyping(pair.first, pair.second),
        ) { status, typing -> status to typing }
      }
    }

  private val firestoreTypingFlow: Flow<Boolean> = baseChatFlow
    .map { chat ->
      chat != null &&
        chat.status == ChatRequestStatus.ACCEPTED &&
        !chat.isBlockedAny &&
        chat.partnerTyping
    }
    .distinctUntilChanged()
    .flatMapLatest { isTyping ->
      if (!isTyping) {
        flowOf(false)
      } else {
        flow {
          emit(true)
          delay(MioConstants.TYPING_STALE_TIMEOUT_MS)
          emit(false)
        }
      }
    }

  val chatState: StateFlow<UiState<ChatThreadPreview?>> = combine(
    baseChatFlow,
    rtdbOverlayFlow,
    firestoreTypingFlow,
  ) { chat, (presence, rtdbTyping), fsTyping ->
    val enriched = chat?.let {
      if (it.status == ChatRequestStatus.ACCEPTED && !it.isBlockedAny) {
        it.copy(
          partnerTyping = fsTyping || rtdbTyping,
          partnerOnline = presence?.isOnline == true,
          partnerLastActiveMs = presence?.lastActiveMs ?: 0L,
        )
      } else {
        it.copy(
          partnerTyping = false,
          partnerOnline = false,
          partnerLastActiveMs = 0L,
        )
      }
    }
    UiState.Success(enriched) as UiState<ChatThreadPreview?>
  }
    .catch { error ->
      val root = unwrapFirestoreError(error) ?: throw error
      emit(UiState.Error(toHumanMessage(root, "Unable to open conversation.")))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Loading,
    )

  val messagesState: StateFlow<UiState<List<ChatMessage>>> = chatState
    .map { state ->
      (state as? UiState.Success)?.data
    }
    .distinctUntilChanged { old, new ->
      old?.chatId == new?.chatId &&
        old?.partnerPublicKey == new?.partnerPublicKey &&
        old?.partnerDisplayName == new?.partnerDisplayName &&
        old?.unreadCount == new?.unreadCount &&
        old?.lastMessageSenderUid == new?.lastMessageSenderUid
    }
    .flatMapLatest { chat ->
      if (chat == null) {
        flowOf(UiState.Success(emptyList()))
      } else {
        chatService.observeMessages(
          chatId = chat.chatId,
          myUid = currentUserId,
          partnerDisplayName = chat.partnerDisplayName,
          partnerPublicKey = chat.partnerPublicKey,
        ).map<List<ChatMessage>, UiState<List<ChatMessage>>> { messages ->
          handleIncomingEffects(chat, messages)
          UiState.Success(messages)
        }.catch { error ->
          val root = unwrapFirestoreError(error) ?: throw error
          emit(UiState.Error(toHumanMessage(root, "Unable to load messages.")))
        }
      }
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Loading,
    )

  private val _replyQuote = MutableStateFlow<ReplyQuote?>(null)
  val replyQuote: StateFlow<ReplyQuote?> = _replyQuote.asStateFlow()

  private val _selectedMood = MutableStateFlow<CatMood?>(null)
  val selectedMood: StateFlow<CatMood?> = _selectedMood.asStateFlow()

  private val _bannerNotice = MutableStateFlow<String?>(null)
  val bannerNotice: StateFlow<String?> = _bannerNotice.asStateFlow()

  fun clearBannerNotice() {
    _bannerNotice.value = null
  }

  fun setReplyQuote(message: ChatMessage, myDisplayName: String, partnerDisplayName: String) {
    val senderName = if (message.senderUid == currentUserId) myDisplayName else partnerDisplayName
    val snippet = when (val c = message.content) {
      is MessageContent.Text -> c.body
      is MessageContent.Mood -> if (c.caption.isNotBlank()) "${c.mood.label} cat · ${c.caption}" else "${c.mood.label} mood cat"
    }
    _replyQuote.value = ReplyQuote(
      messageId = message.id,
      senderName = senderName,
      snippet = snippet.take(90),
    )
    moodFeedbackManager?.playLightTick()
  }

  fun clearReplyQuote() {
    _replyQuote.value = null
  }

  fun selectMood(mood: CatMood?) {
    _selectedMood.value = mood
    if (mood != null) {
      moodFeedbackManager?.playLightTick()
    }
  }

  fun clearTypingIndicator() {
    typingJob?.cancel()
    if (isTypingSent) {
      isTypingSent = false
      viewModelScope.launch {
        chatService.setTyping(chatId, currentUserId, false)
        presenceService.setTyping(chatId, currentUserId, false)
      }
    }
  }

  fun onComposerTextChanged(text: String) {
    typingJob?.cancel()
    if (text.isBlank()) {
      if (isTypingSent) {
        isTypingSent = false
        viewModelScope.launch {
          chatService.setTyping(chatId, currentUserId, false)
          presenceService.setTyping(chatId, currentUserId, false)
        }
      }
      return
    }
    val now = System.currentTimeMillis()
    if (!isTypingSent || (now - lastTypingWriteMs) >= MioConstants.TYPING_THROTTLE_MS) {
      lastTypingWriteMs = now
      isTypingSent = true
      viewModelScope.launch {
        val allowed = runCatching { preferences.settings.first().typingIndicatorEnabled }.getOrDefault(true)
        if (!allowed) {
          isTypingSent = false
          return@launch
        }
        chatService.setTyping(chatId, currentUserId, true)
        presenceService.setTyping(chatId, currentUserId, true)
      }
    }
    typingJob = viewModelScope.launch {
      delay(MioConstants.TYPING_IDLE_TIMEOUT_MS)
      isTypingSent = false
      chatService.setTyping(chatId, currentUserId, false)
      presenceService.setTyping(chatId, currentUserId, false)
    }
  }

  fun sendMessage(chat: ChatThreadPreview, myProfile: UserProfile, rawText: String): Boolean {
    val mood = _selectedMood.value
    val trimmed = rawText.trim()
    if (mood == null && trimmed.isEmpty()) return false

    val now = System.currentTimeMillis()
    if (now - lastSendAtMs < MioConstants.SEND_DEBOUNCE_MS) return false
    lastSendAtMs = now

    val content = if (mood != null) {
      MessageContent.Mood(mood, trimmed)
    } else {
      MessageContent.Text(trimmed)
    }
    val quote = _replyQuote.value
    _replyQuote.value = null
    _selectedMood.value = null
    typingJob?.cancel()
    val wasTyping = isTypingSent
    isTypingSent = false

    viewModelScope.launch {
      if (wasTyping) {
        chatService.setTyping(chatId, currentUserId, false)
        presenceService.setTyping(chatId, currentUserId, false)
      }
      chatService.sendMessage(chat, myProfile, content, quote)
        .onSuccess {
          preferences.setChatClearedAt(currentUserId, chat.chatId, 0L)
        }
        .onFailure { err ->
          _bannerNotice.value = toHumanMessage(err, "Message could not be sent.")
        }
    }
    return true
  }

  fun retryFailedMessage(chat: ChatThreadPreview, myProfile: UserProfile, messageId: String) {
    viewModelScope.launch {
      chatService.retryFailedMessage(chat, myProfile, messageId)
        .onFailure { err ->
          _bannerNotice.value = toHumanMessage(err, "Retry failed. Check your connection.")
        }
    }
  }

  fun toggleReaction(messageId: String, emoji: String) {
    moodFeedbackManager?.playLightTick()
    viewModelScope.launch {
      chatService.toggleReaction(chatId, messageId, currentUserId, emoji)
        .onFailure { err ->
          _bannerNotice.value = toHumanMessage(err, "Could not update reaction.")
        }
    }
  }

  fun deleteForMe(messageId: String) {
    viewModelScope.launch {
      chatService.deleteMessageForMe(chatId, messageId, currentUserId)
        .onFailure { err ->
          _bannerNotice.value = toHumanMessage(err, "Could not delete message.")
        }
    }
  }

  fun deleteForEveryone(messageId: String) {
    viewModelScope.launch {
      chatService.deleteMessageForEveryone(chatId, messageId, currentUserId)
        .onFailure { err ->
          _bannerNotice.value = toHumanMessage(err, "Could not delete for everyone.")
        }
    }
  }

  fun clearChatHistory(chat: ChatThreadPreview) {
    viewModelScope.launch {
      val watermark = maxOf(chat.lastMessageTimeMs, 1L)
      preferences.setChatClearedAt(currentUserId, chat.chatId, watermark)
      chatService.clearChatMessages(chat.chatId, currentUserId)
        .onSuccess {
          lastMarkedReadIds.clear()
          seenMoodIds.clear()
          _replyQuote.value = null
        }
        .onFailure { err ->
          _bannerNotice.value = toHumanMessage(err, "Could not delete chat history.")
        }
    }
  }

  fun blockPartner(chat: ChatThreadPreview) {
    viewModelScope.launch {
      chatService.blockUser(chat.chatId, currentUserId, chat.partnerUid, chat.partnerUsername)
        .onSuccess { _bannerNotice.value = "Blocked @${chat.partnerUsername}." }
        .onFailure { err -> _bannerNotice.value = toHumanMessage(err, "Could not block user.") }
    }
  }

  fun unblockPartner(chat: ChatThreadPreview) {
    viewModelScope.launch {
      chatService.unblockUser(chat.chatId, currentUserId, chat.partnerUid)
        .onSuccess { _bannerNotice.value = "Unblocked @${chat.partnerUsername}." }
        .onFailure { err -> _bannerNotice.value = toHumanMessage(err, "Could not unblock user.") }
    }
  }

  fun reportPartner(chat: ChatThreadPreview, reason: ReportReason, excerpt: String?) {
    viewModelScope.launch {
      chatService.submitReport(currentUserId, chat.partnerUid, chat.chatId, reason, excerpt)
        .onSuccess { _bannerNotice.value = "Report submitted. Thank you for keeping Mio safe." }
        .onFailure { err -> _bannerNotice.value = toHumanMessage(err, "Could not submit report.") }
    }
  }

  fun sendMipLetter(chat: ChatThreadPreview, myProfile: UserProfile, note: String, onSuccess: () -> Unit) {
    viewModelScope.launch {
      mipService.sendMipLetter(chat, myProfile, note)
        .onSuccess {
          _bannerNotice.value = "Sealed MIP letter sent to ${chat.partnerDisplayName}."
          onSuccess()
        }
        .onFailure { err ->
          _bannerNotice.value = toHumanMessage(err, "Could not send MIP letter.")
        }
    }
  }

  fun respondToMip(chat: ChatThreadPreview, myProfile: UserProfile, accept: Boolean) {
    viewModelScope.launch {
      mipService.respondToMipLetter(chat.chatId, myProfile, chat.partnerUid, accept)
        .onSuccess {
          _bannerNotice.value = if (accept) {
            "${chat.partnerDisplayName} is now your MIP."
          } else {
            "MIP invitation declined."
          }
        }
        .onFailure { err ->
          _bannerNotice.value = toHumanMessage(err, "Could not respond to MIP letter.")
        }
    }
  }

  fun respondToChatInvite(accept: Boolean) {
    viewModelScope.launch {
      chatService.respondToChatRequest(chatId, accept)
        .onFailure { err -> _bannerNotice.value = toHumanMessage(err, "Could not update request.") }
    }
  }

  private suspend fun handleIncomingEffects(chat: ChatThreadPreview, messages: List<ChatMessage>) {
    // 1. Mark unread incoming messages as read once per unread batch
    val unreadIds = messages
      .filter { it.recipientUid == currentUserId && it.status != MessageStatus.READ }
      .map { it.id }

    val newUnreadIds = unreadIds.filter { it !in lastMarkedReadIds }
    val needsCounterClear = chat.unreadCount > 0 && lastMarkedUnreadCount != chat.unreadCount
    if (newUnreadIds.isNotEmpty() || needsCounterClear) {
      lastMarkedReadIds.addAll(newUnreadIds)
      lastMarkedUnreadCount = if (chat.unreadCount > 0) 0 else lastMarkedUnreadCount
      chatService.markChatAsRead(
        chatId = chat.chatId,
        myUid = currentUserId,
        unreadMessageIds = newUnreadIds,
        updateLastMessagePreviewStatus = chat.lastMessageSenderUid != currentUserId,
      )
    }

    // 2. Trigger mood cat haptic + sound for newly arriving partner mood messages
    if (!initialMessagesSynced) {
      messages.forEach { seenMoodIds.add(it.id) }
      initialMessagesSynced = true
      return
    }
    val prefs = preferences.settings.first()
    for (msg in messages) {
      if (msg.id !in seenMoodIds) {
        seenMoodIds.add(msg.id)
        if (msg.senderUid != currentUserId && !msg.deletedForEveryone && msg.content is MessageContent.Mood) {
          moodFeedbackManager?.triggerMoodFeedback(
            mood = msg.content.mood,
            hapticsEnabled = prefs.hapticsEnabled,
            soundsEnabled = prefs.soundsEnabled,
          )
        }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    typingJob?.cancel()
    if (isTypingSent) {
      isTypingSent = false
      CoroutineScope(Dispatchers.IO + NonCancellable).launch {
        chatService.setTyping(chatId, currentUserId, false)
        presenceService.setTyping(chatId, currentUserId, false)
      }
    }
  }
}
