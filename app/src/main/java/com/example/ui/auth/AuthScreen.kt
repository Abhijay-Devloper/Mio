package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.local.MioPreferences
import com.example.data.remote.toHumanMessage
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing
import com.example.ui.theme.PlusJakartaSansFamily
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenTerms: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var isSignUpMode by rememberSaveable { mutableStateOf(false) }
  var keepSignedIn by rememberSaveable { mutableStateOf(true) }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }
  var emailInput by rememberSaveable { mutableStateOf("") }
  var passwordInput by rememberSaveable { mutableStateOf("") }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  val preferences = remember(context) { MioPreferences(context.applicationContext) }

  fun submitEmailAuth() {
    val cleanEmail = emailInput.trim()
    if (cleanEmail.isBlank() || passwordInput.isBlank()) {
      statusMessage = null
      errorMessage = "Please enter your email address and password."
      return
    }
    errorMessage = null
    statusMessage = null
    isLoading = true
    scope.launch {
      try {
        if (isSignUpMode) {
          Firebase.auth.createUserWithEmailAndPassword(cleanEmail, passwordInput).await()
        } else {
          Firebase.auth.signInWithEmailAndPassword(cleanEmail, passwordInput).await()
        }
        preferences.setKeepSignedIn(keepSignedIn)
        isLoading = false
        onAuthSuccess()
      } catch (e: Exception) {
        isLoading = false
        errorMessage = toHumanMessage(
          e,
          if (isSignUpMode) "Could not create account." else "Email sign-in failed.",
        )
      }
    }
  }

  fun triggerPasswordReset() {
    val cleanEmail = emailInput.trim()
    if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
      statusMessage = null
      errorMessage = "Enter your email address above first so we can send a reset link."
      return
    }
    errorMessage = null
    statusMessage = null
    isLoading = true
    scope.launch {
      try {
        Firebase.auth.sendPasswordResetEmail(cleanEmail).await()
        isLoading = false
        statusMessage = "Password reset link sent to $cleanEmail."
      } catch (e: Exception) {
        isLoading = false
        errorMessage = toHumanMessage(e, "Unable to send password reset email right now.")
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MioColors.PureBlack),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = MioSpacing.lg, vertical = MioSpacing.xl),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Column(
        modifier = Modifier
          .widthIn(max = 400.dp)
          .fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
      ) {
        // Brand Row: Mio app icon + Mio
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
        ) {
          Image(
            painter = painterResource(id = R.drawable.mio_app_icon),
            contentDescription = "Mio App Icon",
            modifier = Modifier
              .size(34.dp)
              .clip(RoundedCornerShape(MioRadii.sm)),
            contentScale = ContentScale.Crop,
          )
          Text(
            text = "Mio",
            style = MaterialTheme.typography.titleLarge.copy(
              fontFamily = PlusJakartaSansFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 21.sp,
            ),
            color = MioColors.PrimaryText,
          )
        }

        Spacer(modifier = Modifier.height(MioSpacing.xl))

        // Heading & Subtitle
        Text(
          text = if (isSignUpMode) "Create your account" else "Welcome back",
          style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp,
          ),
          color = MioColors.PrimaryText,
        )
        Spacer(modifier = Modifier.height(MioSpacing.xxs))
        Text(
          text = if (isSignUpMode) {
            "Sign up to start private conversations on Mio."
          } else {
            "Sign in to continue to your private space."
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.MutedText,
        )

        Spacer(modifier = Modifier.height(MioSpacing.lg))

        // Single full-width Google sign-in button (GitHub removed)
        OutlinedButton(
          onClick = {
            errorMessage = null
            statusMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                scope.launch {
                  preferences.setKeepSignedIn(keepSignedIn)
                  isLoading = false
                  onAuthSuccess()
                }
              },
              onAuthError = { msg ->
                isLoading = false
                errorMessage = msg
              },
              scope = scope,
              onAuthCancelled = {
                isLoading = false
              },
            )
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(MioRadii.sm),
          border = BorderStroke(1.dp, MioColors.SubtleBorder),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MioColors.ReceivedBubble,
            contentColor = MioColors.PrimaryText,
            disabledContainerColor = MioColors.ReceivedBubble.copy(alpha = 0.6f),
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(MioSpacing.minTouchTarget)
            .testTag("google_sign_in_button"),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            GoogleMarkIcon(modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(MioSpacing.sm))
            Text(
              text = "Continue with Google",
              style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
              ),
              color = MioColors.PrimaryText,
            )
          }
        }

        Spacer(modifier = Modifier.height(MioSpacing.lg))

        // Divider: "or continue with email"
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MioColors.SubtleBorder,
          )
          Text(
            text = "or continue with email",
            style = MaterialTheme.typography.labelMedium,
            color = MioColors.MutedText,
            modifier = Modifier.padding(horizontal = MioSpacing.sm),
          )
          HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MioColors.SubtleBorder,
          )
        }

        Spacer(modifier = Modifier.height(MioSpacing.lg))

        if (errorMessage != null) {
          Surface(
            shape = RoundedCornerShape(MioRadii.sm),
            color = MioColors.DangerEmber.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.45f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = MioSpacing.md),
          ) {
            Text(
              text = errorMessage ?: "",
              style = MaterialTheme.typography.bodyMedium,
              color = MioColors.DangerEmber,
              modifier = Modifier.padding(MioSpacing.sm),
            )
          }
        }

        if (statusMessage != null) {
          Surface(
            shape = RoundedCornerShape(MioRadii.sm),
            color = MioColors.OnlineGreen.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, MioColors.OnlineGreen.copy(alpha = 0.45f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = MioSpacing.md),
          ) {
            Text(
              text = statusMessage ?: "",
              style = MaterialTheme.typography.bodyMedium,
              color = MioColors.OnlineGreen,
              modifier = Modifier.padding(MioSpacing.sm),
            )
          }
        }

        // Email address label + input
        Text(
          text = "Email address",
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
          color = MioColors.PrimaryText,
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
          value = emailInput,
          onValueChange = { emailInput = it },
          placeholder = {
            Text(
              text = "you@example.com",
              style = MaterialTheme.typography.bodyLarge,
              color = MioColors.MutedText,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Email,
              contentDescription = null,
              tint = MioColors.MutedText,
              modifier = Modifier.size(18.dp),
            )
          },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = MioColors.PrimaryText),
          shape = RoundedCornerShape(MioRadii.sm),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MioColors.CardSurface,
            unfocusedContainerColor = MioColors.CardSurface,
            focusedBorderColor = MioColors.RoseAccent,
            unfocusedBorderColor = MioColors.SubtleBorder,
            focusedTextColor = MioColors.PrimaryText,
            unfocusedTextColor = MioColors.PrimaryText,
            cursorColor = MioColors.RoseAccent,
          ),
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("email_input"),
        )

        Spacer(modifier = Modifier.height(MioSpacing.md))

        // Password label + Forgot password link
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Password",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MioColors.PrimaryText,
          )
          if (!isSignUpMode) {
            Text(
              text = "Forgot password?",
              style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
              ),
              color = MioColors.RoseAccent,
              modifier = Modifier
                .clip(RoundedCornerShape(MioRadii.sm))
                .clickable(enabled = !isLoading) { triggerPasswordReset() }
                .padding(horizontal = MioSpacing.xxs, vertical = MioSpacing.xxs)
                .testTag("forgot_password_button"),
            )
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
          value = passwordInput,
          onValueChange = { passwordInput = it },
          placeholder = {
            Text(
              text = "••••••••••",
              style = MaterialTheme.typography.bodyLarge,
              color = MioColors.MutedText,
            )
          },
          leadingIcon = {
            Icon(
              imageVector = Icons.Outlined.Lock,
              contentDescription = null,
              tint = MioColors.MutedText,
              modifier = Modifier.size(18.dp),
            )
          },
          trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
              Icon(
                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                tint = MioColors.MutedText,
                modifier = Modifier.size(18.dp),
              )
            }
          },
          singleLine = true,
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = MioColors.PrimaryText),
          shape = RoundedCornerShape(MioRadii.sm),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MioColors.CardSurface,
            unfocusedContainerColor = MioColors.CardSurface,
            focusedBorderColor = MioColors.RoseAccent,
            unfocusedBorderColor = MioColors.SubtleBorder,
            focusedTextColor = MioColors.PrimaryText,
            unfocusedTextColor = MioColors.PrimaryText,
            cursorColor = MioColors.RoseAccent,
          ),
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
          ),
          keyboardActions = KeyboardActions(
            onDone = { submitEmailAuth() },
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("password_input"),
        )

        Spacer(modifier = Modifier.height(MioSpacing.sm))

        // Keep me signed in row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MioSpacing.minTouchTarget)
            .clip(RoundedCornerShape(MioRadii.sm))
            .clickable { keepSignedIn = !keepSignedIn },
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = keepSignedIn,
            onCheckedChange = { keepSignedIn = it },
            colors = CheckboxDefaults.colors(
              checkedColor = MioColors.RoseAccent,
              uncheckedColor = MioColors.SubtleBorder,
              checkmarkColor = MioColors.PureBlack,
            ),
          )
          Text(
            text = "Keep me signed in",
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.MutedText,
          )
        }

        Spacer(modifier = Modifier.height(MioSpacing.md))

        // Primary Sign In / Create Account button
        Button(
          onClick = { submitEmailAuth() },
          enabled = !isLoading,
          shape = RoundedCornerShape(MioRadii.sm),
          colors = ButtonDefaults.buttonColors(
            containerColor = MioColors.RoseAccent,
            contentColor = MioColors.PureBlack,
            disabledContainerColor = MioColors.RoseAccent.copy(alpha = 0.5f),
            disabledContentColor = MioColors.PureBlack.copy(alpha = 0.7f),
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(MioSpacing.minTouchTarget)
            .testTag("email_sign_in_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MioColors.PureBlack,
              strokeWidth = 2.dp,
            )
          } else {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Text(
                text = if (isSignUpMode) "Create account" else "Sign in",
                style = MaterialTheme.typography.titleMedium.copy(
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 15.sp,
                ),
              )
              Spacer(modifier = Modifier.width(MioSpacing.xs))
              Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(MioSpacing.lg))

        // Account mode switch row ("Don't have an account? Create one for free")
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MioSpacing.minTouchTarget),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = if (isSignUpMode) "Already have an account? " else "Don't have an account? ",
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.MutedText,
          )
          Text(
            text = if (isSignUpMode) "Sign in" else "Create one for free",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MioColors.RoseAccent,
            modifier = Modifier
              .clip(RoundedCornerShape(MioRadii.sm))
              .clickable {
                isSignUpMode = !isSignUpMode
                errorMessage = null
                statusMessage = null
              }
              .padding(horizontal = MioSpacing.xxs, vertical = MioSpacing.xs)
              .testTag("auth_mode_toggle"),
          )
        }

        Spacer(modifier = Modifier.height(MioSpacing.md))

        // Terms & Privacy links
        val legalText = buildAnnotatedString {
          append("By continuing, you accept our ")
          pushStringAnnotation(tag = "TERMS", annotation = "terms")
          withStyle(SpanStyle(color = MioColors.PrimaryText, textDecoration = TextDecoration.Underline)) {
            append("Terms of Service")
          }
          pop()
          append(" and ")
          pushStringAnnotation(tag = "PRIVACY", annotation = "privacy")
          withStyle(SpanStyle(color = MioColors.PrimaryText, textDecoration = TextDecoration.Underline)) {
            append("Privacy Policy")
          }
          pop()
          append(".")
        }

        @Suppress("DEPRECATION")
        androidx.compose.foundation.text.ClickableText(
          text = legalText,
          style = MaterialTheme.typography.labelMedium.copy(
            color = MioColors.MutedText,
            textAlign = TextAlign.Center,
          ),
          onClick = { offset ->
            legalText.getStringAnnotations(tag = "TERMS", start = offset, end = offset)
              .firstOrNull()?.let { onOpenTerms() }
            legalText.getStringAnnotations(tag = "PRIVACY", start = offset, end = offset)
              .firstOrNull()?.let { onOpenPrivacy() }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_legal_text"),
        )
      }
    }
  }
}

@Composable
private fun GoogleMarkIcon(modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val strokeWidth = size.minDimension * 0.20f
    val inset = strokeWidth / 2f
    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
    val topLeft = Offset(inset, inset)

    // Red top arc
    drawArc(
      color = Color(0xFFEA4335),
      startAngle = -145f,
      sweepAngle = 100f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
    )
    // Yellow left arc
    drawArc(
      color = Color(0xFFFBBC05),
      startAngle = 140f,
      sweepAngle = 75f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
    )
    // Green bottom arc
    drawArc(
      color = Color(0xFF34A853),
      startAngle = 40f,
      sweepAngle = 100f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
    )
    // Blue right arc
    drawArc(
      color = Color(0xFF4285F4),
      startAngle = 0f,
      sweepAngle = 48f,
      useCenter = false,
      topLeft = topLeft,
      size = arcSize,
      style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
    )
    // Blue horizontal bar
    drawLine(
      color = Color(0xFF4285F4),
      start = Offset(size.width * 0.52f, size.height * 0.5f),
      end = Offset(size.width - inset, size.height * 0.5f),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Butt,
    )
  }
}
