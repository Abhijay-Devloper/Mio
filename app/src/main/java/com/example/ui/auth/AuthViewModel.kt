package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.remote.AuthService
import com.example.data.remote.toHumanMessage
import com.example.data.remote.unwrapFirestoreError
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope,
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (_: Exception) {
    onUnauthenticated()
    return
  }
  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(clientId)
    .setAutoSelectEnabled(true)
    .build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

  scope.launch {
    try {
      val cred = credentialManager.getCredential(context, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (_: Exception) {
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {},
) {
  val clientId = try {
    context.getString(R.string.default_web_client_id)
  } catch (_: Exception) {
    onAuthError("Google Sign-In configuration missing.")
    return
  }
  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(signInOption)
    .build()

  scope.launch {
    try {
      val activityContext = context as? Activity ?: context
      val cred = credentialManager.getCredential(activityContext, request).credential
      if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
        Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected sign-in credential type.")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w("Auth", "Google Sign-In cancelled: ${e.javaClass.simpleName}")
      onAuthCancelled()
    } catch (e: NoCredentialException) {
      Log.w("Auth", "No Google account available: ${e.javaClass.simpleName}")
      onAuthError("No Google account is signed in on this device yet.")
    } catch (e: Exception) {
      Log.w("Auth", "Google Sign-In failed: ${e.javaClass.simpleName}")
      onAuthError(toHumanMessage(e, "Sign in failed. Please check your connection and try again."))
    }
  }
}

fun performSignOut(
  credentialManager: CredentialManager,
  scope: CoroutineScope,
  onClearLocalArtifacts: suspend () -> Unit = {},
  onSignOutComplete: () -> Unit = {},
) {
  scope.launch {
    withContext(NonCancellable) {
      try {
        onClearLocalArtifacts()
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
      } catch (e: Exception) {
        Log.w("Auth", "Failed to clear credential state: ${e.javaClass.simpleName}")
      } finally {
        Firebase.auth.signOut()
        onSignOutComplete()
      }
    }
  }
}

class ProfileViewModel(
  private val authService: AuthService,
  private val currentUserId: String,
) : ViewModel() {

  val profileState: StateFlow<UiState<UserProfile?>> = authService.observeUserProfile(currentUserId)
    .map<UserProfile?, UiState<UserProfile?>> { profile ->
      if (profile != null) {
        viewModelScope.launch { authService.syncPublicKeyIfNeeded(profile) }
      }
      UiState.Success(profile)
    }
    .catch { error ->
      val root = unwrapFirestoreError(error) ?: throw error
      emit(UiState.Error(toHumanMessage(root, "Unable to load your profile.")))
    }
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = UiState.Loading,
    )

  private val _actionError = MutableStateFlow<String?>(null)
  val actionError: StateFlow<String?> = _actionError.asStateFlow()

  private val _isSubmitting = MutableStateFlow(false)
  val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

  fun clearError() {
    _actionError.value = null
  }

  fun claimUsernameAndCreateProfile(
    username: String,
    displayName: String,
    ageConfirmed: Boolean,
  ) {
    viewModelScope.launch {
      _isSubmitting.value = true
      _actionError.value = null
      val result = authService.claimUsernameAndCreateProfile(
        uid = currentUserId,
        rawUsername = username,
        displayName = displayName,
        ageConfirmed = ageConfirmed,
      )
      result.onFailure { err ->
        _actionError.value = toHumanMessage(err, "Could not claim username. Please try again.")
      }
      _isSubmitting.value = false
    }
  }

  fun updateDisplayName(username: String, displayName: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _isSubmitting.value = true
      _actionError.value = null
      val result = authService.updateDisplayName(currentUserId, username, displayName)
      result.onSuccess { onSuccess() }
        .onFailure { err -> _actionError.value = toHumanMessage(err, "Could not update name.") }
      _isSubmitting.value = false
    }
  }

  fun updateProfile(
    displayName: String,
    bio: String,
    photoUrl: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isSubmitting.value = true
      _actionError.value = null
      val result = authService.updateProfile(currentUserId, displayName, bio, photoUrl)
      result.onSuccess { onSuccess() }
        .onFailure { err -> _actionError.value = toHumanMessage(err, "Could not update profile.") }
      _isSubmitting.value = false
    }
  }

  fun checkUsernameAvailability(username: String, onResult: (Boolean) -> Unit) {
    viewModelScope.launch {
      val result = authService.checkUsernameAvailable(username)
      onResult(result.getOrDefault(false))
    }
  }

  fun changeUsername(
    oldUsername: String,
    newUsername: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isSubmitting.value = true
      _actionError.value = null
      val result = authService.changeUsername(currentUserId, oldUsername, newUsername)
      result.onSuccess { onSuccess() }
        .onFailure { err -> _actionError.value = toHumanMessage(err, "Could not change username.") }
      _isSubmitting.value = false
    }
  }

  fun syncCurrentFcmTokenIfNeeded(currentSavedToken: String, pendingToken: String? = null) {
    viewModelScope.launch {
      runCatching {
        val fetched = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull().orEmpty()
        val token = fetched.ifBlank { pendingToken.orEmpty() }
        if (token.isNotBlank() && token != currentSavedToken) {
          authService.syncFcmToken(currentUserId, token)
        }
      }
    }
  }

  fun syncNotificationPreferences(
    notificationsEnabled: Boolean,
    callNotificationsEnabled: Boolean,
    muteAllUntilMs: Long,
  ) {
    viewModelScope.launch {
      authService.syncNotificationPreferences(
        uid = currentUserId,
        notificationsEnabled = notificationsEnabled,
        callNotificationsEnabled = callNotificationsEnabled,
        muteAllUntilMs = muteAllUntilMs,
      )
    }
  }

  suspend fun clearFcmTokenForSignOut() {
    authService.clearFcmToken(currentUserId)
  }

  fun deleteAccount(username: String, onSuccess: () -> Unit) {
    viewModelScope.launch {
      _isSubmitting.value = true
      _actionError.value = null
      val result = authService.deleteAccount(currentUserId, username)
      result.onSuccess { onSuccess() }
        .onFailure { err ->
          _actionError.value = toHumanMessage(err, "Could not delete account right now.")
        }
      _isSubmitting.value = false
    }
  }
}
