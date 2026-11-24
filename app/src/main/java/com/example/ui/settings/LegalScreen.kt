package com.example.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.config.MioCopy
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing

enum class LegalDocumentType {
  PRIVACY,
  TERMS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalScreen(
  type: LegalDocumentType,
  onBack: () -> Unit,
) {
  val title = if (type == LegalDocumentType.PRIVACY) "Privacy Policy" else "Terms of Service"
  val sections = if (type == LegalDocumentType.PRIVACY) {
    listOf(
      "What Mio stores in the cloud" to
        "Mio uses Firebase Authentication (Google Sign-In) and Cloud Firestore to route your profile handle (@username), public encryption key, chat invitations, typing state, read/delivery receipts, and end-to-end encrypted message relay payloads.",
      "End-to-end encryption & your device" to
        "Your private ECDH encryption key is wrapped using the hardware/OS-backed Android Keystore and stored solely on your phone. Mio does not upload your private key or plaintext messages to cloud logs, analytics, ad networks, or push notifications. Message history lives locally on your device.",
      "Retention & automatic relay cleanup" to
        "Encrypted message relay documents carry a 30-day expiry and are automatically purged by the client on launch. Mio includes zero third-party advertising SDKs or behavioral tracking.",
      "18+ confirmation & safety reports" to
        "We store only your 18+ confirmation flag and its timestamp—never a birth date or phone number. If you submit a safety report, we store the selected reason and any excerpt you choose to attach.",
      "Account deletion & security contact" to
        "You can permanently delete your Mio account at any time in Settings. Deleting your account removes your @username reservation, profile, blocklist, chat threads, message relay documents, MIP link, local device message history, Android Keystore key, and Firebase Auth record. For privacy or security inquiries, contact privacy@mio.example.",
    )
  } else {
    listOf(
      "Eligibility (18+)" to
        "Mio is designed for adults 18 years of age and older. By creating a handle, you confirm that you meet this requirement.",
      "Respectful & consensual connection" to
        "You can connect only with people whose exact @username you know. Do not harass, impersonate, or repeatedly contact someone who has declined or blocked you. Blocked users cannot send chat requests, messages, or MIP sealed letters.",
      "Most Important Person (MIP)" to
        "You may link with one MIP at a time inside an accepted, unblocked conversation. Unlinking initiates a 24-hour quiet cooldown before sealing a new MIP letter.",
      "Device-only history" to
        "In Phase 1, your decrypted message history is stored locally on your device without cloud key backup. Uninstalling Mio or wiping app storage permanently clears local messages.",
    )
  }

  Scaffold(
    containerColor = MioColors.PlumBase,
    topBar = {
      TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("legal_back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
              contentDescription = "Back",
              tint = MioColors.WarmOffWhite,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MioColors.MidnightSlate,
          titleContentColor = MioColors.WarmOffWhite,
        ),
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(MioColors.PlumBase)
        .padding(innerPadding)
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(MioSpacing.md),
      verticalArrangement = Arrangement.spacedBy(MioSpacing.md),
    ) {
      Surface(
        shape = RoundedCornerShape(MioRadii.pill),
        color = MioColors.MidnightSlate,
        border = BorderStroke(1.dp, MioColors.SlateBorder),
      ) {
        Text(
          text = MioCopy.PRIVACY_DRAFT_BADGE,
          style = MaterialTheme.typography.labelMedium,
          color = MioColors.TwilightBlue,
          modifier = Modifier.padding(horizontal = MioSpacing.md, vertical = MioSpacing.xs),
        )
      }

      sections.forEach { (heading, body) ->
        Surface(
          shape = RoundedCornerShape(MioRadii.md),
          color = MioColors.BurgundySurface,
          border = BorderStroke(1.dp, MioColors.SubtleBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(MioSpacing.md)) {
            Text(
              text = heading,
              style = MaterialTheme.typography.titleMedium,
              color = MioColors.BlushRose,
            )
            Spacer(Modifier.height(MioSpacing.xs))
            Text(
              text = body,
              style = MaterialTheme.typography.bodyMedium,
              color = MioColors.WarmOffWhite,
            )
          }
        }
      }
    }
  }
}
