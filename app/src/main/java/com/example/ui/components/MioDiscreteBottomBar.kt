package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MioColors

enum class MioDiscreteTab(
  val route: String,
  val label: String,
  val iconRes: Int,
  val activeColor: Color,
) {
  CHATS(
    route = "chats",
    label = "Chats",
    iconRes = com.example.R.drawable.ic_nav_chats,
    activeColor = Color(0xFFFE8492), // Blush Rose
  ),
  PROFILE(
    route = "settings",
    label = "Profile",
    iconRes = com.example.R.drawable.ic_nav_profile,
    activeColor = Color(0xFFF8E8EE), // Warm Off-White
  ),
}

@Composable
fun MioDiscreteBottomBar(
  currentRoute: String?,
  chatBadgeCount: Int,
  onTabSelected: (MioDiscreteTab) -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 24.dp, vertical = 14.dp)
      .navigationBarsPadding(),
    contentAlignment = Alignment.Center,
  ) {
    // Liquid Glass Container with Frosted Translucency & Gradient Rim
    Surface(
      modifier = Modifier
        .clip(RoundedCornerShape(36.dp))
        .border(
          BorderStroke(
            1.5.dp,
            Brush.linearGradient(
              colors = listOf(
                MioColors.BlushRose.copy(alpha = 0.55f),
                MioColors.TwilightBlue.copy(alpha = 0.35f),
                MioColors.BurgundySurface.copy(alpha = 0.2f),
              )
            )
          ),
          RoundedCornerShape(36.dp)
        ),
      color = MioColors.MidnightSlate.copy(alpha = 0.82f),
      shadowElevation = 24.dp,
      tonalElevation = 24.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        MioDiscreteTab.entries.forEach { tab ->
          val isSelected = when (tab) {
            MioDiscreteTab.CHATS -> currentRoute == "chats"
            MioDiscreteTab.PROFILE -> currentRoute == "settings"
          }

          val tintColor by animateColorAsState(
            targetValue = if (isSelected) tab.activeColor else MioColors.MutedRose,
            animationSpec = spring(),
            label = "TabTint",
          )

          val pillBrush = if (isSelected) {
            Brush.horizontalGradient(
              colors = listOf(
                tab.activeColor.copy(alpha = 0.30f),
                tab.activeColor.copy(alpha = 0.10f),
              )
            )
          } else {
            Brush.horizontalGradient(
              colors = listOf(Color.Transparent, Color.Transparent)
            )
          }

          Surface(
            modifier = Modifier
              .clip(RoundedCornerShape(28.dp))
              .clickable { onTabSelected(tab) }
              .border(
                BorderStroke(
                  width = if (isSelected) 1.dp else 0.dp,
                  color = if (isSelected) tab.activeColor.copy(alpha = 0.45f) else Color.Transparent,
                ),
                shape = RoundedCornerShape(28.dp)
              )
              .testTag("nav_${tab.route}"),
            color = Color.Transparent,
            shape = RoundedCornerShape(28.dp),
          ) {
            Box(
              modifier = Modifier
                .background(pillBrush, RoundedCornerShape(28.dp))
                .padding(horizontal = 18.dp, vertical = 10.dp),
              contentAlignment = Alignment.Center,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
              ) {
                if (tab == MioDiscreteTab.CHATS && chatBadgeCount > 0) {
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
                    Icon(
                      painter = painterResource(id = tab.iconRes),
                      contentDescription = tab.label,
                      tint = tintColor,
                      modifier = Modifier.size(22.dp),
                    )
                  }
                } else {
                  Icon(
                    painter = painterResource(id = tab.iconRes),
                    contentDescription = tab.label,
                    tint = tintColor,
                    modifier = Modifier.size(22.dp),
                  )
                }

                AnimatedVisibility(
                  visible = isSelected,
                  enter = fadeIn(animationSpec = spring()) + expandHorizontally(animationSpec = spring(), expandFrom = Alignment.Start),
                  exit = fadeOut(animationSpec = spring()) + shrinkHorizontally(animationSpec = spring(), shrinkTowards = Alignment.Start),
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = tab.label,
                      color = tintColor,
                      style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                      ),
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
