package com.example.ui.auth

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing

@Composable
fun OnboardingScreen(
  defaultDisplayName: String,
  isSubmitting: Boolean,
  errorMessage: String?,
  onSubmit: (username: String, displayName: String, ageConfirmed: Boolean) -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenTerms: () -> Unit,
  onSignOut: () -> Unit,
) {
  var username by rememberSaveable { mutableStateOf("") }
  var displayName by rememberSaveable(defaultDisplayName) {
    mutableStateOf(defaultDisplayName.take(MioConstants.MAX_DISPLAY_NAME_LENGTH))
  }
  var ageConfirmed by rememberSaveable { mutableStateOf(false) }

  val normalizedUsername = username.trim().lowercase()
  val isReserved = normalizedUsername in MioConstants.RESERVED_USERNAMES
  val isUsernameValid = MioConstants.USERNAME_REGEX.matches(normalizedUsername) && !isReserved
  val isDisplayValid = displayName.trim().length in 1..MioConstants.MAX_DISPLAY_NAME_LENGTH
  val canSubmit = isUsernameValid && isDisplayValid && ageConfirmed && !isSubmitting

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MioColors.PlumBase)
      .safeDrawingPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 520.dp)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(MioSpacing.lg),
      verticalArrangement = Arrangement.spacedBy(MioSpacing.md),
    ) {
      Text(
        text = MioCopy.ONBOARDING_TITLE,
        style = MaterialTheme.typography.headlineMedium,
        color = MioColors.WarmOffWhite,
      )
      Text(
        text = MioCopy.ONBOARDING_SUBTITLE,
        style = MaterialTheme.typography.bodyMedium,
        color = MioColors.MutedRose,
      )

      OutlinedTextField(
        value = username,
        onValueChange = { input ->
          username = input.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
            .take(MioConstants.MAX_USERNAME_LENGTH)
        },
        label = { Text("Unique @username") },
        prefix = { Text("@", color = MioColors.BlushRose) },
        supportingText = {
          val hintText = when {
            isReserved -> "That handle is reserved. Please choose another."
            else -> MioCopy.USERNAME_HINT
          }
          Text(
            text = hintText,
            color = if (username.isEmpty() || isUsernameValid) MioColors.MutedRose else MioColors.DangerEmber,
          )
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MioColors.BlushRose,
          unfocusedBorderColor = MioColors.SubtleBorder,
          focusedLabelColor = MioColors.BlushRose,
          unfocusedLabelColor = MioColors.MutedRose,
          focusedTextColor = MioColors.WarmOffWhite,
          unfocusedTextColor = MioColors.WarmOffWhite,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("onboarding_username_input"),
      )

      OutlinedTextField(
        value = displayName,
        onValueChange = { displayName = it.take(MioConstants.MAX_DISPLAY_NAME_LENGTH) },
        label = { Text("Your display name") },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = MioColors.BlushRose,
          unfocusedBorderColor = MioColors.SubtleBorder,
          focusedLabelColor = MioColors.BlushRose,
          unfocusedLabelColor = MioColors.MutedRose,
          focusedTextColor = MioColors.WarmOffWhite,
          unfocusedTextColor = MioColors.WarmOffWhite,
        ),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("onboarding_display_name_input"),
      )

      Surface(
        shape = RoundedCornerShape(MioRadii.md),
        color = MioColors.BurgundySurface,
        border = BorderStroke(1.dp, MioColors.SubtleBorder),
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = MioSpacing.minTouchTarget)
          .clickable { ageConfirmed = !ageConfirmed }
          .testTag("onboarding_age_checkbox_row"),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = MioSpacing.sm, vertical = MioSpacing.sm),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Checkbox(
            checked = ageConfirmed,
            onCheckedChange = { ageConfirmed = it },
            colors = CheckboxDefaults.colors(
              checkedColor = MioColors.BlushRose,
              checkmarkColor = MioColors.PlumBase,
              uncheckedColor = MioColors.MutedRose,
            ),
            modifier = Modifier.testTag("onboarding_age_checkbox"),
          )
          Text(
            text = MioCopy.AGE_CONFIRMATION_LABEL,
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.WarmOffWhite,
          )
        }
      }

      Text(
        text = MioCopy.DEVICE_HISTORY_NOTICE,
        style = MaterialTheme.typography.labelMedium,
        color = MioColors.TwilightBlue,
      )

      if (errorMessage != null) {
        Surface(
          shape = RoundedCornerShape(MioRadii.sm),
          color = MioColors.DangerEmber.copy(alpha = 0.14f),
          border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.DangerEmber,
            modifier = Modifier.padding(MioSpacing.sm),
          )
        }
      }

      Spacer(Modifier.height(MioSpacing.xs))

      Button(
        onClick = { onSubmit(normalizedUsername, displayName.trim(), ageConfirmed) },
        enabled = canSubmit,
        colors = ButtonDefaults.buttonColors(
          containerColor = MioColors.CrimsonPrimary,
          contentColor = MioColors.WarmOffWhite,
          disabledContainerColor = MioColors.MidnightSlate,
          disabledContentColor = MioColors.MutedRose,
        ),
        shape = RoundedCornerShape(MioRadii.pill),
        modifier = Modifier
          .fillMaxWidth()
          .height(54.dp)
          .testTag("onboarding_submit_button"),
      ) {
        if (isSubmitting) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = MioColors.WarmOffWhite,
            strokeWidth = 2.dp,
          )
        } else {
          Text("Enter Mio", style = MaterialTheme.typography.titleMedium)
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row {
          TextButton(
            onClick = onOpenPrivacy,
            modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
          ) {
            Text("Privacy", color = MioColors.TwilightBlue)
          }
          TextButton(
            onClick = onOpenTerms,
            modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
          ) {
            Text("Terms", color = MioColors.TwilightBlue)
          }
        }
        TextButton(
          onClick = onSignOut,
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Switch account", color = MioColors.BlushRose)
        }
      }
    }
  }
}
