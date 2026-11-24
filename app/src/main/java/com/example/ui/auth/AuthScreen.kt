package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.core.config.CatMood
import com.example.core.config.MioCopy
import com.example.ui.components.MoodCatArtwork
import com.example.ui.components.WaxSealEmblem
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioGradients
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing

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
  var errorMessage by remember { mutableStateOf<String?>(null) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MioGradients.sunsetVertical)
      .safeDrawingPadding()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = MioSpacing.lg, vertical = MioSpacing.lg),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.PlumBase.copy(alpha = 0.88f),
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      modifier = Modifier
        .widthIn(max = 480.dp)
        .fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MioSpacing.lg, vertical = MioSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        // Decorative Mio seal + happy cat vignette
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MioSpacing.md),
        ) {
          WaxSealEmblem(size = 64.dp)
          MoodCatArtwork(mood = CatMood.HAPPY, size = 60.dp)
        }

        Spacer(Modifier.height(MioSpacing.lg))

        Text(
          text = MioCopy.APP_NAME,
          style = MaterialTheme.typography.displayLarge,
          color = MioColors.WarmOffWhite,
        )
        Spacer(Modifier.height(MioSpacing.xs))
        Text(
          text = MioCopy.TAGLINE,
          style = MaterialTheme.typography.titleLarge,
          color = MioColors.BlushRose,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MioSpacing.md))
        Text(
          text = MioCopy.SIGN_IN_SUBTITLE,
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.MutedRose,
          textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(MioSpacing.lg))

        Surface(
          shape = RoundedCornerShape(MioRadii.md),
          color = MioColors.BurgundySurface,
          border = BorderStroke(1.dp, MioColors.SubtleBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(MioSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
          ) {
            Icon(
              imageVector = Icons.Outlined.Lock,
              contentDescription = "End-to-end encrypted",
              tint = MioColors.TwilightBlue,
              modifier = Modifier.size(20.dp),
            )
            Text(
              text = MioCopy.DEVICE_HISTORY_NOTICE,
              style = MaterialTheme.typography.labelMedium,
              color = MioColors.WarmOffWhite,
            )
          }
        }

        if (errorMessage != null) {
          Spacer(Modifier.height(MioSpacing.md))
          Surface(
            shape = RoundedCornerShape(MioRadii.md),
            color = MioColors.DangerEmber.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = errorMessage ?: "",
              style = MaterialTheme.typography.bodyMedium,
              color = MioColors.DangerEmber,
              modifier = Modifier.padding(MioSpacing.sm),
              textAlign = TextAlign.Center,
            )
          }
        }

        Spacer(Modifier.height(MioSpacing.lg))

        Button(
          onClick = {
            errorMessage = null
            isLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isLoading = false
                onAuthSuccess()
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
          colors = ButtonDefaults.buttonColors(
            containerColor = MioColors.CrimsonPrimary,
            contentColor = MioColors.WarmOffWhite,
            disabledContainerColor = MioColors.BurgundySurface,
          ),
          shape = RoundedCornerShape(MioRadii.pill),
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(MioRadii.pill))
            .testTag("google_sign_in_button"),
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(22.dp),
              color = MioColors.WarmOffWhite,
              strokeWidth = 2.dp,
            )
          } else {
            Text(
              text = MioCopy.CONTINUE_WITH_GOOGLE,
              style = MaterialTheme.typography.titleMedium,
            )
          }
        }

        Spacer(Modifier.height(MioSpacing.md))

        Row(
          horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(
            onClick = onOpenPrivacy,
            modifier = Modifier
              .heightIn(min = MioSpacing.minTouchTarget)
              .testTag("auth_privacy_button"),
          ) {
            Text("Privacy Policy", color = MioColors.TwilightBlue)
          }
          Text("·", color = MioColors.MutedRose)
          TextButton(
            onClick = onOpenTerms,
            modifier = Modifier
              .heightIn(min = MioSpacing.minTouchTarget)
              .testTag("auth_terms_button"),
          ) {
            Text("Terms", color = MioColors.TwilightBlue)
          }
        }
      }
    }
  }
}
