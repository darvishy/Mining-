package com.example.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBackground
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudBorderBright
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudCyanBright
import com.example.ui.theme.HudCyanGlow
import com.example.ui.theme.HudGreen
import com.example.ui.theme.HudSurfaceElevated
import com.example.ui.theme.HudTextDim
import com.example.ui.theme.HudTextSecondary
import kotlin.math.sin

/**
 * Animated Cyberpunk HUD Avatar with rotating radar rings, scanning laser,
 * audio telemetry spectrum, and interactive pulse response.
 */
@Composable
fun HudAvatar(
    modifier: Modifier = Modifier,
    totalRecordsCount: Long = 0L,
    statusText: String = "ACTIVE // READY",
    onAvatarClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var diagnosticCount by remember { mutableStateOf(0) }

    val transition = rememberInfiniteTransition(label = "hud_avatar")

    // Slow clockwise rotation for outer radar ring
    val rotateClockwise by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate_cw"
    )

    // Counter-clockwise rotation for inner reticle ring
    val rotateCounter by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate_ccw"
    )

    // Pulsing aura scale
    val pulseAura by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_aura"
    )

    // Vertical scanning laser beam
    val scanLineY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_laser"
    )

    // Wave oscillator for telemetry equalizer bars
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    HudCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = HudCyan,
        backgroundColor = HudBackground,
        cornerLength = 14.dp,
        title = "HOLOGRAPHIC OPERATIVE AVATAR",
        titleColor = HudCyan,
        statusBadge = "SYS: OPTIMAL",
        statusColor = HudGreen
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Circular Avatar Radar Container
            Box(
                modifier = Modifier
                    .size(170.dp)
                    .clickable {
                        triggerHudHaptic(context, 40)
                        diagnosticCount++
                        onAvatarClick()
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background Canvas drawing rotating radar arcs, angle ticks, and targeting reticles
                Canvas(modifier = Modifier.matchParentSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.minDimension / 2f - 4.dp.toPx()

                    // Outer Reticle Circle
                    drawCircle(
                        color = HudBorderBright,
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )

                    // Outer Clockwise Rotating Segmented Arcs
                    rotate(rotateClockwise, pivot = center) {
                        for (i in 0 until 4) {
                            val startAngle = i * 90f + 15f
                            drawArc(
                                color = HudCyan,
                                startAngle = startAngle,
                                sweepAngle = 55f,
                                useCenter = false,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2f, radius * 2f),
                                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Inner Counter-Rotating Reticle with tick marks
                    val innerRadius = radius * 0.82f
                    rotate(rotateCounter, pivot = center) {
                        for (i in 0 until 8) {
                            val angle = Math.toRadians((i * 45.0)).toFloat()
                            val p1 = Offset(
                                center.x + (innerRadius - 6.dp.toPx()) * kotlin.math.cos(angle),
                                center.y + (innerRadius - 6.dp.toPx()) * kotlin.math.sin(angle)
                            )
                            val p2 = Offset(
                                center.x + innerRadius * kotlin.math.cos(angle),
                                center.y + innerRadius * kotlin.math.sin(angle)
                            )
                            drawLine(
                                color = HudAmber,
                                start = p1,
                                end = p2,
                                strokeWidth = 1.5f
                            )
                        }

                        drawCircle(
                            color = HudCyan.copy(alpha = 0.35f),
                            radius = innerRadius,
                            center = center,
                            style = Stroke(width = 1f)
                        )
                    }

                    // Crosshair Ticks (N, S, E, W)
                    val crosshairLen = 10.dp.toPx()
                    drawLine(HudCyan, Offset(center.x, center.y - radius - crosshairLen), Offset(center.x, center.y - radius), 2f)
                    drawLine(HudCyan, Offset(center.x, center.y + radius), Offset(center.x, center.y + radius + crosshairLen), 2f)
                    drawLine(HudCyan, Offset(center.x - radius - crosshairLen, center.y), Offset(center.x - radius, center.y), 2f)
                    drawLine(HudCyan, Offset(center.x + radius, center.y), Offset(center.x + radius + crosshairLen, center.y), 2f)
                }

                // Centered Holographic Avatar Core Image (using ic_nullhud_logo)
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(HudSurfaceElevated)
                        .border(1.5.dp, HudCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_anonymous_mask),
                        contentDescription = "Anonymous HUD Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp)
                    )

                    // Vertical Scanning Laser Beam over the image
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val y = size.height * scanLineY
                        // Glowing laser line
                        drawLine(
                            color = HudCyanBright,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 2.5f
                        )
                        // Laser glow trail
                        drawRect(
                            color = HudCyanGlow,
                            topLeft = Offset(0f, (y - 12.dp.toPx()).coerceAtLeast(0f)),
                            size = Size(size.width, 24.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Audio / Frequency Telemetry Equalizer Spectrum
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(HudSurfaceElevated, RoundedCornerShape(2.dp))
                    .border(0.8.dp, HudBorder, RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val barCount = 28
                    val spacing = size.width / barCount
                    val maxBarHeight = size.height - 4f

                    for (i in 0 until barCount) {
                        val factor = (sin(wavePhase + i * 0.45) * 0.5 + 0.5).toFloat()
                        val barHeight = (maxBarHeight * factor).coerceAtLeast(3f)
                        val x = i * spacing + spacing / 2f
                        val yTop = (size.height - barHeight) / 2f

                        val barColor = when {
                            i % 5 == 0 -> HudAmber
                            i % 2 == 0 -> HudCyan
                            else -> HudGreen
                        }

                        drawLine(
                            color = barColor,
                            start = Offset(x, yTop),
                            end = Offset(x, yTop + barHeight),
                            strokeWidth = 3f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Telemetry Readout Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CORE: ONLINE [RADAR ACTIVE]",
                        color = HudCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "RECORDS: ${String.format("%,d", totalRecordsCount)} INDEXED",
                        color = HudTextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    HudBadge(
                        text = if (diagnosticCount > 0) "DIAG CYCLE #$diagnosticCount" else statusText,
                        color = if (diagnosticCount > 0) HudAmber else HudGreen
                    )
                    Text(
                        text = "SECURITY: BIO-LOCKED",
                        color = HudTextDim,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
