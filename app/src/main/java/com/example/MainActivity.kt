package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.config.MioConstants
import com.example.core.crypto.CryptoService
import com.example.core.crypto.EcdhAesCryptoService
import com.example.core.feedback.MioNotificationHelper
import com.example.core.feedback.MoodFeedbackManager
import com.example.data.local.MioDatabase
import com.example.data.local.MioPreferences
import com.example.data.local.MioUserSettings
import com.example.data.model.ChatRequestStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseAuthRepository
import com.example.data.remote.FirebaseChatRepository
import com.example.data.remote.FirebaseMipRepository
import com.example.data.remote.toHumanMessage
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.OnboardingScreen
import com.example.ui.auth.ProfileViewModel
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.performSignOut
import com.example.ui.chats.ChatDetailScreen
import com.example.ui.chats.ChatDetailViewModel
import com.example.ui.chats.ChatListScreen
import com.example.ui.chats.ChatListViewModel
import com.example.ui.components.MioErrorState
import com.example.ui.components.MioLoadingState
import com.example.ui.mip.MipScreen
import com.example.ui.settings.LegalDocumentType
import com.example.ui.settings.LegalScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object MioRoutes {
  const val ROUTE_CHATS = "chats"
  const val ROUTE_MIP = "mip"
  const val ROUTE_SETTINGS = "settings"
  const val ROUTE_CHAT_DETAIL = "chat_detail/{chatId}"
  const val ROUTE_PRIVACY = "privacy"
  const val ROUTE_TERMS = "terms"

  fun chatDetail(chatId: String): String = "chat_detail/$chatId"
}

private enum class MioTab(
  val route: String,
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
) {
  CHATS(MioRoutes.ROUTE_CHATS, "Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
  MIP(MioRoutes.ROUTE_MIP, "MIP", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
  SETTINGS(MioRoutes.ROUTE_SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val rawChatId = intent?.getStringExtra("open_chat_id")
    val validatedChatId = rawChatId?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }
    setContent {
      MioTheme {
        MioRootApp(initialChatId = validatedChatId)
      }
    }
  }
}

@Composable
private fun MioRootApp(initialChatId: String?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val notificationHelper = remember(context) { MioNotificationHelper(context.applicationContext) }

  var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
  var unauthLegalPage by rememberSaveable { mutableStateOf<LegalDocumentType?>(null) }

  DisposableEffect(Unit) {
    val listener = FirebaseAuth.AuthStateListener { auth ->
      currentUser = auth.currentUser
    }
    Firebase.auth.addAuthStateListener(listener)
    onDispose { Firebase.auth.removeAuthStateListener(listener) }
  }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = { currentUser = Firebase.auth.currentUser },
      onUnauthenticated = {},
      scope = scope,
    )
  }

  val user = currentUser
  if (user == null) {
    val legal = unauthLegalPage
    if (legal != null) {
      BackHandler { unauthLegalPage = null }
      LegalScreen(type = legal, onBack = { unauthLegalPage = null })
    } else {
      AuthScreen(
        onAuthSuccess = { currentUser = Firebase.auth.currentUser },
        onOpenPrivacy = { unauthLegalPage = LegalDocumentType.PRIVACY },
        onOpenTerms = { unauthLegalPage = LegalDocumentType.TERMS },
      )
    }
  } else {
    val uid = user.uid
    key(uid) {
      AuthenticatedMioSession(
        currentUserId = uid,
        defaultDisplayName = user.displayName ?: "",
        initialChatId = initialChatId,
        onSignOut = {
          performSignOut(
            credentialManager = credentialManager,
            scope = scope,
            onClearLocalArtifacts = { notificationHelper.cancelAll() },
          ) {
            currentUser = null
          }
        },
      )
    }
  }
}

