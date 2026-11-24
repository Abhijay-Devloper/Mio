package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing

@Composable
fun MioLoadingState(
  message: String = "Opening your space...",
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(MioSpacing.md),
    ) {
      CircularProgressIndicator(
        color = MioColors.BlushRose,
        trackColor = MioColors.BurgundySurface,
        strokeWidth = 2.5.dp,
      )
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MioColors.MutedRose,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
fun MioEmptyState(
  title: String,
  body: String,
  icon: ImageVector = Icons.Outlined.FavoriteBorder,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(MioSpacing.xl),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier
        .widthIn(max = 420.dp)
        .fillMaxWidth(),
    ) {
      Surface(
        shape = RoundedCornerShape(MioRadii.pill),
        color = MioColors.BurgundySurface,
        border = BorderStroke(1.dp, MioColors.SubtleBorder),
        modifier = Modifier.size(68.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MioColors.BlushRose,
            modifier = Modifier.size(30.dp),
          )
        }
      }
      Spacer(Modifier.height(MioSpacing.md))
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MioColors.WarmOffWhite,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(MioSpacing.xs))
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MioColors.MutedRose,
        textAlign = TextAlign.Center,
      )
      if (actionLabel != null && onAction != null) {
        Spacer(Modifier.height(MioSpacing.lg))
        Button(
          onClick = onAction,
          colors = ButtonDefaults.buttonColors(
            containerColor = MioColors.CrimsonPrimary,
            contentColor = MioColors.WarmOffWhite,
          ),
          shape = RoundedCornerShape(MioRadii.pill),
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text(actionLabel)
        }
      }
    }
  }
}

@Composable
fun MioErrorState(
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(MioSpacing.xl),
    contentAlignment = Alignment.Center,
  ) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.BurgundySurface,
      border = BorderStroke(1.dp, MioColors.DangerEmber.copy(alpha = 0.55f)),
      modifier = Modifier
        .widthIn(max = 420.dp)
        .fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(MioSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Outlined.ErrorOutline,
          contentDescription = "Error",
          tint = MioColors.DangerEmber,
          modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(MioSpacing.sm))
        Text(
          text = "Could not load this view",
          style = MaterialTheme.typography.titleMedium,
          color = MioColors.WarmOffWhite,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MioSpacing.xs))
        Text(
          text = message.ifBlank { "Please check your connection and try again." },
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.MutedRose,
          textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(MioSpacing.md))
        Button(
          onClick = onRetry,
          colors = ButtonDefaults.buttonColors(
            containerColor = MioColors.CrimsonPrimary,
            contentColor = MioColors.WarmOffWhite,
          ),
          shape = RoundedCornerShape(MioRadii.pill),
          modifier = Modifier.heightIn(min = MioSpacing.minTouchTarget),
        ) {
          Text("Try again")
        }
      }
    }
  }
}
