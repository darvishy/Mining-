package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBackground
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudBorderBright
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudCyanGlow
import com.example.ui.theme.HudGreen
import com.example.ui.theme.HudGridLine
import com.example.ui.theme.HudRed
import com.example.ui.theme.HudSurface
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.HudTextDim
import com.example.ui.theme.HudTextPrimary
import com.example.ui.theme.HudTextSecondary

fun triggerHudHaptic(context: Context, durationMs: Long = 20) {
    try {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    } catch (_: Exception) {}
}

/**
 * Tactical HUD Container with sci-fi bracket corners and optional glowing header
 */
@Composable
fun HudCard(
    modifier: Modifier = Modifier,
    borderColor: Color = HudBorderBright,
    cornerLength: Dp = 12.dp,
    strokeWidth: Dp = 1.5.dp,
    backgroundColor: Color = HudSurface,
    title: String? = null,
    titleColor: Color = HudCyan,
    statusBadge: String? = null,
    statusColor: Color = HudGreen,
    content: @Composable BoxScope.() -> Unit
) {
    Column(modifier = modifier) {
        if (title != null || statusBadge != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (title != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(titleColor, RoundedCornerShape(1.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title.uppercase(),
                            color = titleColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (statusBadge != null) {
                    Text(
                        text = "[$statusBadge]",
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(backgroundColor, RoundedCornerShape(2.dp))
                .drawBehind {
                    val cLen = cornerLength.toPx()
                    val sWidth = strokeWidth.toPx()
                    val w = size.width
                    val h = size.height

                    // Draw subtle bounding box
                    drawRect(
                        color = HudBorder,
                        style = Stroke(width = 0.8f)
                    )

                    // Draw Top-Left Corner Bracket
                    drawLine(borderColor, Offset(0f, 0f), Offset(cLen, 0f), sWidth)
                    drawLine(borderColor, Offset(0f, 0f), Offset(0f, cLen), sWidth)

                    // Top-Right Corner Bracket
                    drawLine(borderColor, Offset(w, 0f), Offset(w - cLen, 0f), sWidth)
                    drawLine(borderColor, Offset(w, 0f), Offset(w, cLen), sWidth)

                    // Bottom-Left Corner Bracket
                    drawLine(borderColor, Offset(0f, h), Offset(cLen, h), sWidth)
                    drawLine(borderColor, Offset(0f, h), Offset(0f, h - cLen), sWidth)

                    // Bottom-Right Corner Bracket
                    drawLine(borderColor, Offset(w, h), Offset(w - cLen, h), sWidth)
                    drawLine(borderColor, Offset(w, h), Offset(w, h - cLen), sWidth)
                }
                .padding(12.dp)
        ) {
            content()
        }
    }
}

/**
 * Interactive Cyber HUD Button
 */
@Composable
fun HudButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = HudCyan,
    icon: ImageVector? = null,
    isSecondary: Boolean = false
) {
    val context = LocalContext.current
    val bgColor = if (isSecondary) HudSurfaceElevated else accentColor.copy(alpha = 0.15f)
    val contentColor = if (enabled) (if (isSecondary) HudTextPrimary else accentColor) else HudTextDim
    val borderCol = if (enabled) (if (isSecondary) HudBorderBright else accentColor) else HudBorder

    Surface(
        modifier = modifier
            .height(48.dp)
            .focusProperties { canFocus = false }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                triggerHudHaptic(context, 25)
                onClick()
            },
        color = bgColor,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.2.dp, borderCol)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text.uppercase(),
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * HUD Tactical Text Field
 */
@Composable
fun HudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    textAlign: TextAlign = TextAlign.Start
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.fillMaxWidth(),
            label = {
                Text(
                    text = label.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                    textAlign = textAlign
                )
            },
            placeholder = {
                Text(
                    text = placeholder,
                    color = HudTextDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    textAlign = textAlign
                )
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon ?: {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (value.isNotEmpty()) {
                        IconButton(
                            onClick = { onValueChange("") },
                            modifier = Modifier
                                .size(24.dp)
                                .focusProperties { canFocus = false }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = HudTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            isError = isError,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                color = HudTextPrimary,
                fontSize = 14.sp,
                textAlign = textAlign
            ),
            shape = RoundedCornerShape(2.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HudCyan,
                unfocusedBorderColor = HudBorderBright,
                focusedLabelColor = HudCyan,
                unfocusedLabelColor = HudTextSecondary,
                cursorColor = HudCyan,
                focusedContainerColor = HudSurfaceElevated,
                unfocusedContainerColor = HudSurface,
                errorBorderColor = HudRed,
                errorLabelColor = HudRed,
                errorContainerColor = HudSurface
            )
        )

        if (isError && !errorMessage.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "! $errorMessage",
                color = HudRed,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * Telemetry Badge Chip
 */
@Composable
fun HudBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HudCyan,
    backgroundColor: Color = color.copy(alpha = 0.12f)
) {
    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(2.dp))
            .border(0.8.dp, color.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp
        )
    }
}

/**
 * Animated High-Tech Segmented Progress Bar
 */
@Composable
fun HudProgressBar(
    progress: Float, // 0.0f to 1.0f
    modifier: Modifier = Modifier,
    color: Color = HudCyan,
    trackColor: Color = HudBorder
) {
    val transition = rememberInfiniteTransition(label = "hud_scan")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(HudSurfaceElevated, RoundedCornerShape(1.dp))
            .border(0.8.dp, trackColor, RoundedCornerShape(1.dp))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val fillWidth = (w * progress.coerceIn(0f, 1f))

            // Draw filled progress
            if (fillWidth > 0) {
                drawRect(
                    color = color,
                    topLeft = Offset(0f, 0f),
                    size = Size(fillWidth, h)
                )

                // Draw high-tech scanline over progress
                val sweepX = fillWidth * pulse
                drawLine(
                    color = Color.White.copy(alpha = 0.7f),
                    start = Offset(sweepX, 0f),
                    end = Offset(sweepX, h),
                    strokeWidth = 3f
                )
            }

            // Draw segment tick marks
            val segments = 20
            val segWidth = w / segments
            for (i in 1 until segments) {
                val x = i * segWidth
                drawLine(
                    color = HudBackground,
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1.5f
                )
            }
        }
    }
}

/**
 * Top Developer Header: "Developer: NullEmperor" + Join Telegram Button
 */
@Composable
fun DeveloperHeader(
    modifier: Modifier = Modifier,
    onTelegramClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val openTelegram = {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://t.me/nullemperor"))
            context.startActivity(intent)
        } catch (_: Exception) {}
        onTelegramClick()
    }

    HudCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = HudAmber,
        backgroundColor = HudSurfaceElevated,
        cornerLength = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(HudGreen, RoundedCornerShape(1.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "DEVELOPER: NullEmperor",
                        color = HudAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.1.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "NULL-CORE SECURE DATABASE TERMINAL",
                    color = HudTextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Telegram Button
            Surface(
                modifier = Modifier
                    .height(36.dp)
                    .clickable {
                        triggerHudHaptic(context, 20)
                        openTelegram()
                    },
                color = HudAmber.copy(alpha = 0.15f),
                shape = RoundedCornerShape(2.dp),
                border = BorderStroke(1.dp, HudAmber)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Telegram",
                        tint = HudAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "JOIN TG",
                        color = HudAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}
