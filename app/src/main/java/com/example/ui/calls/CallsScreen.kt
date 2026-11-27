package com.example.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MioColors

@Composable
fun CallsScreen(contentPadding: PaddingValues) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MioColors.PlumBase)
      .padding(contentPadding),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "coming soon",
      style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
      color = MioColors.MutedRose,
    )
  }
}
