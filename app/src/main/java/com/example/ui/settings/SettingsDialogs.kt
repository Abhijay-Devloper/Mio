package com.example.ui.settings

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.config.MioConstants
import com.example.data.model.UserProfile
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun EditProfileDialog(
  profile: UserProfile,
  initialBio: String,
  onDismiss: () -> Unit,
  onSave: (displayName: String, bio: String) -> Unit,
) {
  var displayName by rememberSaveable { mutableStateOf(profile.displayName) }
  var bio by rememberSaveable { mutableStateOf(if (profile.bio.isNotBlank()) profile.bio else initialBio) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MioColors.CardSurface,
    title = { Text("Edit Profile", color = MioColors.PrimaryText, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MioSpacing.md),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Display Name", color = MioColors.MutedText, style = MaterialTheme.typography.labelMedium)
          OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it.take(MioConstants.MAX_DISPLAY_NAME_LENGTH) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MioColors.RoseAccent,
              unfocusedBorderColor = MioColors.SubtleBorder,
              focusedTextColor = MioColors.PrimaryText,
              unfocusedTextColor = MioColors.PrimaryText,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("edit_display_name_input"),
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text("Bio", color = MioColors.MutedText, style = MaterialTheme.typography.labelMedium)
          OutlinedTextField(
            value = bio,
            onValueChange = { bio = it.take(200) },
            minLines = 3,
            maxLines = 4,
            placeholder = { Text("Add a warm note about yourself...", color = MioColors.MutedText) },
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = MioColors.RoseAccent,
              unfocusedBorderColor = MioColors.SubtleBorder,
              focusedTextColor = MioColors.PrimaryText,
              unfocusedTextColor = MioColors.PrimaryText,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("edit_bio_input"),
          )
          Text(
            text = "${bio.length}/200",
            style = MaterialTheme.typography.labelSmall,
            color = MioColors.MutedText,
            modifier = Modifier.align(Alignment.End),
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(displayName, bio)
          onDismiss()
        },
        enabled = displayName.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
        modifier = Modifier.testTag("save_profile_button"),
      ) {
        Text("Save", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = MioColors.MutedText)
      }
    },
  )
}

@Composable
fun ChangeUsernameDialog(
  currentUsername: String,
  onDismiss: () -> Unit,
  onCheckAvailability: (String, (Boolean) -> Unit) -> Unit,
  onChange: (oldUsername: String, newUsername: String) -> Unit,
) {
  var usernameInput by rememberSaveable { mutableStateOf(currentUsername) }
  var isChecking by remember { mutableStateOf(false) }
  var isAvailable by remember { mutableStateOf<Boolean?>(null) }
  var statusMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(usernameInput) {
    val clean = usernameInput.trim().lowercase()
    if (clean == currentUsername.lowercase()) {
      isChecking = false
      isAvailable = true
      statusMessage = "Your current username."
      return@LaunchedEffect
    }
    if (clean.length < MioConstants.MIN_USERNAME_LENGTH) {
      isChecking = false
      isAvailable = false
      statusMessage = "Must be at least 3 characters."
      return@LaunchedEffect
    }
    if (!MioConstants.USERNAME_REGEX.matches(clean)) {
      isChecking = false
      isAvailable = false
      statusMessage = "Letters, numbers, and underscore only."
      return@LaunchedEffect
    }
    if (clean in MioConstants.RESERVED_USERNAMES) {
      isChecking = false
      isAvailable = false
      statusMessage = "That username is reserved."
      return@LaunchedEffect
    }

    isChecking = true
    statusMessage = "Checking availability..."
    delay(600) // 600ms debounce
    onCheckAvailability(clean) { available ->
      isChecking = false
      isAvailable = available
      statusMessage = if (available) "Username is available!" else "Username is already taken."
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MioColors.CardSurface,
    title = { Text("Change Username", color = MioColors.PrimaryText, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MioSpacing.sm),
      ) {
        Text(
          text = "Choose a unique username. Your partner can find and connect with you using this exact handle.",
          style = MaterialTheme.typography.bodyMedium,
          color = MioColors.MutedText,
        )

        OutlinedTextField(
          value = usernameInput,
          onValueChange = { usernameInput = it.trim().lowercase().take(MioConstants.MAX_USERNAME_LENGTH) },
          singleLine = true,
          prefix = { Text("@", color = MioColors.RoseAccent, fontWeight = FontWeight.Bold) },
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isAvailable == true) MioColors.RoseAccent else MioColors.DangerEmber,
            unfocusedBorderColor = MioColors.SubtleBorder,
            focusedTextColor = MioColors.PrimaryText,
            unfocusedTextColor = MioColors.PrimaryText,
          ),
          trailingIcon = {
            if (isChecking) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MioColors.RoseAccent, strokeWidth = 2.dp)
            } else if (isAvailable == true) {
              Icon(Icons.Outlined.Check, contentDescription = "Available", tint = MioColors.RoseAccent)
            } else if (isAvailable == false) {
              Icon(Icons.Outlined.Close, contentDescription = "Unavailable", tint = MioColors.DangerEmber)
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("change_username_input"),
        )

        statusMessage?.let { msg ->
          Text(
            text = msg,
            style = MaterialTheme.typography.labelMedium,
            color = if (isAvailable == true) MioColors.RoseAccent else MioColors.DangerEmber,
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val clean = usernameInput.trim().lowercase()
          if (isAvailable == true && clean != currentUsername.lowercase()) {
            onChange(currentUsername, clean)
            onDismiss()
          }
        },
        enabled = isAvailable == true && usernameInput.trim().lowercase() != currentUsername.lowercase() && !isChecking,
        colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
        modifier = Modifier.testTag("confirm_change_username_button"),
      ) {
        Text("Change", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = MioColors.MutedText)
      }
    },
  )
}

