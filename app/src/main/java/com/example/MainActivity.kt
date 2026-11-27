package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Surface
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
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.example.core.calls.CallSessionHolder
import com.example.core.calls.WebRtcCallManager
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.core.crypto.CryptoService
import com.example.core.crypto.EcdhAesCryptoService
import com.example.core.feedback.ActiveChatTracker
import com.example.core.feedback.MioNotificationHelper
import com.example.core.feedback.MoodFeedbackManager
import com.example.data.local.MioDatabase
import com.example.data.local.MioPreferences
import com.example.data.local.MioUserSettings
import com.example.data.model.CallStatus
import com.example.data.model.CallTransport
import com.example.data.model.CallType
import com.example.data.model.ChatRequestStatus
import com.example.data.model.ChatThreadPreview
import com.example.data.model.IncomingCallInvite
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseAuthRepository
import com.example.data.remote.FirebaseChatRepository
import com.example.data.remote.FirebaseMipRepository
import com.example.data.remote.PresenceService
import com.example.data.remote.RtdbPresenceRepository
import com.example.data.remote.toHumanMessage
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.OnboardingScreen
import com.example.ui.auth.ProfileViewModel
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.performSignOut
import com.example.ui.calls.ActiveCallScreen
import com.example.ui.calls.IncomingCallOverlay
import com.example.ui.calls.MinimizedCallBanner
import com.example.ui.chats.ChatDetailScreen
import com.example.ui.chats.ChatDetailViewModel
import com.example.ui.chats.ChatListScreen
import com.example.ui.chats.ChatListViewModel
import com.example.ui.components.MioDiscreteBottomBar
import com.example.ui.components.MioDiscreteTab
import com.example.ui.components.MioErrorState
import com.example.ui.components.MioLoadingState
import com.example.ui.components.OpeningIntroVideo
import com.example.ui.settings.LegalDocumentType
import com.example.ui.settings.LegalScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object MioRoutes {
  const val ROUTE_CHATS = "chats"
  const val ROUTE_SETTINGS = "settings"
  const val ROUTE_CHAT_DETAIL = "chat_detail/{chatId}?fromCircle={fromCircle}"
  const val ROUTE_PRIVACY = "privacy"
  const val ROUTE_TERMS = "terms"

  fun chatDetail(chatId: String, fromCircle: Boolean = false): String =
    "chat_detail/$chatId?fromCircle=$fromCircle"
}


