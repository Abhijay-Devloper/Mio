package com.example

import androidx.compose.ui.graphics.toArgb
import com.example.core.config.CatMood
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.core.feedback.ActiveChatTracker
import com.example.core.feedback.MioNotificationHelper
import com.example.data.local.MioUserSettings
import com.example.data.local.UserSettingsStore
import com.example.data.model.BlockRecord
import com.example.data.model.CallStatus
import com.example.data.model.CallTransport
import com.example.data.model.CallType
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRequestStatus
import com.example.data.model.ChatThreadPreview
import com.example.data.model.MessageContent
import com.example.data.model.MessageStatus
import com.example.data.model.MipStatus
import com.example.data.model.PresenceStatus
import com.example.data.model.ReplyQuote
import com.example.data.model.ReportReason
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UsernameClaim
import com.example.data.remote.ChatService
import com.example.data.remote.MipService
import com.example.data.remote.PresenceService
import com.example.data.remote.toHumanMessage
import com.example.ui.chats.ChatDetailViewModel
import com.example.ui.theme.MioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun username_and_safe_id_validation_rules() {
    assertTrue(MioConstants.USERNAME_REGEX.matches("mayank_01"))
    assertTrue(MioConstants.USERNAME_REGEX.matches("mio_two"))
    assertFalse(MioConstants.USERNAME_REGEX.matches("ab"))
    assertFalse(MioConstants.USERNAME_REGEX.matches("Upper_Case"))
    assertFalse(MioConstants.USERNAME_REGEX.matches("bad-char"))
    assertFalse(MioConstants.USERNAME_REGEX.matches("has space"))

    assertTrue("admin" in MioConstants.RESERVED_USERNAMES)
    assertTrue("support" in MioConstants.RESERVED_USERNAMES)
    assertTrue("mio" in MioConstants.RESERVED_USERNAMES)

    assertTrue(MioConstants.SAFE_ID_REGEX.matches("uidA_uidB-123"))
    assertFalse(MioConstants.SAFE_ID_REGEX.matches(""))
    assertFalse(MioConstants.SAFE_ID_REGEX.matches("bad/path"))
    assertFalse(MioConstants.SAFE_ID_REGEX.matches("../etc/passwd"))
  }

  @Test
  fun exact_palette_tokens_match_specification() {
    assertEquals(0xFF000000.toInt(), MioColors.PureBlack.toArgb())
    assertEquals(0xFF121212.toInt(), MioColors.CardSurface.toArgb())
    assertEquals(0xFF1C1C1E.toInt(), MioColors.ReceivedBubble.toArgb())
    assertEquals(0xFFFE8492.toInt(), MioColors.RoseAccent.toArgb())
    assertEquals(0xFFFFFFFF.toInt(), MioColors.PrimaryText.toArgb())
    assertEquals(0xFFA0A0A0.toInt(), MioColors.MutedText.toArgb())
    assertEquals(0xFFFF6B35.toInt(), MioColors.DangerEmber.toArgb())
    assertEquals(0xFF5196CE.toInt(), MioColors.TwilightBlue.toArgb())
    assertEquals(0xFF22C55E.toInt(), MioColors.OnlineGreen.toArgb())
    assertEquals(16f / 9f, MioConstants.INTRO_VIDEO_ASPECT_RATIO, 0.0001f)
  }

  @Test
  fun mood_registry_covers_all_five_phase1_moods() {
    val ids = CatMood.entries.map { it.id }.toSet()
    assertEquals(setOf("happy", "sad", "tired", "bored", "angry"), ids)
    for (mood in CatMood.entries) {
      assertNotNull(CatMood.fromId(mood.id))
      assertEquals(mood.hapticTimingsMs.size, mood.hapticAmplitudes.size)
      assertTrue(mood.soundAssetKey.startsWith("cat_"))
    }
    assertNull(CatMood.fromId("unknown"))
  }

  @Test
  fun rate_limit_and_quota_constants_are_strictly_bounded() {
    assertTrue(MioConstants.TYPING_THROTTLE_MS >= 3_000L)
    assertTrue(MioConstants.TYPING_IDLE_TIMEOUT_MS >= 5_000L)
    assertTrue(MioConstants.TYPING_STALE_TIMEOUT_MS > MioConstants.TYPING_IDLE_TIMEOUT_MS)
    assertEquals(24L, MioConstants.MIP_COOLDOWN_HOURS)
    assertEquals(2000, MioConstants.MAX_MESSAGE_LENGTH)
    assertTrue(MioConstants.CHATS_QUERY_LIMIT in 1L..100L)
    assertTrue(MioConstants.MESSAGES_QUERY_LIMIT in 1L..200L)
    assertEquals(
      listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"),
      MioConstants.GOOGLE_STUN_URLS,
    )
    assertTrue(MioConstants.CALL_RING_TIMEOUT_MS >= 30_000L)
    assertTrue(MioConstants.CALL_DISCONNECT_GRACE_MS >= 3_000L)
  }

  @Test
  fun webrtc_call_models_and_ice_stun_defaults_are_valid() {
    assertEquals(CallType.AUDIO, CallType.fromWire("audio"))
    assertEquals(CallType.VIDEO, CallType.fromWire("video"))
    assertEquals(CallType.AUDIO, CallType.fromWire("unknown"))

    assertFalse(CallStatus.CONNECTING.isTerminal)
    assertEquals(CallStatus.CONNECTING, CallStatus.fromWire("connecting"))
    assertFalse(CallStatus.RINGING.isTerminal)
    assertFalse(CallStatus.CONNECTED.isTerminal)
    assertTrue(CallStatus.ENDED.isTerminal)
    assertTrue(CallStatus.DECLINED.isTerminal)
    assertTrue(CallStatus.MISSED.isTerminal)
    assertEquals(CallTransport.RTDB, CallTransport.valueOf("RTDB"))
    assertEquals(CallTransport.FIRESTORE, CallTransport.valueOf("FIRESTORE"))
    assertEquals(
      listOf("stun:stun.l.google.com:19302", "stun:stun1.l.google.com:19302"),
      MioConstants.GOOGLE_STUN_URLS,
    )
  }

  @Test
  fun notification_title_body_and_active_chat_suppression_rules() {
    assertEquals("Sent you a message !", MioCopy.NOTIFICATION_NEW_MESSAGE_BODY)
    assertEquals(4, MioConstants.MAX_NOTIFICATION_POPUPS_PER_CHAT)

    assertEquals("Aanya", MioNotificationHelper.resolveSenderTitle("Aanya", "aanya_01"))
    assertEquals("aanya_01", MioNotificationHelper.resolveSenderTitle("", "aanya_01"))
    assertEquals("aanya_01", MioNotificationHelper.resolveSenderTitle("   ", "@aanya_01"))
    assertEquals("aanya_01", MioNotificationHelper.resolveSenderTitle(null, "aanya_01"))

    try {
      ActiveChatTracker.currentChatId = "chat_ab"
      ActiveChatTracker.isAppInForeground = true
      assertTrue(ActiveChatTracker.isActivelyViewingChat("chat_ab"))
      assertFalse(ActiveChatTracker.isActivelyViewingChat("chat_ac"))

      ActiveChatTracker.isAppInForeground = false
      assertFalse(ActiveChatTracker.isActivelyViewingChat("chat_ab"))
    } finally {
      ActiveChatTracker.currentChatId = null
      ActiveChatTracker.isAppInForeground = false
    }
  }

  @Test
  fun error_handler_strips_internal_details_and_maps_human_messages() {
    val internalRuntime = RuntimeException("NullPointerException at Foo.kt:99")
    assertEquals("Unable to send.", toHumanMessage(internalRuntime, "Unable to send."))

    val safeValidation = IllegalArgumentException("Please enter a valid username.")
    assertEquals("Please enter a valid username.", toHumanMessage(safeValidation, "Fallback"))

    val leakyValidation = IllegalStateException("Firebase internal path /databases/abc failed")
    assertEquals("Fallback", toHumanMessage(leakyValidation, "Fallback"))
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun chat_detail_viewmodel_throttles_rtdb_typing_and_debounces_send() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(dispatcher)
    try {
      val fakeChat = FakeChatService()
      val fakePresence = FakePresenceService()
      val vm = ChatDetailViewModel(
        chatService = fakeChat,
        presenceService = fakePresence,
        mipService = FakeMipService(),
        moodFeedbackManager = null,
        preferences = object : UserSettingsStore {
          override val settings: Flow<MioUserSettings> = MutableStateFlow(MioUserSettings())
        },
        chatId = "chat_1",
        currentUserId = "uid_1",
      )

      // Rapid keystrokes within 3s should produce only 1 RTDB typing=true write
      vm.onComposerTextChanged("h")
      runCurrent()
      vm.onComposerTextChanged("he")
      runCurrent()
      vm.onComposerTextChanged("hey")
      runCurrent()
      assertEquals(listOf(true), fakePresence.typingWrites)

      // Idle clear fires after 5 seconds
      advanceTimeBy(MioConstants.TYPING_IDLE_TIMEOUT_MS + 50L)
      advanceUntilIdle()
      assertEquals(listOf(true, false), fakePresence.typingWrites)

      // Double-tap send within debounce window sends only once; blank without mood is ignored
      val thread = sampleAcceptedThread()
      val profile = UserProfile(uid = "uid_1", username = "me", displayName = "Me", publicKey = "mypub")
      assertFalse(vm.sendMessage(thread, profile, "   "))
      assertTrue(vm.sendMessage(thread, profile, "Hello!"))
      assertFalse(vm.sendMessage(thread, profile, "Hello!"))
      advanceUntilIdle()
      assertEquals(1, fakeChat.sentBodies.size)

      vm.clearChatHistory(thread)
      advanceUntilIdle()
      assertEquals(listOf("chat_1"), fakeChat.clearedChats)
    } finally {
      Dispatchers.resetMain()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun chat_detail_overlays_rtdb_presence_and_syncs_outgoing_receipts() {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
      runTest(dispatcher) {
        val threadFlow = MutableStateFlow<ChatThreadPreview?>(
          sampleAcceptedThread().copy(
            lastMessageSenderUid = "uid_1",
            lastMessageStatus = MessageStatus.SENT,
            lastMessageTimeMs = 1_000L,
          )
        )
        val fakeChat = FakeChatService(chatFlow = threadFlow)
        val fakePresence = FakePresenceService()
        val vm = ChatDetailViewModel(
          chatService = fakeChat,
          presenceService = fakePresence,
          mipService = FakeMipService(),
          moodFeedbackManager = null,
          preferences = object : UserSettingsStore {
            override val settings: Flow<MioUserSettings> = MutableStateFlow(MioUserSettings())
          },
          chatId = "chat_1",
          currentUserId = "uid_1",
        )

        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
          vm.chatState.collect()
        }

        fakePresence.partnerStatus.value = PresenceStatus(state = "online", lastActiveMs = 5_000L)
        fakePresence.partnerTyping.value = true
        advanceUntilIdle()

        val active = (vm.chatState.value as UiState.Success).data!!
        assertTrue(active.partnerOnline)
        assertTrue(active.partnerTyping)
        assertEquals(5_000L, active.partnerLastActiveMs)

        // Blocking hides partner presence & typing in UI state
        threadFlow.value = active.copy(blockedByMe = true)
        advanceUntilIdle()
        val blocked = (vm.chatState.value as UiState.Success).data!!
        assertFalse(blocked.partnerOnline)
        assertFalse(blocked.partnerTyping)
        assertEquals(0L, blocked.partnerLastActiveMs)

        // Outgoing receipt status from Firestore chat doc promotes Room outgoing rows
        threadFlow.value = sampleAcceptedThread().copy(
          lastMessageSenderUid = "uid_1",
          lastMessageStatus = MessageStatus.DELIVERED,
          lastMessageTimeMs = 2_000L,
        )
        advanceUntilIdle()
        assertEquals(listOf(MessageStatus.DELIVERED), fakeChat.syncedReceiptStatuses)

        collectJob.cancel()
        advanceTimeBy(5_100L)
        advanceUntilIdle()
      }
    } finally {
      Dispatchers.resetMain()
    }
  }

  private fun sampleAcceptedThread() = ChatThreadPreview(
    chatId = "chat_1",
    partnerUid = "uid_2",
    partnerUsername = "partner",
    partnerDisplayName = "Partner",
    partnerPublicKey = "pub",
    status = ChatRequestStatus.ACCEPTED,
    initiatorUid = "uid_1",
    blockedByMe = false,
    blockedByPartner = false,
    partnerTyping = false,
    unreadCount = 0,
    lastMessagePreview = "",
    lastMessageSenderUid = "",
    lastMessageStatus = null,
    lastMessageTimeMs = 0L,
    mipStatus = MipStatus.NONE,
    mipSenderUid = "",
    mipNoteDecrypted = "",
    mipUpdatedTimeMs = 0L,
  )

  private class FakePresenceService : PresenceService {
    val typingWrites = mutableListOf<Boolean>()
    val partnerStatus = MutableStateFlow<PresenceStatus?>(null)
    val partnerTyping = MutableStateFlow(false)
    var lastBoundEnabled: Boolean? = null

    override fun bindPresence(myUid: String, enabled: Boolean) {
      lastBoundEnabled = enabled
    }
    override suspend fun goOfflineAndClear(myUid: String, removeNode: Boolean) {}
    override fun observeUserStatus(uid: String): Flow<PresenceStatus?> = partnerStatus
    override fun observeTyping(chatId: String, partnerUid: String): Flow<Boolean> = partnerTyping
    override suspend fun setTyping(chatId: String, myUid: String, typing: Boolean) {
      typingWrites += typing
    }
  }

  private class FakeChatService(
    private val chatFlow: Flow<ChatThreadPreview?> = flowOf(null),
  ) : ChatService {
    val sentBodies = mutableListOf<String>()
    val clearedChats = mutableListOf<String>()
    val syncedReceiptStatuses = mutableListOf<MessageStatus?>()

    override fun observeChats(myUid: String): Flow<List<ChatThreadPreview>> = flowOf(emptyList())
    override fun observeChat(chatId: String, myUid: String): Flow<ChatThreadPreview?> = chatFlow
    override fun observeMessages(chatId: String, myUid: String, partnerDisplayName: String, partnerPublicKey: String): Flow<List<ChatMessage>> = flowOf(emptyList())
    override suspend fun findUserByExactUsername(rawUsername: String, myUid: String): Result<UsernameClaim?> = Result.success(null)
    override suspend fun sendChatRequest(myProfile: UserProfile, target: UsernameClaim): Result<String> = Result.success("chat_1")
    override suspend fun respondToChatRequest(chatId: String, accept: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun sendMessage(chat: ChatThreadPreview, myProfile: UserProfile, content: MessageContent, replyQuote: ReplyQuote?): Result<Unit> {
      sentBodies += when (content) {
        is MessageContent.Text -> content.body
        is MessageContent.Mood -> content.mood.id
      }
      return Result.success(Unit)
    }
    override suspend fun retryFailedMessage(chat: ChatThreadPreview, myProfile: UserProfile, messageId: String): Result<Unit> = Result.success(Unit)
    override suspend fun markChatAsRead(chatId: String, myUid: String, unreadMessageIds: List<String>, updateLastMessagePreviewStatus: Boolean) {}
    override suspend fun syncOutgoingReceiptStatus(chatId: String, myUid: String, lastMessageStatus: MessageStatus?, lastMessageTimeMs: Long) {
      syncedReceiptStatuses += lastMessageStatus
    }
    override suspend fun toggleReaction(chatId: String, messageId: String, myUid: String, emoji: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteMessageForMe(chatId: String, messageId: String, myUid: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteMessageForEveryone(chatId: String, messageId: String, myUid: String): Result<Unit> = Result.success(Unit)
    override suspend fun clearChatMessages(chatId: String, myUid: String): Result<Unit> {
      clearedChats += chatId
      return Result.success(Unit)
    }
    override suspend fun blockUser(chatId: String, myUid: String, partnerUid: String, partnerUsername: String): Result<Unit> = Result.success(Unit)
    override suspend fun unblockUser(chatId: String, myUid: String, partnerUid: String): Result<Unit> = Result.success(Unit)
    override fun observeBlockedUsers(myUid: String): Flow<List<BlockRecord>> = flowOf(emptyList())
    override suspend fun unblockUserRecord(myUid: String, blockedUid: String): Result<Unit> = Result.success(Unit)
    override suspend fun submitReport(myUid: String, reportedUid: String, chatId: String, reason: ReportReason, excerpt: String?): Result<Unit> = Result.success(Unit)
    override suspend fun cleanupExpiredRelayDocs(myUid: String) {}
  }

  private class FakeMipService : MipService {
    override suspend fun sendMipLetter(chat: ChatThreadPreview, myProfile: UserProfile, note: String): Result<Unit> = Result.success(Unit)
    override suspend fun respondToMipLetter(chatId: String, myProfile: UserProfile, partnerUid: String, accept: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun unlinkMip(chatId: String, myProfile: UserProfile): Result<Unit> = Result.success(Unit)
  }
}