@Composable
private fun AuthenticatedMioSession(
  currentUserId: String,
  defaultDisplayName: String,
  initialChatId: String?,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  var reloadTick by remember { mutableIntStateOf(0) }
  var onboardingLegalPage by rememberSaveable { mutableStateOf<LegalDocumentType?>(null) }

  val profileViewModel: ProfileViewModel = viewModel(
    key = "${currentUserId}_profile_$reloadTick",
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
        val crypto = EcdhAesCryptoService(app)
        val dao = MioDatabase.getInstance(app).localMessageDao()
        ProfileViewModel(
          authService = FirebaseAuthRepository(db, crypto, dao),
          currentUserId = currentUserId,
        )
      }
    },
  )

  val profileState by profileViewModel.profileState.collectAsStateWithLifecycle()
  val isSubmitting by profileViewModel.isSubmitting.collectAsStateWithLifecycle()
  val actionError by profileViewModel.actionError.collectAsStateWithLifecycle()

  when (val state = profileState) {
    is UiState.Loading -> MioLoadingState("Signing into Mio...")
    is UiState.Error -> MioErrorState(
      message = state.message,
      onRetry = { reloadTick++ },
    )
    is UiState.Success -> {
      val profile = state.data
      if (profile == null) {
        val legal = onboardingLegalPage
        if (legal != null) {
          BackHandler { onboardingLegalPage = null }
          LegalScreen(type = legal, onBack = { onboardingLegalPage = null })
        } else {
          OnboardingScreen(
            defaultDisplayName = defaultDisplayName,
            isSubmitting = isSubmitting,
            errorMessage = actionError,
            onSubmit = { username, displayName, ageConfirmed ->
              profileViewModel.claimUsernameAndCreateProfile(username, displayName, ageConfirmed)
            },
            onOpenPrivacy = { onboardingLegalPage = LegalDocumentType.PRIVACY },
            onOpenTerms = { onboardingLegalPage = LegalDocumentType.TERMS },
            onSignOut = onSignOut,
          )
        }
      } else {
        MioMainScaffold(
          myProfile = profile,
          currentUserId = currentUserId,
          profileViewModel = profileViewModel,
          initialChatId = initialChatId,
          onSignOut = onSignOut,
        )
      }
    }
  }
}