@Composable
fun UnblockConfirmDialog(
  username: String,
  onDismiss: () -> Unit,
  onConfirm: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MioColors.CardSurface,
    title = { Text("Unblock User", color = MioColors.PrimaryText, style = MaterialTheme.typography.titleLarge) },
    text = {
      Text(
        text = "Unblock @$username? They'll need to send a new message request.",
        style = MaterialTheme.typography.bodyMedium,
        color = MioColors.PrimaryText,
      )
    },
    confirmButton = {
      Button(
        onClick = {
          onConfirm()
          onDismiss()
        },
        colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
        modifier = Modifier.testTag("confirm_unblock_button"),
      ) {
        Text("Unblock", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = MioColors.MutedText)
      }
    },
  )
}

@Composable
fun MuteAllDurationDialog(
  onDismiss: () -> Unit,
  onSelectDuration: (Long) -> Unit,
) {
  val durations = listOf(
    "1 Hour" to 1 * 3600_000L,
    "8 Hours" to 8 * 3600_000L,
    "24 Hours" to 24 * 3600_000L,
    "7 Days" to 7 * 24 * 3600_000L,
    "Always (Until turned back on)" to Long.MAX_VALUE,
    "Unmute" to 0L,
  )

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MioColors.CardSurface,
    title = { Text("Mute All Notifications", color = MioColors.PrimaryText, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MioSpacing.xs),
      ) {
        durations.forEach { (label, durationMs) ->
          Button(
            onClick = {
              val targetTime = if (durationMs == 0L || durationMs == Long.MAX_VALUE) durationMs else System.currentTimeMillis() + durationMs
              onSelectDuration(targetTime)
              onDismiss()
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = if (durationMs == 0L) MioColors.DangerEmber.copy(alpha = 0.2f) else MioColors.BurgundySurface,
            ),
            shape = RoundedCornerShape(MioRadii.sm),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(label, color = if (durationMs == 0L) MioColors.DangerEmber else MioColors.PrimaryText)
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Close", color = MioColors.MutedText)
      }
    },
  )
}

@Composable
fun AppLockSetupDialog(
  initialType: String,
  onDismiss: () -> Unit,
  onApply: (type: String, pin: String) -> Unit,
) {
  var selectedType by rememberSaveable { mutableStateOf(initialType) }
  var pinInput by rememberSaveable { mutableStateOf("") }
  var pinConfirm by rememberSaveable { mutableStateOf("") }
  var error by rememberSaveable { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MioColors.CardSurface,
    title = { Text("Set App Lock", color = MioColors.PrimaryText, style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MioSpacing.sm),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(
            selected = selectedType == "biometric",
            onClick = { selectedType = "biometric" },
            colors = RadioButtonDefaults.colors(selectedColor = MioColors.RoseAccent),
          )
          Text("Biometric (Fingerprint / Face)", color = MioColors.PrimaryText)
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(
            selected = selectedType == "pin",
            onClick = { selectedType = "pin" },
            colors = RadioButtonDefaults.colors(selectedColor = MioColors.RoseAccent),
          )
          Text("4-digit PIN", color = MioColors.PrimaryText)
        }

        if (selectedType == "pin") {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
              value = pinInput,
              onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pinInput = it },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              placeholder = { Text("Enter 4 digits", color = MioColors.MutedText) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MioColors.RoseAccent,
                unfocusedBorderColor = MioColors.SubtleBorder,
                focusedTextColor = MioColors.PrimaryText,
                unfocusedTextColor = MioColors.PrimaryText,
              ),
              modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
              value = pinConfirm,
              onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pinConfirm = it },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
              placeholder = { Text("Confirm 4 digits", color = MioColors.MutedText) },
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MioColors.RoseAccent,
                unfocusedBorderColor = MioColors.SubtleBorder,
                focusedTextColor = MioColors.PrimaryText,
                unfocusedTextColor = MioColors.PrimaryText,
              ),
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }

        error?.let { err ->
          Text(err, color = MioColors.DangerEmber, style = MaterialTheme.typography.labelMedium)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (selectedType == "pin") {
            if (pinInput.length != 4) {
              error = "PIN must be exactly 4 digits."
              return@Button
            }
            if (pinInput != pinConfirm) {
              error = "PINs do not match."
              return@Button
            }
          }
          onApply(selectedType, pinInput)
          onDismiss()
        },
        colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
      ) {
        Text("Enable Lock", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = MioColors.MutedText)
      }
    },
  )
}

