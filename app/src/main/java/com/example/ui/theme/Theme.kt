package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MioDarkColorScheme = darkColorScheme(
  primary = MioColors.RoseAccent,
  onPrimary = MioColors.PureBlack,
  primaryContainer = MioColors.RoseAccent,
  onPrimaryContainer = MioColors.PrimaryText,
  secondary = MioColors.TwilightBlue,
  onSecondary = MioColors.PureBlack,
  secondaryContainer = MioColors.CardSurface,
  onSecondaryContainer = MioColors.PrimaryText,
  tertiary = MioColors.RoseAccent,
  onTertiary = MioColors.PureBlack,
  tertiaryContainer = MioColors.CardSurface,
  onTertiaryContainer = MioColors.PrimaryText,
  background = MioColors.PureBlack,
  onBackground = MioColors.PrimaryText,
  surface = MioColors.CardSurface,
  onSurface = MioColors.PrimaryText,
  surfaceVariant = MioColors.CardSurface,
  onSurfaceVariant = MioColors.MutedText,
  surfaceTint = MioColors.PureBlack,
  surfaceContainerLowest = MioColors.PureBlack,
  surfaceContainerLow = MioColors.CardSurface,
  surfaceContainer = MioColors.CardSurface,
  surfaceContainerHigh = MioColors.CardSurface,
  surfaceContainerHighest = MioColors.CardSurface,
  outline = MioColors.SubtleBorder,
  outlineVariant = MioColors.SlateBorder,
  scrim = MioColors.PureBlack,
  error = MioColors.DangerEmber,
  onError = MioColors.PureBlack,
  errorContainer = MioColors.CardSurface,
  onErrorContainer = MioColors.DangerEmber,
)

private val MioShapes = Shapes(
  small = RoundedCornerShape(MioRadii.sm),
  medium = RoundedCornerShape(MioRadii.md),
  large = RoundedCornerShape(MioRadii.lg),
)

@Composable
fun MioTheme(
  content: @Composable () -> Unit,
) {
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        WindowCompat.getInsetsController(window, view).apply {
          isAppearanceLightStatusBars = false
          isAppearanceLightNavigationBars = false
        }
      }
    }
  }

  MaterialTheme(
    colorScheme = MioDarkColorScheme,
    typography = MioTypography,
    shapes = MioShapes,
    content = content,
  )
}
