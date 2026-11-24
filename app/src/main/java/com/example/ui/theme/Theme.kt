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
  primary = MioColors.BlushRose,
  onPrimary = MioColors.PlumBase,
  primaryContainer = MioColors.CrimsonPrimary,
  onPrimaryContainer = MioColors.WarmOffWhite,
  secondary = MioColors.TwilightBlue,
  onSecondary = MioColors.PlumBase,
  secondaryContainer = MioColors.MidnightSlate,
  onSecondaryContainer = MioColors.WarmOffWhite,
  tertiary = MioColors.BlushRose,
  onTertiary = MioColors.PlumBase,
  tertiaryContainer = MioColors.BurgundySurface,
  onTertiaryContainer = MioColors.WarmOffWhite,
  background = MioColors.PlumBase,
  onBackground = MioColors.WarmOffWhite,
  surface = MioColors.BurgundySurface,
  onSurface = MioColors.WarmOffWhite,
  surfaceVariant = MioColors.MidnightSlate,
  onSurfaceVariant = MioColors.MutedRose,
  surfaceTint = MioColors.PlumBase,
  surfaceContainerLowest = MioColors.PlumBase,
  surfaceContainerLow = MioColors.BurgundySurface,
  surfaceContainer = MioColors.BurgundySurface,
  surfaceContainerHigh = MioColors.MidnightSlate,
  surfaceContainerHighest = MioColors.MidnightSlate,
  outline = MioColors.SubtleBorder,
  outlineVariant = MioColors.SlateBorder,
  scrim = MioColors.PlumBase,
  error = MioColors.DangerEmber,
  onError = MioColors.PlumBase,
  errorContainer = MioColors.BurgundySurface,
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