class MainActivity : ComponentActivity() {
  private val pendingChatIdState = MutableStateFlow<String?>(null)
  private val pendingOpenActiveCallState = MutableStateFlow(false)
  private val pendingAnswerInviteState = MutableStateFlow<IncomingCallInvite?>(null)
  private val pendingIncomingInviteState = MutableStateFlow<IncomingCallInvite?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      splashScreen.setOnExitAnimationListener { splashScreenView ->
        splashScreenView.remove()
      }
    }
    enableEdgeToEdge()
    processIntent(intent)
    setContent {
      val pendingChatId by pendingChatIdState.collectAsStateWithLifecycle()
      val pendingOpenActiveCall by pendingOpenActiveCallState.collectAsStateWithLifecycle()
      val pendingAnswerInvite by pendingAnswerInviteState.collectAsStateWithLifecycle()
      val pendingIncomingInvite by pendingIncomingInviteState.collectAsStateWithLifecycle()
      MioTheme {
        MioRootApp(
          pendingChatId = pendingChatId,
          pendingOpenActiveCall = pendingOpenActiveCall,
          pendingAnswerInvite = pendingAnswerInvite,
          pendingIncomingInvite = pendingIncomingInvite,
          onConsumePendingChatId = { pendingChatIdState.value = null },
          onConsumePendingOpenActiveCall = { pendingOpenActiveCallState.value = false },
          onConsumePendingAnswerInvite = { pendingAnswerInviteState.value = null },
          onConsumePendingIncomingInvite = { pendingIncomingInviteState.value = null },
          onLockScreenModeChanged = { showOverLockScreen ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
              setShowWhenLocked(showOverLockScreen)
              setTurnScreenOn(showOverLockScreen)
            }
          },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    processIntent(intent)
  }

  private fun processIntent(intent: Intent?) {
    if (intent == null) return
    val action = intent.action
    if (action == MioNotificationHelper.ACTION_OPEN_ACTIVE_CALL ||
      intent.getBooleanExtra(MioNotificationHelper.EXTRA_OPEN_ACTIVE_CALL, false)
    ) {
      pendingOpenActiveCallState.value = true
      return
    }

    if (action == MioNotificationHelper.ACTION_ANSWER_CALL ||
      action == MioNotificationHelper.ACTION_INCOMING_CALL
    ) {
      val invite = extractIncomingCallInvite(intent) ?: return
      if (action == MioNotificationHelper.ACTION_ANSWER_CALL) {
        MioNotificationHelper(applicationContext).cancelIncomingCallNotification(invite.callId)
        pendingIncomingInviteState.value = null
        pendingAnswerInviteState.value = invite
      } else {
        pendingIncomingInviteState.value = invite
      }
      return
    }

    val nextChatId = extractSafeChatId(intent)
    if (nextChatId != null) {
      pendingChatIdState.value = nextChatId
    }
  }

  private fun extractSafeChatId(intent: Intent?): String? {
    val rawChatId = intent?.getStringExtra("open_chat_id") ?: intent?.getStringExtra("chatId")
    return rawChatId?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }
  }

  private fun extractIncomingCallInvite(intent: Intent): IncomingCallInvite? {
    val callId = intent.getStringExtra(MioNotificationHelper.EXTRA_CALL_ID)
      ?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) } ?: return null
    val callerId = intent.getStringExtra(MioNotificationHelper.EXTRA_CALLER_ID)
      ?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) } ?: return null
    val chatId = intent.getStringExtra(MioNotificationHelper.EXTRA_CALL_CHAT_ID)
      ?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }.orEmpty()
    val callerName = intent.getStringExtra(MioNotificationHelper.EXTRA_CALLER_NAME)
      ?.trim()?.takeIf { it.isNotBlank() } ?: MioCopy.APP_NAME
    val callerUsername = intent.getStringExtra(MioNotificationHelper.EXTRA_CALLER_USERNAME)
      ?.trim().orEmpty()
    val callType = CallType.fromWire(intent.getStringExtra(MioNotificationHelper.EXTRA_CALL_TYPE))
    val transport = if (intent.getStringExtra(MioNotificationHelper.EXTRA_CALL_TRANSPORT) == CallTransport.FIRESTORE.name) {
      CallTransport.FIRESTORE
    } else {
      CallTransport.RTDB
    }
    val createdAtMs = intent.getLongExtra(MioNotificationHelper.EXTRA_CALL_CREATED_AT_MS, System.currentTimeMillis())
    return IncomingCallInvite(
      callId = callId,
      chatId = chatId,
      callerId = callerId,
      callerName = callerName,
      callerUsername = callerUsername,
      type = callType,
      status = CallStatus.RINGING,
      createdAtMs = createdAtMs,
      transport = transport,
    )
  }
}

