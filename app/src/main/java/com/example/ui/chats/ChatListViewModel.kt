package com.example.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.config.MioCopy
import com.example.core.feedback.ActiveChatTracker
import com.example.core.feedback.MioNotificationHelper
import com.example.data.local.MioPreferences
import com.example.data.model.ChatRequestStatus
import com.example.data.model.ChatThreadPreview
import com.example.data.model.PresenceStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UsernameClaim
import com.example.data.remote.ChatService
import com.example.data.remote.PresenceService
import com.example.data.remote.toHumanMessage
import com.example.data.remote.unwrapFirestoreError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModel(
  private val chatService: ChatService,
  private val presenceService: PresenceService,
  private val notificationHelper: MioNotificationHelper,
  private val preferences: MioPreferences,
  private val currentUserId: String,
) : ViewModel() {

  private val lastNotifiedTimestamps = mutableMapOf<String, Long>()
  private var initialLoadDone = false

  init {
    viewModelScope.launch {
      chatService.cleanupExpiredRelayDocs(currentUserId)
    }
  }

  private val baseChatsFlow = combine(
    chatService.observeChats(currentUserId),
    preferences.observeClearedChatTimestamps(currentUserId),
  ) { chats, clearedMap ->
    val adjusted = chats.map { chat ->
      val clearedAt = clearedMap[chat.chatId] ?: 0L
      if (
        clearedAt > 0L &&
        chat.status == ChatRequestStatus.ACCEPTED &&
        chat.lastMessageTimeMs <= clearedAt
      ) {
        chat.copy(
          unreadCount = 0,
          lastMessagePreview = MioCopy.EMPTY_THREAD_PREVIEW,
          lastMessageSenderUid = "",
          lastMessageStatus = null,
          lastMessageTimeMs = 0L,
        )
      } else {
        chat
      }
    }
    notifyIfNewIncomingMessage(adjusted)
    adjusted
  }

  private val rtdbOverlayFlow = baseChatsFlow
    .map { chats ->
      chats
        .filter { it.status == ChatRequestStatus.ACCEPTED && !it.isBlockedAny }
        .map { it.chatId to it.partnerUid }
        .sortedBy { it.first }
    }
    .distinctUntilChanged()
    .flatMapLatest { activePairs ->
      if (activePairs.isEmpty()) {
        flowOf(emptyMap<String, Pair<PresenceStatus?, Boolean>>())
      } else {
        combine(
          activePairs.map { (chatId, partnerUid) ->
            combine(
              presenceService.observeUserStatus(partnerUid),
              presenceService.observeTyping(chatId, partnerUid),
            ) { status, typing ->
              chatId to (status to typing)
            }
          },
        ) { entries ->
          entries.toMap()
        }
      }
    }

  val chatsState: StateFlow<UiState<List<ChatThreadPreview>>> = combine(
    baseChatsFlow,
    rtdbOverlayFlow,
  ) { chats, overlayMap ->
    val enriched = chats.map { chat ->
      val overlay = overlayMap[chat.chatId]
      if (overlay != null && chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny) {
        val status = overlay.first
        val typing = overlay.second
        chat.copy(
          partnerTyping = typing,
          partnerOnline = status?.isOnline == true,
          partnerLastActiveMs = status?.lastActiveMs ?: 0L,
        )
      } else {
        chat.copy(
          partnerTyping = false,
          partnerOnline = false,
          partnerLastActiveMs = 0L,
        )
      }
    }
    UiState.Success(enriched) as UiState<List<ChatThreadPreview>>
  }
    .catch { error ->
      val root = unwrapFirestoreError(error) ?: throw error
      emit(UiState.Error(toHumanMessage(root, "Unable to load conversations.")))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = UiState.Loading,
    )

  private val _searchResult = MutableStateFlow<UsernameClaim?>(null)
  val searchResult: StateFlow<UsernameClaim?> = _searchResult.asStateFlow()

  private val _searchError = MutableStateFlow<String?>(null)
  val searchError: StateFlow<String?> = _searchError.asStateFlow()

  private val _isSearching = MutableStateFlow(false)
  val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

  private val _actionMessage = MutableStateFlow<String?>(null)
  val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

  fun clearSearch() {
    _searchResult.value = null
    _searchError.value = null
  }

  fun clearActionMessage() {
    _actionMessage.value = null
  }

  fun searchExactUsername(query: String) {
    viewModelScope.launch {
      _isSearching.value = true
      _searchError.value = null
      _searchResult.value = null
      val res = chatService.findUserByExactUsername(query, currentUserId)
      res.onSuccess { found ->
        if (found == null) {
          _searchError.value = MioCopy.SEARCH_NEUTRAL_NOT_FOUND
        } else {
          _searchResult.value = found
        }
      }.onFailure { err ->
        _searchError.value = toHumanMessage(err, "Search failed. Please try again.")
      }
      _isSearching.value = false
    }
  }

  fun sendChatRequest(myProfile: UserProfile, target: UsernameClaim, onCreated: (String) -> Unit) {
    viewModelScope.launch {
      val res = chatService.sendChatRequest(myProfile, target)
      res.onSuccess { chatId ->
        clearSearch()
        onCreated(chatId)
      }.onFailure { err ->
        _actionMessage.value = toHumanMessage(err, "Could not send chat invitation.")
      }
    }
  }

  fun respondToRequest(chatId: String, accept: Boolean) {
    viewModelScope.launch {
      chatService.respondToChatRequest(chatId, accept)
        .onFailure { err ->
          _actionMessage.value = toHumanMessage(err, "Could not update invitation.")
        }
    }
  }

  fun clearChatHistory(chat: ChatThreadPreview) {
    viewModelScope.launch {
      val watermark = maxOf(chat.lastMessageTimeMs, 1L)
      preferences.setChatClearedAt(currentUserId, chat.chatId, watermark)
      chatService.clearChatMessages(chat.chatId, currentUserId)
        .onSuccess {
          // Deleted after confirmation without displaying banner message
        }
        .onFailure { err ->
          _actionMessage.value = toHumanMessage(err, "Could not delete chat history.")
        }
    }
  }

  private suspend fun notifyIfNewIncomingMessage(chats: List<ChatThreadPreview>) {
    if (!initialLoadDone) {
      chats.forEach { lastNotifiedTimestamps[it.chatId] = it.lastMessageTimeMs }
      initialLoadDone = true
      return
    }
    val settings = preferences.settings.first()
    val notificationsAllowed = settings.notificationsEnabled &&
      settings.muteAllUntilMs <= System.currentTimeMillis()

    for (chat in chats) {
      if (chat.unreadCount == 0) {
        notificationHelper.onChatOpened(chat.chatId)
      }

      val prevTime = lastNotifiedTimestamps[chat.chatId] ?: 0L
      if (chat.lastMessageTimeMs > prevTime) {
        lastNotifiedTimestamps[chat.chatId] = chat.lastMessageTimeMs
        val isIncomingUnread = chat.unreadCount > 0 &&
          chat.lastMessageSenderUid.isNotBlank() &&
          chat.lastMessageSenderUid != currentUserId
        if (
          notificationsAllowed &&
          isIncomingUnread &&
          !ActiveChatTracker.isActivelyViewingChat(chat.chatId)
        ) {
          notificationHelper.showIncomingChatMessage(
            chatId = chat.chatId,
            senderDisplayName = chat.partnerDisplayName,
            senderUsername = chat.partnerUsername,
            messageTimestampMs = chat.lastMessageTimeMs,
            isFromFcm = false,
          )
        }
      }
    }
  }
}
