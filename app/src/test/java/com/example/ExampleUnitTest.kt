package com.example

import androidx.compose.ui.graphics.toArgb
import com.example.core.config.CatMood
import com.example.core.config.MioConstants
import com.example.data.local.MioUserSettings
import com.example.data.local.UserSettingsStore
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRequestStatus
import com.example.data.model.ChatThreadPreview
import com.example.data.model.MessageContent
import com.example.data.model.MipStatus
import com.example.data.model.ReplyQuote
import com.example.data.model.ReportReason
import com.example.data.model.UserProfile
import com.example.data.model.UsernameClaim
import com.example.data.remote.ChatService
import com.example.data.remote.MipService
import com.example.data.remote.toHumanMessage
import com.example.ui.chats.ChatDetailViewModel
import com.example.ui.theme.MioColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
    assertEquals(0xFF221820.toInt(), MioColors.PlumBase.toArgb())
    assertEquals(0xFF45141B.toInt(), MioColors.BurgundySurface.toArgb())
    assertEquals(0xFFA42527.toInt(), MioColors.CrimsonPrimary.toArgb())
    assertEquals(0xFFFE8492.toInt(), MioColors.BlushRose.toArgb())
    assertEquals(0xFF5196CE.toInt(), MioColors.TwilightBlue.toArgb())
    assertEquals(0xFF262F45.toInt(), MioColors.MidnightSlate.toArgb())
    assertEquals(0xFFF8E8EE.toInt(), MioColors.WarmOffWhite.toArgb())
    assertEquals(0xFFB89AA6.toInt(), MioColors.MutedRose.toArgb())
    assertEquals(0xFFFF6B35.toInt(), MioColors.DangerEmber.toArgb())
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
    assertEquals(24L, MioConstants.MIP_COOLDOWN_HOURS)
    assertEquals(2000, MioConstants.MAX_MESSAGE_LENGTH)
    assertTrue(MioConstants.CHATS_QUERY_LIMIT in 1L..100L)
    assertTrue(MioConstants.MESSAGES_QUERY_LIMIT in 1L..200L)
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
  fun chat_detail_viewmodel_throttles_typing_and_debounces_send() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    Dispatchers.setMain(dispatcher)
    try {
      val fakeChat = FakeChatService()
      val vm = ChatDetailViewModel(
        chatService = fakeChat,
        mipService = FakeMipService(),
        moodFeedbackManager = null,
        preferences = object : UserSettingsStore {
          override val settings: Flow<MioUserSettings> = MutableStateFlow(MioUserSettings())
        },
        chatId = "chat_1",
        currentUserId = "uid_1",
      )

      // Rapid keystrokes within 3s should produce only 1 typing=true write
      vm.onComposerTextChanged("h")
      runCurrent()
      vm.onComposerTextChanged("he")
      runCurrent()
      vm.onComposerTextChanged("hey")
      runCurrent()
      assertEquals(listOf(true), fakeChat.typingWrites)

      // Idle clear fires after 5 seconds
      advanceTimeBy(MioConstants.TYPING_IDLE_TIMEOUT_MS + 50L)
      advanceUntilIdle()
      assertEquals(listOf(true, false), fakeChat.typingWrites)

      // Double-tap send within debounce window sends only once; blank without mood is ignored
      val thread = ChatThreadPreview(
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
      val profile = UserProfile(uid = "uid_1", username = "me", displayName = "Me", publicKey = "mypub")
      assertFalse(vm.sendMessage(thread, profile, "   "))
      assertTrue(vm.sendMessage(thread, profile, "Hello!"))
      assertFalse(vm.sendMessage(thread, profile, "Hello!"))
      advanceUntilIdle()
      assertEquals(1, fakeChat.sentBodies.size)
    } finally {
      Dispatchers.resetMain()
    }
  }

  private class FakeChatService : ChatService {
    val typingWrites = mutableListOf<Boolean>()
    val sentBodies = mutableListOf<String>()

    override fun observeChats(myUid: String): Flow<List<ChatThreadPreview>> = flowOf(emptyList())
    override fun observeChat(chatId: String, myUid: String): Flow<ChatThreadPreview?> = flowOf(null)
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
    override suspend fun setTyping(chatId: String, myUid: String, typing: Boolean) {
      typingWrites += typing
    }
    override suspend fun toggleReaction(chatId: String, messageId: String, myUid: String, emoji: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteMessageForMe(chatId: String, messageId: String, myUid: String): Result<Unit> = Result.success(Unit)
    override suspend fun deleteMessageForEveryone(chatId: String, messageId: String, myUid: String): Result<Unit> = Result.success(Unit)
    override suspend fun blockUser(chatId: String, myUid: String, partnerUid: String, partnerUsername: String): Result<Unit> = Result.success(Unit)
    override suspend fun unblockUser(chatId: String, myUid: String, partnerUid: String): Result<Unit> = Result.success(Unit)
    override suspend fun submitReport(myUid: String, reportedUid: String, chatId: String, reason: ReportReason, excerpt: String?): Result<Unit> = Result.success(Unit)
    override suspend fun cleanupExpiredRelayDocs(myUid: String) {}
  }

  private class FakeMipService : MipService {
    override suspend fun sendMipLetter(chat: ChatThreadPreview, myProfile: UserProfile, note: String): Result<Unit> = Result.success(Unit)
    override suspend fun respondToMipLetter(chatId: String, myProfile: UserProfile, partnerUid: String, accept: Boolean): Result<Unit> = Result.success(Unit)
    override suspend fun unlinkMip(chatId: String, myProfile: UserProfile): Result<Unit> = Result.success(Unit)
  }
}