@Composable
private fun MioRootApp(
  pendingChatId: String?,
  pendingOpenActiveCall: Boolean,
  pendingAnswerInvite: IncomingCallInvite?,
  pendingIncomingInvite: IncomingCallInvite?,
  onConsumePendingChatId: () -> Unit,
  onConsumePendingOpenActiveCall: () -> Unit,
  onConsumePendingAnswerInvite: () -> Unit,
  onConsumePendingIncomingInvite: () -> Unit,
  onLockScreenModeChanged: (Boolean) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val notificationHelper = remember(context) { MioNotificationHelper(context.applicationContext) }

  var showIntro by rememberSaveable { mutableStateOf(true) }
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
    val keepSignedIn = runCatching {
      MioPreferences(context.applicationContext).settings.first().keepSignedIn
    }.getOrDefault(true)
    if (!keepSignedIn) {
      Firebase.auth.signOut()
      currentUser = null
    } else {
      attemptAutoSignIn(
        context = context,
        credentialManager = credentialManager,
        onAuthSuccess = { currentUser = Firebase.auth.currentUser },
        onUnauthenticated = {},
        scope = scope,
      )
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(if (showIntro) MioColors.PureBlack else MioColors.PlumBase),
  ) {
    Surface(
      color = MioColors.PlumBase,
      modifier = Modifier.fillMaxSize(),
    ) {
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
            introFinished = !showIntro,
            pendingChatId = pendingChatId,
            pendingOpenActiveCall = pendingOpenActiveCall,
            pendingAnswerInvite = pendingAnswerInvite,
            pendingIncomingInvite = pendingIncomingInvite,
            onConsumePendingChatId = onConsumePendingChatId,
            onConsumePendingOpenActiveCall = onConsumePendingOpenActiveCall,
            onConsumePendingAnswerInvite = onConsumePendingAnswerInvite,
            onConsumePendingIncomingInvite = onConsumePendingIncomingInvite,
            onLockScreenModeChanged = onLockScreenModeChanged,
            onSignOut = { clearRemoteToken ->
              performSignOut(
                credentialManager = credentialManager,
                scope = scope,
                onClearLocalArtifacts = {
                  clearRemoteToken()
                  CallSessionHolder.disposeSession(uid)
                  notificationHelper.cancelAll()
                },
              ) {
                currentUser = null
              }
            },
          )
        }
      }
    }

    AnimatedVisibility(
      visible = showIntro,
      enter = EnterTransition.None,
      exit = fadeOut(animationSpec = tween(durationMillis = MioConstants.INTRO_FADE_OUT_MS)),
      modifier = Modifier.fillMaxSize(),
    ) {
      OpeningIntroVideo(
        onFinished = { showIntro = false },
      )
    }
  }
}