@Composable
private fun MioMainScaffold(
  myProfile: UserProfile,
  currentUserId: String,
  profileViewModel: ProfileViewModel,
  initialChatId: String?,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val navController = rememberNavController()
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStackEntry?.destination?.route

  var chatListReloadTick by remember { mutableIntStateOf(0) }
  var mipActionError by rememberSaveable { mutableStateOf<String?>(null) }

  val preferences = remember(context) { MioPreferences(context.applicationContext) }
  val settings by preferences.settings.collectAsStateWithLifecycle(initialValue = MioUserSettings())
  val cryptoService: CryptoService = remember(context) { EcdhAesCryptoService(context.applicationContext) }
  val keyFingerprint by produceState(initialValue = "Loading...", key1 = currentUserId) {
    value = cryptoService.getKeyFingerprint(currentUserId)
  }

  val chatListViewModel: ChatListViewModel = viewModel(
    key = "${currentUserId}_chatlist_$chatListReloadTick",
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
        val crypto = EcdhAesCryptoService(app)
        val dao = MioDatabase.getInstance(app).localMessageDao()
        ChatListViewModel(
          chatService = FirebaseChatRepository(db, crypto, dao),
          notificationHelper = MioNotificationHelper(app),
          preferences = MioPreferences(app),
          currentUserId = currentUserId,
        )
      }
    },
  )

  val chatsState by chatListViewModel.chatsState.collectAsStateWithLifecycle()
  val searchResult by chatListViewModel.searchResult.collectAsStateWithLifecycle()
  val searchError by chatListViewModel.searchError.collectAsStateWithLifecycle()
  val isSearching by chatListViewModel.isSearching.collectAsStateWithLifecycle()
  val actionMessage by chatListViewModel.actionMessage.collectAsStateWithLifecycle()
  val profileSubmitting by profileViewModel.isSubmitting.collectAsStateWithLifecycle()
  val profileError by profileViewModel.actionError.collectAsStateWithLifecycle()

  LaunchedEffect(initialChatId) {
    if (!initialChatId.isNullOrBlank() && MioConstants.SAFE_ID_REGEX.matches(initialChatId)) {
      navController.navigate(MioRoutes.chatDetail(initialChatId)) {
        launchSingleTop = true
      }
    }
  }

  val totalUnreadCount = (chatsState as? UiState.Success)?.data
    ?.sumOf { it.unreadCount } ?: 0
  val pendingInviteCount = (chatsState as? UiState.Success)?.data
    ?.count { it.status == ChatRequestStatus.PENDING && it.initiatorUid != currentUserId } ?: 0
  val chatBadgeCount = totalUnreadCount + pendingInviteCount

  val showChrome = currentRoute in setOf(
    MioRoutes.ROUTE_CHATS,
    MioRoutes.ROUTE_MIP,
    MioRoutes.ROUTE_SETTINGS,
  )

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWideScreen = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (showChrome && isWideScreen) {
        NavigationRail(
          containerColor = MioColors.MidnightSlate,
          contentColor = MioColors.WarmOffWhite,
        ) {
          MioTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationRailItem(
              selected = selected,
              onClick = {
                navController.navigate(tab.route) {
                  popUpTo(MioRoutes.ROUTE_CHATS) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              },
              icon = {
                TabIconWithBadge(tab = tab, selected = selected, chatBadgeCount = chatBadgeCount)
              },
              label = { Text(tab.label) },
              colors = NavigationRailItemDefaults.colors(
                selectedIconColor = MioColors.BlushRose,
                selectedTextColor = MioColors.BlushRose,
                indicatorColor = MioColors.BurgundySurface,
                unselectedIconColor = MioColors.MutedRose,
                unselectedTextColor = MioColors.MutedRose,
              ),
              modifier = Modifier.testTag("rail_${tab.route}"),
            )
          }
        }
      }

      Scaffold(
        modifier = Modifier.weight(1f),
        containerColor = MioColors.PlumBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
          if (showChrome && !isWideScreen) {
            NavigationBar(
              containerColor = MioColors.MidnightSlate,
              contentColor = MioColors.WarmOffWhite,
              modifier = Modifier.navigationBarsPadding(),
            ) {
              MioTab.entries.forEach { tab ->
                val selected = currentRoute == tab.route
                NavigationBarItem(
                  selected = selected,
                  onClick = {
                    navController.navigate(tab.route) {
                      popUpTo(MioRoutes.ROUTE_CHATS) { saveState = true }
                      launchSingleTop = true
                      restoreState = true
                    }
                  },
                  icon = {
                    TabIconWithBadge(tab = tab, selected = selected, chatBadgeCount = chatBadgeCount)
                  },
                  label = { Text(tab.label) },
                  colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MioColors.BlushRose,
                    selectedTextColor = MioColors.BlushRose,
                    indicatorColor = MioColors.BurgundySurface,
                    unselectedIconColor = MioColors.MutedRose,
                    unselectedTextColor = MioColors.MutedRose,
                  ),
                  modifier = Modifier.testTag("nav_${tab.route}"),
                )
              }
            }
          }
        },
      ) { innerPadding ->
        NavHost(
          navController = navController,
          startDestination = MioRoutes.ROUTE_CHATS,
          modifier = Modifier.fillMaxSize(),
        ) {
          composable(MioRoutes.ROUTE_CHATS) {
            ChatListScreen(
              myProfile = myProfile,
              chatsState = chatsState,
              searchResult = searchResult,
              searchError = searchError,
              isSearching = isSearching,
              actionMessage = actionMessage,
              contentPadding = innerPadding,
              onOpenChat = { chatId ->
                navController.navigate(MioRoutes.chatDetail(chatId))
              },
              onSearchExactUsername = { query -> chatListViewModel.searchExactUsername(query) },
              onClearSearch = { chatListViewModel.clearSearch() },
              onSendChatRequest = { target ->
                chatListViewModel.sendChatRequest(myProfile, target) { chatId ->
                  navController.navigate(MioRoutes.chatDetail(chatId))
                }
              },
              onRespondToRequest = { chatId, accept ->
                chatListViewModel.respondToRequest(chatId, accept)
              },
              onRetry = { chatListReloadTick++ },
              onDismissActionMessage = { chatListViewModel.clearActionMessage() },
            )
          }

          composable(MioRoutes.ROUTE_MIP) {
            val mipRepo = remember(context) {
              val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
              FirebaseMipRepository(db, EcdhAesCryptoService(context.applicationContext))
            }
            MipScreen(
              myProfile = myProfile,
              chatsState = chatsState,
              mipError = mipActionError,
              contentPadding = innerPadding,
              onOpenChat = { chatId -> navController.navigate(MioRoutes.chatDetail(chatId)) },
              onRespondToMip = { chat, accept ->
                scope.launch {
                  mipActionError = null
                  mipRepo.respondToMipLetter(chat.chatId, myProfile, chat.partnerUid, accept)
                    .onFailure { err ->
                      mipActionError = toHumanMessage(err, "Could not respond to MIP letter.")
                    }
                }
              },
              onUnlinkMip = { chat ->
                scope.launch {
                  mipActionError = null
                  mipRepo.unlinkMip(chat.chatId, myProfile)
                    .onFailure { err ->
                      mipActionError = toHumanMessage(err, "Could not unlink MIP.")
                    }
                }
              },
              onDismissMipError = { mipActionError = null },
              onRetry = { chatListReloadTick++ },
            )
          }

          composable(MioRoutes.ROUTE_SETTINGS) {
            SettingsScreen(
              profile = myProfile,
              keyFingerprint = keyFingerprint,
              settings = settings,
              isSubmitting = profileSubmitting,
              actionError = profileError,
              contentPadding = innerPadding,
              onUpdateDisplayName = { newName ->
                profileViewModel.updateDisplayName(myProfile.username, newName)
              },
              onToggleHaptics = { enabled ->
                scope.launch { preferences.setHapticsEnabled(enabled) }
              },
              onToggleSounds = { enabled ->
                scope.launch { preferences.setSoundsEnabled(enabled) }
              },
              onToggleNotifications = { enabled ->
                scope.launch { preferences.setNotificationsEnabled(enabled) }
              },
              onOpenPrivacy = { navController.navigate(MioRoutes.ROUTE_PRIVACY) },
              onOpenTerms = { navController.navigate(MioRoutes.ROUTE_TERMS) },
              onSignOut = onSignOut,
              onDeleteAccount = {
                profileViewModel.deleteAccount(myProfile.username) {
                  onSignOut()
                }
              },
            )
          }

          composable(
            route = MioRoutes.ROUTE_CHAT_DETAIL,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
          ) { backStackEntry ->
            val rawChatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val chatId = rawChatId.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }.orEmpty()
            var detailReloadTick by remember { mutableIntStateOf(0) }

            val detailViewModel: ChatDetailViewModel = viewModel(
              key = "${currentUserId}_chat_${chatId}_$detailReloadTick",
              factory = viewModelFactory {
                initializer {
                  val app = checkNotNull(this[APPLICATION_KEY])
                  val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                  val crypto = EcdhAesCryptoService(app)
                  val dao = MioDatabase.getInstance(app).localMessageDao()
                  ChatDetailViewModel(
                    chatService = FirebaseChatRepository(db, crypto, dao),
                    mipService = FirebaseMipRepository(db, crypto),
                    moodFeedbackManager = MoodFeedbackManager(app),
                    preferences = MioPreferences(app),
                    chatId = chatId,
                    currentUserId = currentUserId,
                  )
                }
              },
            )

            val chatState by detailViewModel.chatState.collectAsStateWithLifecycle()
            val messagesState by detailViewModel.messagesState.collectAsStateWithLifecycle()
            val replyQuote by detailViewModel.replyQuote.collectAsStateWithLifecycle()
            val selectedMood by detailViewModel.selectedMood.collectAsStateWithLifecycle()
            val bannerNotice by detailViewModel.bannerNotice.collectAsStateWithLifecycle()

            ChatDetailScreen(
              myProfile = myProfile,
              chatState = chatState,
              messagesState = messagesState,
              replyQuote = replyQuote,
              selectedMood = selectedMood,
              bannerNotice = bannerNotice,
              onBack = { navController.popBackStack() },
              onTextChanged = { text -> detailViewModel.onComposerTextChanged(text) },
              onSendMessage = { chat, text -> detailViewModel.sendMessage(chat, myProfile, text) },
              onRetryFailedMessage = { chat, msgId ->
                detailViewModel.retryFailedMessage(chat, myProfile, msgId)
              },
              onSetReplyQuote = { msg, partnerName ->
                detailViewModel.setReplyQuote(msg, myProfile.displayName, partnerName)
              },
              onClearReplyQuote = { detailViewModel.clearReplyQuote() },
              onSelectMood = { mood -> detailViewModel.selectMood(mood) },
              onToggleReaction = { msgId, emoji -> detailViewModel.toggleReaction(msgId, emoji) },
              onDeleteForMe = { msgId -> detailViewModel.deleteForMe(msgId) },
              onDeleteForEveryone = { msgId -> detailViewModel.deleteForEveryone(msgId) },
              onRespondToChatInvite = { accept -> detailViewModel.respondToChatInvite(accept) },
              onBlockPartner = { chat -> detailViewModel.blockPartner(chat) },
              onUnblockPartner = { chat -> detailViewModel.unblockPartner(chat) },
              onReportPartner = { chat, reason, excerpt ->
                detailViewModel.reportPartner(chat, reason, excerpt)
              },
              onSendMipLetter = { chat, note, onDone ->
                detailViewModel.sendMipLetter(chat, myProfile, note, onDone)
              },
              onRespondToMip = { chat, accept -> detailViewModel.respondToMip(chat, myProfile, accept) },
              onDismissBanner = { detailViewModel.clearBannerNotice() },
              onRetry = { detailReloadTick++ },
            )
          }

          composable(MioRoutes.ROUTE_PRIVACY) {
            LegalScreen(
              type = LegalDocumentType.PRIVACY,
              onBack = { navController.popBackStack() },
            )
          }

          composable(MioRoutes.ROUTE_TERMS) {
            LegalScreen(
              type = LegalDocumentType.TERMS,
              onBack = { navController.popBackStack() },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TabIconWithBadge(
  tab: MioTab,
  selected: Boolean,
  chatBadgeCount: Int,
) {
  val icon = if (selected) tab.selectedIcon else tab.unselectedIcon
  if (tab == MioTab.CHATS && chatBadgeCount > 0) {
    BadgedBox(
      badge = {
        Badge(
          containerColor = MioColors.BlushRose,
          contentColor = MioColors.PlumBase,
        ) {
          Text(if (chatBadgeCount > 99) "99+" else chatBadgeCount.toString())
        }
      },
    ) {
      Icon(imageVector = icon, contentDescription = tab.label)
    }
  } else {
    Icon(imageVector = icon, contentDescription = tab.label)
  }
}
