package com.example.ui.components

import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.core.config.MioConstants
import com.example.ui.theme.MioColors
import kotlinx.coroutines.delay

/**
 * Plays the bundled 16:9 opening logo video once at app launch.
 * All screen area outside the 16:9 frame uses true AMOLED #000000 pure black.
 */
@Composable
fun OpeningIntroVideo(
  onFinished: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val latestOnFinished by rememberUpdatedState(onFinished)
  val lifecycleOwner = LocalLifecycleOwner.current

  var completed by remember { mutableStateOf(false) }
  var hasStartedPlayback by remember { mutableStateOf(false) }
  var isFirstFrameRendered by remember { mutableStateOf(false) }
  var isPaused by remember { mutableStateOf(false) }

  val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
  val surfaceRef = remember { mutableStateOf<Surface?>(null) }

  fun completeOnce() {
    if (!completed) {
      completed = true
      latestOnFinished()
    }
  }

  fun releasePlayer() {
    runCatching {
      mediaPlayerRef.value?.let { player ->
        if (player.isPlaying) {
          player.stop()
        }
        player.reset()
        player.release()
      }
    }
    mediaPlayerRef.value = null
    runCatching { surfaceRef.value?.release() }
    surfaceRef.value = null
  }

  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_PAUSE -> {
          isPaused = true
          runCatching {
            val player = mediaPlayerRef.value
            if (player != null && player.isPlaying) {
              player.pause()
            }
          }
        }
        Lifecycle.Event.ON_RESUME -> {
          isPaused = false
          runCatching {
            val player = mediaPlayerRef.value
            if (player != null && hasStartedPlayback && !completed && !player.isPlaying) {
              player.start()
            }
          }
        }
        Lifecycle.Event.ON_DESTROY -> releasePlayer()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      releasePlayer()
    }
  }

  // Fallback frame reveal in OEM decoders that omit MEDIA_INFO_VIDEO_RENDERING_START.
  LaunchedEffect(hasStartedPlayback) {
    if (hasStartedPlayback && !isFirstFrameRendered) {
      delay(90L)
      isFirstFrameRendered = true
    }
  }

  // Safety timeout so broken or headless media decoders never block entry into Mio.
  LaunchedEffect(isPaused, completed) {
    if (!isPaused && !completed) {
      delay(MioConstants.INTRO_SAFETY_TIMEOUT_MS)
      completeOnce()
    }
  }

  BoxWithConstraints(
    contentAlignment = Alignment.Center,
    modifier = modifier
      .fillMaxSize()
      .background(MioColors.PureBlack)
      .semantics { contentDescription = "Mio opening animation" }
      .testTag("opening_intro_overlay"),
  ) {
    val aspect = MioConstants.INTRO_VIDEO_ASPECT_RATIO
    val videoFrameModifier = if (maxHeight > 0.dp && (maxWidth / maxHeight) > aspect) {
      Modifier.fillMaxHeight().aspectRatio(aspect)
    } else {
      Modifier.fillMaxWidth().aspectRatio(aspect)
    }

    Box(
      contentAlignment = Alignment.Center,
      modifier = videoFrameModifier
        .background(MioColors.PureBlack)
        .testTag("opening_intro_video_frame"),
    ) {
      AndroidView(
        factory = { viewContext ->
          TextureView(viewContext).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
            isOpaque = true
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
              override fun onSurfaceTextureAvailable(
                surfaceTexture: android.graphics.SurfaceTexture,
                width: Int,
                height: Int,
              ) {
                if (completed) return
                releasePlayer()
                val surface = Surface(surfaceTexture)
                surfaceRef.value = surface
                val player = MediaPlayer()
                mediaPlayerRef.value = player
                val setupResult = runCatching {
                  viewContext.resources.openRawResourceFd(R.raw.mio_intro).use { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                  }
                  player.isLooping = false
                  player.setSurface(surface)
                  player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                  player.setOnInfoListener { _, what, _ ->
                    if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                      isFirstFrameRendered = true
                    }
                    false
                  }
                  player.setOnPreparedListener { preparedPlayer ->
                    if (!completed && !isPaused) {
                      preparedPlayer.start()
                      hasStartedPlayback = true
                    }
                  }
                  player.setOnCompletionListener {
                    completeOnce()
                  }
                  player.setOnErrorListener { _, _, _ ->
                    completeOnce()
                    true
                  }
                  player.prepareAsync()
                }
                if (setupResult.isFailure) {
                  completeOnce()
                }
              }

              override fun onSurfaceTextureSizeChanged(
                surfaceTexture: android.graphics.SurfaceTexture,
                width: Int,
                height: Int,
              ) = Unit

              override fun onSurfaceTextureDestroyed(
                surfaceTexture: android.graphics.SurfaceTexture,
              ): Boolean {
                releasePlayer()
                return true
              }

              override fun onSurfaceTextureUpdated(
                surfaceTexture: android.graphics.SurfaceTexture,
              ) = Unit
            }
          }
        },
        modifier = Modifier.fillMaxSize(),
      )

      // Pure black veil until frame 0 hits GPU, avoiding any decoder warm-up flash.
      if (!isFirstFrameRendered) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(MioColors.PureBlack),
        )
      }
    }
  }
}