@Composable
private fun AuthenticatedMioSession(
  currentUserId: String,
  defaultDisplayName: String,
  introFinished: Boolean,
  pendingChatId: String?,
  pendingOpenActiveCall: Boolean,
  pendingAnswerInvite: IncomingCallInvite?,
  pendingIncomingInvite: IncomingCallInvite?,
  onConsumePendingChatId: () -> Unit,
  onConsumePendingOpenActiveCall: () -> Unit,
  onConsumePendingAnswerInvite: () -> Unit,
  onConsumePendingIncomingInvite: () -> Unit,
  onLockScreenModeChanged: (Boolean) -> Unit,
  onSignOut: (clearRemoteToken: suspend () -> Unit) -> Unit,
) {
  val context = LocalContext.current
  var reloadTick by remember { mutableIntStateOf(0) }
  var onboardingLegalPage by rememberSaveable { mutableStateOf<LegalDocumentType?>(null) }
  val presenceService: PresenceService = remember(currentUserId) { RtdbPresenceRepository() }
  val callManager = remember(currentUserId) {
    CallSessionHolder.getOrCreate(context.applicationContext, currentUserId)
  }

  val profileViewModel: ProfileViewModel = viewModel(
    key = "${currentUserId}_profile_$reloadTick",
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY])
        val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
        val crypto = EcdhAesCryptoService(app)
        val dao = MioDatabase.getInstance(app).localMessageDao()
        ProfileViewModel(
          authService = FirebaseAuthRepository(
            db = db,
            cryptoService = crypto,
            localMessageDao = dao,
            presenceService = presenceService,
          ),
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
            onSignOut = { onSignOut {} },
          )
        }
      } else {
        MioMainScaffold(
          myProfile = profile,
          currentUserId = currentUserId,
          profileViewModel = profileViewModel,
          presenceService = presenceService,
          callManager = callManager,
          introFinished = introFinished,
          pendingChatId = pendingChatId,
          pendingOpenActiveCall = pendingOpenActiveCall,
          pendingAnswerInvite = pendingAnswerInvite,
          pendingIncomingInvite = pendingIncomingInvite,
          onConsumePendingChatId = onConsumePendingChatId,
          onConsumePendingOpenActiveCall = onConsumePendingOpenActiveCall,
          onConsumePendingAnswerInvite = onConsumePendingAnswerInvite,
          onConsumePendingIncomingInvite = onConsumePendingIncomingInvite,
          onLockScreenModeChanged = onLockScreenModeChanged,
          onSignOut = {
            onSignOut {
              callManager.endCall(CallStatus.ENDED)
              val hidePresence = runCatching {
                !MioPreferences(context.applicationContext).settings.first().showOnlineStatus
              }.getOrDefault(false)
              presenceService.goOfflineAndClear(currentUserId, removeNode = hidePresence)
              profileViewModel.clearFcmTokenForSignOut()
            }
          },
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
  presenceService: PresenceService,
  callManager: WebRtcCallManager,
  introFinished: Boolean,
  pendingChatId: String?,
  pendingOpenActiveCall: Boolean,
  pendingAnswerInvite: IncomingCallInvite?,
  pendingIncomingInvite: IncomingCallInvite?,
  onConsumePendingChatId: () -> Unit,
  onConsumePendingOpenActiveCall: () -> Unit,
  onConsumePendingAnswerInvite: () -> Unit,
  onConsumePendingIncomingInvite: () -> Unit,
  onLockScreenModeChanged: (Boolean) -> Unit,
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
  val pendingFcmToken by preferences.pendingFcmToken.collectAsStateWithLifecycle(initialValue = null)
  val cryptoService: CryptoService = remember(context) { EcdhAesCryptoService(context.applicationContext) }
  val keyFingerprint by produceState(initialValue = "Loading...", key1 = currentUserId) {
    value = cryptoService.getKeyFingerprint(currentUserId)
  }

  val chatRepo = remember(currentUserId) {
    val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
    val dao = MioDatabase.getInstance(context).localMessageDao()
    FirebaseChatRepository(db, cryptoService, dao)
  }
  val blockedUsers by chatRepo.observeBlockedUsers(currentUserId).collectAsStateWithLifecycle(initialValue = emptyList())

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
          presenceService = presenceService,
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

  var hasRequestedNotifPermission by rememberSaveable(currentUserId) { mutableStateOf(false) }
  var pendingOutgoingCall by remember { mutableStateOf<Pair<ChatThreadPreview, Boolean>?>(null) }
  var pendingIncomingAccept by remember { mutableStateOf<IncomingCallInvite?>(null) }
  var callPermissionNotice by remember { mutableStateOf<String?>(null) }

  val incomingInvite by callManager.incomingInvite.collectAsStateWithLifecycle()
  val activeCall by callManager.activeCall.collectAsStateWithLifecycle()
  val isCallMinimized by CallSessionHolder.isCallMinimized.collectAsStateWithLifecycle()
  val callErrorNotice by callManager.callErrorNotice.collectAsStateWithLifecycle()

  val callPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions(),
  ) { grants ->
    val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val pendingOut = pendingOutgoingCall
    val pendingIn = pendingIncomingAccept
    pendingOutgoingCall = null
    pendingIncomingAccept = null

    if (pendingOut != null) {
      val (targetChat, isVideo) = pendingOut
      val cameraGranted = !isVideo || grants[Manifest.permission.CAMERA] == true ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
      if (audioGranted && cameraGranted) {
        callManager.startCall(
          callerProfile = myProfile,
          receiverId = targetChat.partnerUid,
          receiverName = targetChat.partnerDisplayName,
          receiverUsername = targetChat.partnerUsername,
          chatId = targetChat.chatId,
          isVideo = isVideo,
        )
      } else {
        callPermissionNotice = MioCopy.CALL_PERMISSION_REQUIRED
      }
    } else if (pendingIn != null) {
      val isVideo = pendingIn.type == CallType.VIDEO
      val cameraGranted = !isVideo || grants[Manifest.permission.CAMERA] == true ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
      if (audioGranted && cameraGranted) {
        callManager.answerCall(pendingIn, currentUserId)
      } else {
        callPermissionNotice = MioCopy.CALL_PERMISSION_REQUIRED
      }
    }
  }

  fun launchCallWithPermissions(chat: ChatThreadPreview, isVideo: Boolean) {
    val needed = buildList {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        add(Manifest.permission.RECORD_AUDIO)
      }
      if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        add(Manifest.permission.CAMERA)
      }
    }
    if (needed.isEmpty()) {
      callManager.startCall(
        callerProfile = myProfile,
        receiverId = chat.partnerUid,
        receiverName = chat.partnerDisplayName,
        receiverUsername = chat.partnerUsername,
        chatId = chat.chatId,
        isVideo = isVideo,
      )
    } else {
      pendingIncomingAccept = null
      pendingOutgoingCall = chat to isVideo
      callPermissionLauncher.launch(needed.toTypedArray())
    }
  }

  fun answerCallWithPermissions(invite: IncomingCallInvite) {
    val isVideo = invite.type == CallType.VIDEO
    val needed = buildList {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        add(Manifest.permission.RECORD_AUDIO)
      }
      if (isVideo && ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        add(Manifest.permission.CAMERA)
      }
    }
    if (needed.isEmpty()) {
      callManager.answerCall(invite, currentUserId)
    } else {
      pendingOutgoingCall = null
      pendingIncomingAccept = invite
      callPermissionLauncher.launch(needed.toTypedArray())
    }
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    scope.launch { preferences.setNotificationsEnabled(granted) }
  }

  LaunchedEffect(currentUserId) {
    callManager.startIncomingCallListener(currentUserId)
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  val persistedShowOnlineStatus by remember(preferences) {
    preferences.settings.map { it.showOnlineStatus }
  }.collectAsStateWithLifecycle(initialValue = null)

  LaunchedEffect(currentUserId, persistedShowOnlineStatus) {
    val enabled = persistedShowOnlineStatus ?: return@LaunchedEffect
    presenceService.bindPresence(currentUserId, enabled)
  }

  DisposableEffect(lifecycleOwner, currentUserId, persistedShowOnlineStatus) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START,
        Lifecycle.Event.ON_RESUME -> {
          ActiveChatTracker.isAppInForeground = true
          if (event == Lifecycle.Event.ON_START) {
            callManager.onAppForegrounded()
            profileViewModel.syncCurrentFcmTokenIfNeeded(myProfile.fcmToken, pendingFcmToken)
            val enabled = persistedShowOnlineStatus
            if (enabled != null) {
              presenceService.bindPresence(currentUserId, enabled)
            }
          }
        }
        Lifecycle.Event.ON_STOP -> {
          val rotating = (context as? ComponentActivity)?.isChangingConfigurations == true
          if (!rotating) {
            ActiveChatTracker.isAppInForeground = false
            callManager.onAppBackgrounded()
            val enabled = persistedShowOnlineStatus
            if (enabled != null) {
              CoroutineScope(Dispatchers.IO + NonCancellable).launch {
                presenceService.goOfflineAndClear(
                  myUid = currentUserId,
                  removeNode = !enabled,
                )
              }
            }
          }
        }
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      ActiveChatTracker.isAppInForeground = false
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  LaunchedEffect(currentUserId, pendingFcmToken, myProfile.fcmToken) {
    MioNotificationHelper(context.applicationContext).ensureChannel()
    profileViewModel.syncCurrentFcmTokenIfNeeded(myProfile.fcmToken, pendingFcmToken)
  }

  LaunchedEffect(
    currentUserId,
    settings.notificationsEnabled,
    settings.callNotificationsEnabled,
    settings.muteAllUntilMs,
  ) {
    if (
      myProfile.notificationsEnabled != settings.notificationsEnabled ||
      myProfile.callNotificationsEnabled != settings.callNotificationsEnabled ||
      myProfile.muteAllUntilMs != settings.muteAllUntilMs
    ) {
      profileViewModel.syncNotificationPreferences(
        notificationsEnabled = settings.notificationsEnabled,
        callNotificationsEnabled = settings.callNotificationsEnabled,
        muteAllUntilMs = settings.muteAllUntilMs,
      )
    }
  }

  LaunchedEffect(activeCall, incomingInvite, pendingIncomingInvite) {
    onLockScreenModeChanged(
      activeCall != null || incomingInvite != null || pendingIncomingInvite != null,
    )
  }

  LaunchedEffect(pendingOpenActiveCall) {
    if (pendingOpenActiveCall) {
      CallSessionHolder.setMinimized(false)
      onConsumePendingOpenActiveCall()
    }
  }

  LaunchedEffect(pendingAnswerInvite) {
    val inviteToAnswer = pendingAnswerInvite ?: return@LaunchedEffect
    onConsumePendingAnswerInvite()
    answerCallWithPermissions(inviteToAnswer)
  }

  LaunchedEffect(currentUserId, introFinished, settings.notificationsEnabled) {
    if (
      introFinished &&
      !hasRequestedNotifPermission &&
      settings.notificationsEnabled &&
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    ) {
      val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        hasRequestedNotifPermission = true
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
  }

  LaunchedEffect(pendingChatId) {
    if (!pendingChatId.isNullOrBlank() && MioConstants.SAFE_ID_REGEX.matches(pendingChatId)) {
      if (activeCall != null) {
        CallSessionHolder.setMinimized(true)
      }
      navController.navigate(MioRoutes.chatDetail(pendingChatId)) {
        launchSingleTop = true
      }
      onConsumePendingChatId()
    }
  }

  val totalUnreadCount = (chatsState as? UiState.Success)?.data
    ?.sumOf { it.unreadCount } ?: 0
  val pendingInviteCount = (chatsState as? UiState.Success)?.data
    ?.count { it.status == ChatRequestStatus.PENDING && it.initiatorUid != currentUserId } ?: 0
  val chatBadgeCount = totalUnreadCount + pendingInviteCount

  var openConnectDialogState by rememberSaveable { mutableStateOf(false) }

  val showChrome = currentRoute in setOf(
    MioRoutes.ROUTE_CHATS,
    MioRoutes.ROUTE_SETTINGS,
  )

  val currentActiveCall = activeCall
  val showMinimizedBanner = currentActiveCall != null && isCallMinimized

  Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
      if (showMinimizedBanner && currentActiveCall != null) {
        MinimizedCallBanner(
          callState = currentActiveCall,
          onExpand = { CallSessionHolder.setMinimized(false) },
          onToggleMic = { callManager.toggleMicMute() },
          onEndCall = { callManager.endCall(CallStatus.ENDED) },
          modifier = Modifier.background(MioColors.PureBlack),
        )
      }
      Scaffold(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .then(if (showMinimizedBanner) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
        containerColor = MioColors.PlumBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      if (showChrome) {
        MioDiscreteBottomBar(
          currentRoute = currentRoute,
          chatBadgeCount = chatBadgeCount,
          onTabSelected = { tab ->
            when (tab) {
              MioDiscreteTab.CHATS -> {
                navController.navigate(MioRoutes.ROUTE_CHATS) {
                  popUpTo(MioRoutes.ROUTE_CHATS) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              }
              MioDiscreteTab.PROFILE -> {
                navController.navigate(MioRoutes.ROUTE_SETTINGS) {
                  popUpTo(MioRoutes.ROUTE_CHATS) { saveState = true }
                  launchSingleTop = true
                  restoreState = true
                }
              }
            }
          },
        )
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
          chatWallpaperPath = settings.chatWallpaperPath,
          contentPadding = innerPadding,
          initialOpenConnectDialog = openConnectDialogState,
          onConnectDialogDismissed = { openConnectDialogState = false },
          onOpenChat = { chatId, fromCircle ->
            navController.navigate(MioRoutes.chatDetail(chatId, fromCircle))
          },
          onDeleteChatHistory = { chat -> chatListViewModel.clearChatHistory(chat) },
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

      composable(MioRoutes.ROUTE_SETTINGS) {
        val activeMipChat = (chatsState as? UiState.Success)?.data?.firstOrNull { it.isMipActive }
        SettingsScreen(
          profile = myProfile,
          keyFingerprint = keyFingerprint,
          settings = settings,
          blockedUsers = blockedUsers,
          activeMipPartnerName = activeMipChat?.partnerDisplayName,
          isSubmitting = profileSubmitting,
          actionError = profileError,
          contentPadding = innerPadding,
          onUpdateDisplayName = { newName ->
            profileViewModel.updateDisplayName(myProfile.username, newName)
          },
          onUpdateProfile = { displayName, bio, photoUrl ->
            profileViewModel.updateProfile(displayName, bio, photoUrl)
          },
          onChangeUsername = { oldName, newName, onSuccess ->
            profileViewModel.changeUsername(oldName, newName, onSuccess)
          },
          onCheckUsername = { username, callback ->
            profileViewModel.checkUsernameAvailability(username, callback)
          },
          onUnblockUser = { blockedUid ->
            scope.launch { chatRepo.unblockUserRecord(currentUserId, blockedUid) }
          },
          onUnlinkMip = {
            if (activeMipChat != null) {
              scope.launch {
                val db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id))
                val mipRepo = FirebaseMipRepository(db, cryptoService)
                mipRepo.unlinkMip(activeMipChat.chatId, myProfile)
              }
            }
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
          onToggleShowOnlineStatus = { enabled ->
            scope.launch { preferences.setShowOnlineStatus(enabled) }
          },
          onSetWallpaperPath = { path ->
            scope.launch { preferences.setChatWallpaperPath(path) }
          },
          onSetBio = { bio ->
            scope.launch { preferences.setBio(bio) }
          },
          onSetProfilePhotoPath = { path ->
            scope.launch { preferences.setProfilePhotoPath(path) }
          },
          onSetLastActiveEnabled = { enabled ->
            scope.launch { preferences.setLastActiveEnabled(enabled) }
          },
          onSetActiveNowEnabled = { enabled ->
            scope.launch { preferences.setActiveNowEnabled(enabled) }
          },
          onSetReadReceiptsEnabled = { enabled ->
            scope.launch { preferences.setReadReceiptsEnabled(enabled) }
          },
          onSetTypingIndicatorEnabled = { enabled ->
            scope.launch { preferences.setTypingIndicatorEnabled(enabled) }
          },
          onSetWhoCanMessageMe = { mode ->
            scope.launch { preferences.setWhoCanMessageMe(mode) }
          },
          onToggleRestrictedUsername = { username ->
            scope.launch { preferences.toggleRestrictedUsername(username) }
          },
          onSetDisappearingTimer = { timer ->
            scope.launch { preferences.setDisappearingTimer(timer) }
          },
          onSetScheduledMessagesEnabled = { enabled ->
            scope.launch { preferences.setScheduledMessagesEnabled(enabled) }
          },
          onSetMediaAutoDownload = { mode ->
            scope.launch { preferences.setMediaAutoDownload(mode) }
          },
          onSetWhoCanCallMe = { mode ->
            scope.launch { preferences.setWhoCanCallMe(mode) }
          },
          onSetCallDataSaverEnabled = { enabled ->
            scope.launch { preferences.setCallDataSaverEnabled(enabled) }
          },
          onSetCallRingtone = { ringtone ->
            scope.launch { preferences.setCallRingtone(ringtone) }
          },
          onSetMipNotificationSounds = { enabled ->
            scope.launch { preferences.setMipNotificationSounds(enabled) }
          },
          onSetMipRingtoneSounds = { enabled ->
            scope.launch { preferences.setMipRingtoneSounds(enabled) }
          },
          onSetMipHelloTune = { enabled ->
            scope.launch { preferences.setMipHelloTune(enabled) }
          },
          onSetMessagePreviewEnabled = { enabled ->
            scope.launch { preferences.setMessagePreviewEnabled(enabled) }
          },
          onSetCallNotificationsEnabled = { enabled ->
            scope.launch { preferences.setCallNotificationsEnabled(enabled) }
          },
          onSetBirthdayAlertsEnabled = { enabled ->
            scope.launch { preferences.setBirthdayAlertsEnabled(enabled) }
          },
          onSetMuteAllUntilMs = { ms ->
            scope.launch { preferences.setMuteAllUntilMs(ms) }
          },
          onSetAppLockEnabled = { enabled ->
            scope.launch { preferences.setAppLockEnabled(enabled) }
          },
          onSetAppLockType = { type ->
            scope.launch { preferences.setAppLockType(type) }
          },
          onSetAppLockPin = { pin ->
            scope.launch { preferences.setAppLockPin(pin) }
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
            arguments = listOf(
              navArgument("chatId") { type = NavType.StringType },
              navArgument("fromCircle") {
                type = NavType.BoolType
                defaultValue = false
              },
            ),
          ) { backStackEntry ->
            val rawChatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val chatId = rawChatId.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }.orEmpty()
            val fromCircle = backStackEntry.arguments?.getBoolean("fromCircle") ?: false
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
                    presenceService = presenceService,
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
              useWallpaperMode = fromCircle,
              chatWallpaperPath = settings.chatWallpaperPath,
              onBack = { navController.popBackStack() },
              onTextChanged = { text -> detailViewModel.onComposerTextChanged(text) },
              onClearTyping = { detailViewModel.clearTypingIndicator() },
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
              onClearChat = { chat -> detailViewModel.clearChatHistory(chat) },
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
              onStartAudioCall = { chat -> launchCallWithPermissions(chat, isVideo = false) },
              onStartVideoCall = { chat -> launchCallWithPermissions(chat, isVideo = true) },
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

  val currentIncomingInvite = incomingInvite ?: pendingIncomingInvite
  if (currentActiveCall != null && !isCallMinimized) {
    ActiveCallScreen(
      callState = currentActiveCall,
      onToggleMic = { callManager.toggleMicMute() },
      onToggleCamera = { callManager.toggleCameraEnabled() },
      onFlipCamera = { callManager.switchCamera() },
      onToggleSpeaker = { callManager.toggleSpeaker() },
      onMinimize = { CallSessionHolder.setMinimized(true) },
      onEndCall = { callManager.endCall(CallStatus.ENDED) },
    )
  } else if (currentActiveCall == null && currentIncomingInvite != null) {
    IncomingCallOverlay(
      invite = currentIncomingInvite,
      onAccept = {
        onConsumePendingIncomingInvite()
        answerCallWithPermissions(currentIncomingInvite)
      },
      onDecline = {
        onConsumePendingIncomingInvite()
        callManager.declineCall(currentIncomingInvite, currentUserId)
      },
    )
  }

  val activeCallNotice = callErrorNotice ?: callPermissionNotice
  if (activeCallNotice != null) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = {
        callManager.clearCallErrorNotice()
        callPermissionNotice = null
      },
      containerColor = MioColors.CardSurface,
      title = {
        Text(
          text = MioCopy.CALL_TYPE_VOICE,
          color = MioColors.PrimaryText,
        )
      },
      text = {
        Text(
          text = activeCallNotice,
          color = MioColors.MutedText,
        )
      },
      confirmButton = {
        androidx.compose.material3.TextButton(
          onClick = {
            callManager.clearCallErrorNotice()
            callPermissionNotice = null
          },
        ) {
          Text("OK", color = MioColors.RoseAccent)
        }
      },
    )
  }
  }
}