@Composable
fun WallpaperCropDialog(
  imageUri: Uri,
  onDismiss: () -> Unit,
  onApply: (String) -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var scale by remember { mutableStateOf(1f) }
  var offsetX by remember { mutableStateOf(0f) }
  var offsetY by remember { mutableStateOf(0f) }
  var isTouching by remember { mutableStateOf(false) }

  val bitmap = remember(imageUri) {
    runCatching {
      val stream = context.contentResolver.openInputStream(imageUri)
      val bmp = BitmapFactory.decodeStream(stream)
      stream?.close()
      bmp
    }.getOrNull()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MioColors.CardSurface,
    title = {
      Text("Adjust Wallpaper", color = MioColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MioSpacing.md),
      ) {
        Text(
          text = "Drag to pan, pinch or drag to zoom. Shows exact visible header area.",
          color = MioColors.MutedText,
          style = MaterialTheme.typography.bodyMedium,
        )

        if (bitmap != null) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(288.dp)
              .clip(RoundedCornerShape(MioRadii.md))
              .background(MioColors.PureBlack)
              .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(MioRadii.md))
              .pointerInput(Unit) {
                awaitEachGesture {
                  awaitFirstDown()
                  isTouching = true
                  do {
                    val event = awaitPointerEvent()
                    val anyPressed = event.changes.any { it.pressed }
                    if (!anyPressed) break
                  } while (true)
                  isTouching = false
                }
              }
              .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                  scale = (scale * zoom).coerceIn(1f, 4f)
                  offsetX += pan.x
                  offsetY += pan.y
                }
              },
            contentAlignment = Alignment.Center,
          ) {
            Image(
              bitmap = bitmap.asImageBitmap(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                  scaleX = scale,
                  scaleY = scale,
                  translationX = offsetX,
                  translationY = offsetY,
                ),
            )

            if (isTouching) {
              Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val strokeWidth = 1.dp.toPx()
                val lineColor = Color.White.copy(alpha = 0.45f)
                drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth)
                drawLine(lineColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth)
                drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth)
                drawLine(lineColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth)
              }
            }
          }
        } else {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(288.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(color = MioColors.RoseAccent)
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (bitmap != null) {
            scope.launch(Dispatchers.IO) {
              val path = cropAndSaveWallpaper(
                context = context,
                uri = imageUri,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                containerWidth = 1080f,
                containerHeight = 720f,
              )
              if (path != null) {
                withContext(Dispatchers.Main) {
                  onApply(path)
                  onDismiss()
                }
              }
            }
          }
        },
        colors = ButtonDefaults.buttonColors(containerColor = MioColors.RoseAccent),
      ) {
        Text("Crop & Apply", color = MioColors.PureBlack, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel", color = MioColors.MutedText)
      }
    },
  )
}

private fun cropAndSaveWallpaper(
  context: Context,
  uri: Uri,
  scale: Float,
  offsetX: Float,
  offsetY: Float,
  containerWidth: Float,
  containerHeight: Float,
): String? {
  return try {
    val inputStream = context.contentResolver.openInputStream(uri) ?: return null
    val originalBitmap = BitmapFactory.decodeStream(inputStream)
    inputStream.close()
    if (originalBitmap == null) return null

    val targetWidth = 1080
    val targetHeight = 720
    val resultBitmap = android.graphics.Bitmap.createBitmap(targetWidth, targetHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(resultBitmap)

    val matrix = android.graphics.Matrix()
    val bitmapWidth = originalBitmap.width.toFloat()
    val bitmapHeight = originalBitmap.height.toFloat()

    val fitScale = kotlin.math.max(containerWidth / bitmapWidth, containerHeight / bitmapHeight)
    val effectiveScale = fitScale * scale

    val scaledW = bitmapWidth * effectiveScale
    val scaledH = bitmapHeight * effectiveScale

    val dx = (containerWidth - scaledW) / 2f + offsetX
    val dy = (containerHeight - scaledH) / 2f + offsetY

    matrix.setScale(effectiveScale, effectiveScale)
    matrix.postTranslate(dx, dy)

    val outputScaleX = targetWidth / containerWidth
    val outputScaleY = targetHeight / containerHeight
    matrix.postScale(outputScaleX, outputScaleY)

    val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(originalBitmap, matrix, paint)

    val file = File(context.filesDir, "chat_wallpaper.jpg")
    val outputStream = FileOutputStream(file)
    resultBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, outputStream)
    outputStream.flush()
    outputStream.close()
    file.absolutePath
  } catch (e: Exception) {
    null
  }
}
