package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class ProvinceNode(
    val id: Int,
    val name: String,
    val code: String,
    val nx: Float, // 0..1 relative to Iran map region
    val ny: Float,
    val neighbors: List<Int> = emptyList()
)

data class ExternalMeshNode(
    val label: String,
    val nx: Float,
    val ny: Float
)

@Composable
fun MiddleEastMeshBackground(
    modifier: Modifier = Modifier
) {
    // Provinces of Iran with normalized relative coordinates and network linkages
    val provinces = remember {
        listOf(
            ProvinceNode(0, "TEHRAN", "021", 0.50f, 0.34f, listOf(1, 2, 3, 4, 6, 8)),
            ProvinceNode(1, "ALBORZ", "026", 0.47f, 0.33f, listOf(0, 4, 5)),
            ProvinceNode(2, "QOM", "025", 0.49f, 0.41f, listOf(0, 3, 9)),
            ProvinceNode(3, "MARKAZI", "086", 0.42f, 0.43f, listOf(0, 2, 9, 22, 25)),
            ProvinceNode(4, "QAZVIN", "028", 0.43f, 0.31f, listOf(1, 5, 27)),
            ProvinceNode(5, "GILAN", "013", 0.42f, 0.23f, listOf(4, 6, 27, 30)),
            ProvinceNode(6, "MAZANDARAN", "011", 0.52f, 0.27f, listOf(0, 5, 7, 8)),
            ProvinceNode(7, "GOLESTAN", "017", 0.61f, 0.24f, listOf(6, 8, 14)),
            ProvinceNode(8, "SEMNAN", "023", 0.60f, 0.37f, listOf(0, 6, 7, 10, 13, 15)),
            ProvinceNode(9, "ISFAHAN", "031", 0.49f, 0.51f, listOf(2, 3, 8, 10, 11, 20, 21)),
            ProvinceNode(10, "YAZD", "035", 0.60f, 0.55f, listOf(8, 9, 11, 12, 15)),
            ProvinceNode(11, "FARS", "071", 0.52f, 0.70f, listOf(9, 10, 12, 17, 18, 21)),
            ProvinceNode(12, "KERMAN", "034", 0.68f, 0.69f, listOf(10, 11, 15, 16, 17)),
            ProvinceNode(13, "KHORASAN-R", "051", 0.75f, 0.33f, listOf(8, 14, 15)),
            ProvinceNode(14, "KHORASAN-N", "058", 0.68f, 0.23f, listOf(7, 13)),
            ProvinceNode(15, "KHORASAN-S", "056", 0.75f, 0.51f, listOf(8, 10, 12, 13, 16)),
            ProvinceNode(16, "SISTAN", "054", 0.83f, 0.76f, listOf(12, 15, 17)),
            ProvinceNode(17, "HORMOZGAN", "076", 0.62f, 0.85f, listOf(11, 12, 16, 18)),
            ProvinceNode(18, "BUSHEHR", "077", 0.43f, 0.73f, listOf(11, 17, 19, 21)),
            ProvinceNode(19, "KHUZESTAN", "061", 0.35f, 0.59f, listOf(18, 20, 22, 23)),
            ProvinceNode(20, "CHAHARMAHAL", "038", 0.43f, 0.55f, listOf(9, 19, 21, 22)),
            ProvinceNode(21, "KOHGILUYEH", "074", 0.45f, 0.63f, listOf(11, 18, 20)),
            ProvinceNode(22, "LORESTAN", "066", 0.36f, 0.47f, listOf(3, 19, 20, 23, 24, 25)),
            ProvinceNode(23, "ILAM", "084", 0.28f, 0.49f, listOf(19, 22, 24)),
            ProvinceNode(24, "KERMANSHAH", "083", 0.27f, 0.41f, listOf(22, 23, 25, 26)),
            ProvinceNode(25, "HAMADAN", "081", 0.35f, 0.39f, listOf(3, 22, 24, 26, 27)),
            ProvinceNode(26, "KURDISTAN", "087", 0.28f, 0.33f, listOf(24, 25, 27, 28, 29)),
            ProvinceNode(27, "ZANJAN", "024", 0.36f, 0.29f, listOf(4, 5, 25, 26, 28, 30)),
            ProvinceNode(28, "AZARBAIJAN-E", "041", 0.27f, 0.21f, listOf(26, 27, 29, 30)),
            ProvinceNode(29, "AZARBAIJAN-W", "044", 0.20f, 0.23f, listOf(26, 28)),
            ProvinceNode(30, "ARDABIL", "045", 0.33f, 0.18f, listOf(5, 27, 28))
        )
    }

    // Surrounding Middle Eastern reference nodes to anchor the regional mesh
    val externalNodes = remember {
        listOf(
            ExternalMeshNode("BAGHDAD", 0.14f, 0.45f),
            ExternalMeshNode("BASRA", 0.23f, 0.64f),
            ExternalMeshNode("KUWAIT", 0.27f, 0.68f),
            ExternalMeshNode("RIYADH", 0.18f, 0.82f),
            ExternalMeshNode("DUBAI", 0.58f, 0.94f),
            ExternalMeshNode("MUSCAT", 0.72f, 0.96f),
            ExternalMeshNode("HERAT", 0.89f, 0.40f),
            ExternalMeshNode("KABUL", 0.98f, 0.35f),
            ExternalMeshNode("ASHGABAT", 0.71f, 0.14f),
            ExternalMeshNode("BAKU", 0.35f, 0.10f),
            ExternalMeshNode("ANKARA", 0.05f, 0.18f)
        )
    }

    // Animation transition for continuous cycle and radar sweep
    val infiniteTransition = rememberInfiniteTransition(label = "middle_east_mesh")

    // Province cycle progression (cycles through 0..30)
    val cycleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = provinces.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "province_cycle"
    )

    // Radar beam angle (0..2*PI)
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep"
    )

    // Pulse expansion for active province node
    val pulseRing by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_ring"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // Map area centered with generous padding
            val mapLeft = canvasW * 0.04f
            val mapTop = canvasH * 0.08f
            val mapWidth = canvasW * 0.92f
            val mapHeight = canvasH * 0.84f

            fun toOffset(nx: Float, ny: Float): Offset {
                return Offset(mapLeft + nx * mapWidth, mapTop + ny * mapHeight)
            }

            // 1. Draw subtle background cyber grid
            val gridSpacing = canvasW / 12f
            var gx = 0f
            while (gx < canvasW) {
                drawLine(
                    color = HudCyan.copy(alpha = 0.035f),
                    start = Offset(gx, 0f),
                    end = Offset(gx, canvasH),
                    strokeWidth = 0.8f
                )
                gx += gridSpacing
            }
            var gy = 0f
            while (gy < canvasH) {
                drawLine(
                    color = HudCyan.copy(alpha = 0.035f),
                    start = Offset(0f, gy),
                    end = Offset(canvasW, gy),
                    strokeWidth = 0.8f
                )
                gy += gridSpacing
            }

            // 2. Draw Persian Gulf & Caspian Sea regional outline arcs for geographic orientation
            // Caspian Sea water body outline (North)
            val caspianPath = Path().apply {
                val p1 = toOffset(0.33f, 0.17f)
                val p2 = toOffset(0.42f, 0.20f)
                val p3 = toOffset(0.53f, 0.24f)
                val p4 = toOffset(0.61f, 0.21f)
                moveTo(p1.x, p1.y)
                lineTo(p2.x, p2.y)
                lineTo(p3.x, p3.y)
                lineTo(p4.x, p4.y)
            }
            drawPath(
                path = caspianPath,
                color = HudCyan.copy(alpha = 0.15f),
                style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            )

            // Persian Gulf & Oman Sea outline (South)
            val gulfPath = Path().apply {
                val g1 = toOffset(0.24f, 0.65f)
                val g2 = toOffset(0.35f, 0.64f)
                val g3 = toOffset(0.43f, 0.77f)
                val g4 = toOffset(0.60f, 0.86f)
                val g5 = toOffset(0.72f, 0.90f)
                val g6 = toOffset(0.83f, 0.80f)
                moveTo(g1.x, g1.y)
                lineTo(g2.x, g2.y)
                lineTo(g3.x, g3.y)
                lineTo(g4.x, g4.y)
                lineTo(g5.x, g5.y)
                lineTo(g6.x, g6.y)
            }
            drawPath(
                path = gulfPath,
                color = HudCyan.copy(alpha = 0.15f),
                style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            )

            // 3. Draw External Middle Eastern mesh connections
            val externalOffsets = externalNodes.map { toOffset(it.nx, it.ny) }
            externalOffsets.forEachIndexed { i, extOffset ->
                // Draw external anchor point
                drawCircle(
                    color = HudAmber.copy(alpha = 0.15f),
                    radius = 2.2f,
                    center = extOffset
                )
            }

            // Connect external nodes to nearest Iranian border provinces
            val borderLinkPairs = listOf(
                Pair(externalOffsets[0], toOffset(0.27f, 0.41f)), // Baghdad -> Kermanshah
                Pair(externalOffsets[1], toOffset(0.35f, 0.59f)), // Basra -> Khuzestan
                Pair(externalOffsets[2], toOffset(0.35f, 0.59f)), // Kuwait -> Khuzestan
                Pair(externalOffsets[4], toOffset(0.62f, 0.85f)), // Dubai -> Hormozgan
                Pair(externalOffsets[6], toOffset(0.75f, 0.33f)), // Herat -> Khorasan Razavi
                Pair(externalOffsets[8], toOffset(0.68f, 0.23f)), // Ashgabat -> North Khorasan
                Pair(externalOffsets[9], toOffset(0.33f, 0.18f)), // Baku -> Ardabil
                Pair(externalOffsets[10], toOffset(0.20f, 0.23f)) // Ankara -> West Azarbaijan
            )

            borderLinkPairs.forEach { (ext, irn) ->
                drawLine(
                    color = HudCyan.copy(alpha = 0.08f),
                    start = ext,
                    end = irn,
                    strokeWidth = 0.8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f))
                )
            }

            // Current active illuminated province index
            val activeIndex = cycleProgress.toInt() % provinces.size
            val activeProvince = provinces[activeIndex]
            val activeCenter = toOffset(activeProvince.nx, activeProvince.ny)

            // 4. Draw Iranian Inter-Province Cyber Mesh Lines
            provinces.forEach { prov ->
                val p1 = toOffset(prov.nx, prov.ny)
                prov.neighbors.forEach { nIdx ->
                    if (nIdx > prov.id && nIdx < provinces.size) {
                        val neighbor = provinces[nIdx]
                        val p2 = toOffset(neighbor.nx, neighbor.ny)

                        // Is this link connected to the currently illuminated province?
                        val isConnectedToActive = (prov.id == activeIndex || nIdx == activeIndex)

                        val lineColor = if (isConnectedToActive) {
                            HudCyan.copy(alpha = 0.55f)
                        } else {
                            HudCyan.copy(alpha = 0.12f)
                        }

                        val strokeW = if (isConnectedToActive) 1.6f else 0.8f

                        drawLine(
                            color = lineColor,
                            start = p1,
                            end = p2,
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            // 5. Draw Radar Sweep Beam originating from Tehran/Central Iran
            val radarCenter = toOffset(0.50f, 0.45f)
            val radarLength = mapWidth * 0.75f
            val radarEnd = Offset(
                x = radarCenter.x + cos(radarAngle) * radarLength,
                y = radarCenter.y + sin(radarAngle) * radarLength
            )

            // Radar beam line
            drawLine(
                color = HudGreen.copy(alpha = 0.22f),
                start = radarCenter,
                end = radarEnd,
                strokeWidth = 1.4f,
                cap = StrokeCap.Round
            )

            // Faint concentric radar ranges
            drawCircle(
                color = HudCyan.copy(alpha = 0.05f),
                radius = mapWidth * 0.25f,
                center = radarCenter,
                style = Stroke(width = 0.8f)
            )
            drawCircle(
                color = HudCyan.copy(alpha = 0.04f),
                radius = mapWidth * 0.45f,
                center = radarCenter,
                style = Stroke(width = 0.8f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
            )

            // 6. Draw Province Nodes
            provinces.forEach { prov ->
                val nodePos = toOffset(prov.nx, prov.ny)
                val isActive = prov.id == activeIndex

                if (isActive) {
                    // Glowing beacon aura
                    drawCircle(
                        color = HudGreen.copy(alpha = 0.18f),
                        radius = 18f,
                        center = nodePos
                    )

                    // Expanding radar pulse ring
                    drawCircle(
                        color = HudCyan.copy(alpha = (1f - pulseRing) * 0.8f),
                        radius = 8f + pulseRing * 24f,
                        center = nodePos,
                        style = Stroke(width = 1.4f)
                    )

                    // Bright inner core
                    drawCircle(
                        color = HudGreen,
                        radius = 4.5f,
                        center = nodePos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.2f,
                        center = nodePos
                    )
                } else {
                    // Normal idle province node
                    drawCircle(
                        color = HudCyan.copy(alpha = 0.25f),
                        radius = 2.8f,
                        center = nodePos
                    )
                    drawCircle(
                        color = HudCyan.copy(alpha = 0.65f),
                        radius = 1.4f,
                        center = nodePos
                    )
                }
            }

            // 7. Tactical HUD Province Telemetry Overlay (Bottom Left corner of map)
            drawActiveProvinceTelemetry(
                scope = this,
                province = activeProvince,
                position = Offset(mapLeft + 8.dp.toPx(), mapTop + mapHeight - 16.dp.toPx())
            )
        }
    }
}

private fun drawActiveProvinceTelemetry(
    scope: DrawScope,
    province: ProvinceNode,
    position: Offset
) {
    scope.run {
        // Tactical reticle indicator
        drawLine(
            color = HudGreen.copy(alpha = 0.6f),
            start = Offset(position.x, position.y - 12f),
            end = Offset(position.x, position.y + 12f),
            strokeWidth = 1.2f
        )
        drawLine(
            color = HudGreen.copy(alpha = 0.6f),
            start = Offset(position.x - 12f, position.y),
            end = Offset(position.x + 12f, position.y),
            strokeWidth = 1.2f
        )

        drawCircle(
            color = HudGreen.copy(alpha = 0.4f),
            radius = 16f,
            center = position,
            style = Stroke(width = 1f)
        )
    }
}
