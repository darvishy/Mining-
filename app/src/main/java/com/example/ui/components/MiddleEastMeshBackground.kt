package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import com.example.ui.theme.HudAmber
import com.example.ui.theme.HudBorder
import com.example.ui.theme.HudCyan
import com.example.ui.theme.HudGreen
import com.example.ui.theme.HudTextDim
import com.example.ui.theme.HudTextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Data model for a geographical node in the Middle East tactical mesh.
 */
data class GeoNode(
    val id: String,
    val name: String,
    val lat: Float,
    val lon: Float,
    val isIranProvince: Boolean = false,
    val provinceCode: String = ""
)

/**
 * Animated cybernetic Middle East mesh background.
 * Features a wireframe tactical map of the Middle East region with all 31 Iranian provinces
 * lighting up sequentially in an animated radar reconnaissance sweep.
 */
@Composable
fun MiddleEastMeshBackground(
    modifier: Modifier = Modifier
) {
    // 31 Provinces of Iran with real geographic centroids
    val iranProvinces = remember {
        listOf(
            GeoNode("thr", "TEHRAN", 35.6892f, 51.3890f, true, "THR"),
            GeoNode("alb", "ALBORZ", 35.8327f, 50.9915f, true, "ALB"),
            GeoNode("qom", "QOM", 34.6401f, 50.8764f, true, "QOM"),
            GeoNode("mrk", "MARKAZI", 34.0917f, 49.6892f, true, "MRK"),
            GeoNode("qzv", "QAZVIN", 36.2688f, 50.0041f, true, "QZV"),
            GeoNode("znj", "ZANJAN", 36.6736f, 48.4787f, true, "ZNJ"),
            GeoNode("isf", "ISFAHAN", 32.6546f, 51.6680f, true, "ISF"),
            GeoNode("frs", "FARS", 29.5918f, 52.5837f, true, "FRS"),
            GeoNode("khz", "KHUZESTAN", 31.3183f, 48.6706f, true, "KHZ"),
            GeoNode("bsh", "BUSHEHR", 28.9234f, 50.8203f, true, "BSH"),
            GeoNode("hmz", "HORMOZGAN", 27.1832f, 56.2666f, true, "HMZ"),
            GeoNode("sis", "SISTAN-BALUCHESTAN", 27.5000f, 60.8000f, true, "SIS"),
            GeoNode("krm", "KERMAN", 30.2839f, 57.0834f, true, "KRM"),
            GeoNode("yzd", "YAZD", 31.8974f, 54.3569f, true, "YZD"),
            GeoNode("rkh", "RAZAVI KHORASAN", 36.2972f, 59.6067f, true, "RKH"),
            GeoNode("nkh", "NORTH KHORASAN", 37.4761f, 57.3245f, true, "NKH"),
            GeoNode("skh", "SOUTH KHORASAN", 32.8663f, 59.2211f, true, "SKH"),
            GeoNode("smn", "SEMNAN", 35.5769f, 53.3970f, true, "SMN"),
            GeoNode("mzn", "MAZANDARAN", 36.5659f, 53.0586f, true, "MZN"),
            GeoNode("gln", "GILAN", 37.2808f, 49.5832f, true, "GLN"),
            GeoNode("gls", "GOLESTAN", 36.8456f, 54.4394f, true, "GLS"),
            GeoNode("ard", "ARDABIL", 38.2498f, 48.2933f, true, "ARD"),
            GeoNode("eaz", "EAST AZERBAIJAN", 38.0800f, 46.2919f, true, "EAZ"),
            GeoNode("waz", "WEST AZERBAIJAN", 37.5522f, 45.0760f, true, "WAZ"),
            GeoNode("krd", "KURDISTAN", 35.3144f, 46.9961f, true, "KRD"),
            GeoNode("ksh", "KERMANSHAH", 34.3277f, 47.0778f, true, "KSH"),
            GeoNode("ilm", "ILAM", 33.6374f, 46.4227f, true, "ILM"),
            GeoNode("lor", "LORESTAN", 33.4647f, 48.3558f, true, "LOR"),
            GeoNode("hmd", "HAMEDAN", 34.7989f, 48.5150f, true, "HMD"),
            GeoNode("chb", "CHAHARMAHAL", 32.3256f, 50.8644f, true, "CHB"),
            GeoNode("kba", "KOHGILUYEH", 30.6684f, 51.5876f, true, "KBA")
        )
    }

    // Surrounding Middle East regional landmark nodes
    val regionalNodes = remember {
        listOf(
            GeoNode("bgd", "BAGHDAD", 33.3152f, 44.3661f),
            GeoNode("bsr", "BASRA", 30.5081f, 47.7835f),
            GeoNode("msl", "MOSUL", 36.3400f, 43.1300f),
            GeoNode("dam", "DAMASCUS", 33.5138f, 36.2765f),
            GeoNode("amm", "AMMAN", 31.9454f, 35.9284f),
            GeoNode("ank", "ANKARA", 39.9334f, 32.8597f),
            GeoNode("van", "VAN", 38.4891f, 43.4089f),
            GeoNode("bku", "BAKU", 40.4093f, 49.8671f),
            GeoNode("asg", "ASHGABAT", 37.9601f, 58.3261f),
            GeoNode("hrt", "HERAT", 34.3529f, 62.2040f),
            GeoNode("kbl", "KABUL", 34.5553f, 65.5000f),
            GeoNode("kwt", "KUWAIT CITY", 29.3759f, 47.9774f),
            GeoNode("ryd", "RIYADH", 24.7136f, 46.6753f),
            GeoNode("mnm", "MANAMA", 26.2285f, 50.5860f),
            GeoNode("dha", "DOHA", 25.2854f, 51.5310f),
            GeoNode("dxb", "DUBAI", 25.2048f, 55.2708f),
            GeoNode("mct", "MUSCAT", 23.5880f, 58.3829f),
            GeoNode("qta", "QUETTA", 30.1798f, 66.9750f)
        )
    }

    val allNodes = remember { iranProvinces + regionalNodes }
    val nodeMap = remember { allNodes.associateBy { it.id } }

    // Mesh connections (Edges)
    val meshEdges = remember {
        listOf(
            // Intra-Iran connections
            Pair("thr", "alb"), Pair("thr", "qzv"), Pair("thr", "qom"), Pair("thr", "smn"), Pair("thr", "mzn"),
            Pair("alb", "qzv"), Pair("alb", "mzn"),
            Pair("qom", "mrk"), Pair("qom", "isf"), Pair("qom", "smn"),
            Pair("mrk", "hmd"), Pair("mrk", "lor"), Pair("mrk", "isf"),
            Pair("qzv", "znj"), Pair("qzv", "gln"), Pair("qzv", "hmd"),
            Pair("znj", "ard"), Pair("znj", "eaz"), Pair("znj", "krd"), Pair("znj", "hmd"),
            Pair("gln", "ard"), Pair("gln", "mzn"),
            Pair("ard", "eaz"),
            Pair("eaz", "waz"),
            Pair("waz", "krd"),
            Pair("krd", "ksh"), Pair("krd", "hmd"),
            Pair("ksh", "ilm"), Pair("ksh", "lor"), Pair("ksh", "hmd"),
            Pair("ilm", "khz"), Pair("ilm", "lor"),
            Pair("lor", "khz"), Pair("lor", "chb"), Pair("lor", "isf"),
            Pair("khz", "chb"), Pair("khz", "kba"), Pair("khz", "bsh"),
            Pair("chb", "isf"), Pair("chb", "kba"),
            Pair("kba", "isf"), Pair("kba", "frs"), Pair("kba", "bsh"),
            Pair("isf", "yzd"), Pair("isf", "smn"), Pair("isf", "frs"),
            Pair("frs", "bsh"), Pair("frs", "hmz"), Pair("frs", "yzd"), Pair("frs", "krm"),
            Pair("bsh", "hmz"),
            Pair("hmz", "krm"), Pair("hmz", "sis"),
            Pair("yzd", "krm"), Pair("yzd", "skh"), Pair("yzd", "smn"),
            Pair("krm", "sis"), Pair("krm", "skh"),
            Pair("sis", "skh"),
            Pair("skh", "rkh"),
            Pair("rkh", "nkh"), Pair("rkh", "smn"),
            Pair("nkh", "gls"), Pair("nkh", "smn"),
            Pair("gls", "mzn"), Pair("gls", "smn"),
            Pair("mzn", "smn"),

            // External Middle East mesh edges
            Pair("waz", "van"), Pair("ard", "bku"), Pair("gln", "bku"),
            Pair("van", "msl"), Pair("ank", "van"), Pair("dam", "amm"),
            Pair("dam", "bgd"), Pair("msl", "bgd"), Pair("bgd", "bsr"),
            Pair("ksh", "bgd"), Pair("ilm", "bgd"), Pair("khz", "bsr"),
            Pair("bsr", "kwt"), Pair("kwt", "bsh"), Pair("kwt", "ryd"),
            Pair("ryd", "mnm"), Pair("mnm", "dha"), Pair("dha", "dxb"),
            Pair("dxb", "mct"), Pair("bsh", "dha"), Pair("hmz", "dxb"),
            Pair("hmz", "mct"), Pair("gls", "asg"), Pair("nkh", "asg"),
            Pair("rkh", "hrt"), Pair("hrt", "kbl"), Pair("skh", "hrt"),
            Pair("sis", "qta"), Pair("kbl", "qta"), Pair("asg", "hrt")
        )
    }

    // Animation transition: radar scan and province spotlight
    val transition = rememberInfiniteTransition(label = "middle_east_mesh")

    // Sweep across provinces
    val sweepProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_progress"
    )

    // Pulse animation for node rings
    val pulseRing by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_ring"
    )

    // Radar scan beam angle
    val radarAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_angle"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Coordinate bounding box for Middle East:
        // Longitude: 31°E to 67°E
        // Latitude: 21°N to 41.5°N
        val minLon = 31.0f
        val maxLon = 67.0f
        val minLat = 21.0f
        val maxLat = 41.5f

        fun toScreen(lat: Float, lon: Float): Offset {
            val xRel = (lon - minLon) / (maxLon - minLon)
            val yRel = (maxLat - lat) / (maxLat - minLat)
            // Add slight padding to keep inside screen boundaries
            val padX = w * 0.04f
            val padY = h * 0.08f
            val renderW = w - (padX * 2f)
            val renderH = h - (padY * 2f)
            return Offset(padX + (xRel * renderW), padY + (yRel * renderH))
        }

        // Draw background tactical coordinates graticule
        drawGraticule(w, h)

        // Draw Persian Gulf and Caspian Sea cyber water contours
        drawCyberWaterContours(::toScreen)

        // Calculate active province index based on sweepProgress
        val totalProvinces = iranProvinces.size
        val currentProvinceFloat = sweepProgress * totalProvinces
        val activeProvinceIndex = currentProvinceFloat.toInt() % totalProvinces
        val activeProvince = iranProvinces[activeProvinceIndex]
        val activeOffset = toScreen(activeProvince.lat, activeProvince.lon)

        // 1. Draw Mesh Connecting Lines
        for (edge in meshEdges) {
            val n1 = nodeMap[edge.first] ?: continue
            val n2 = nodeMap[edge.second] ?: continue
            val p1 = toScreen(n1.lat, n1.lon)
            val p2 = toScreen(n2.lat, n2.lon)

            val connectsActive = (n1.id == activeProvince.id || n2.id == activeProvince.id)
            val isIntraIran = n1.isIranProvince && n2.isIranProvince

            val lineColor: Color
            val strokeW: Float
            if (connectsActive) {
                lineColor = HudCyan.copy(alpha = 0.65f)
                strokeW = 2.0f
            } else if (isIntraIran) {
                lineColor = HudCyan.copy(alpha = 0.16f)
                strokeW = 1.0f
            } else {
                lineColor = HudBorder.copy(alpha = 0.22f)
                strokeW = 0.8f
            }

            drawLine(
                color = lineColor,
                start = p1,
                end = p2,
                strokeWidth = strokeW
            )
        }

        // 2. Draw Regional Middle East Nodes (faint background context)
        for (node in regionalNodes) {
            val pos = toScreen(node.lat, node.lon)
            drawCircle(
                color = HudBorder.copy(alpha = 0.35f),
                radius = 2.2f,
                center = pos
            )
        }

        // 3. Draw All 31 Iranian Province Nodes
        for (i in iranProvinces.indices) {
            val province = iranProvinces[i]
            val pos = toScreen(province.lat, province.lon)

            // Calculate trailing glow / distance in cycle from active province
            val diff = (activeProvinceIndex - i + totalProvinces) % totalProvinces
            val isCurrent = (diff == 0)
            val isTrail1 = (diff == 1)
            val isTrail2 = (diff == 2)

            when {
                isCurrent -> {
                    // Lit up province: bright glowing concentric radar pulse
                    val pulseRadius = 5f + (pulseRing * 18f)
                    val pulseAlpha = (1f - pulseRing).coerceIn(0f, 1f) * 0.8f

                    // Outer expanding shockwave
                    drawCircle(
                        color = HudAmber.copy(alpha = pulseAlpha),
                        radius = pulseRadius,
                        center = pos,
                        style = Stroke(width = 1.5f)
                    )

                    // Secondary cyan glow ring
                    drawCircle(
                        color = HudCyan.copy(alpha = 0.7f),
                        radius = 8f,
                        center = pos,
                        style = Stroke(width = 2.0f)
                    )

                    // Core bright glowing point
                    drawCircle(
                        color = HudAmber,
                        radius = 4.2f,
                        center = pos
                    )

                    // Crosshair tick marks around active province
                    val tickLen = 6f
                    drawLine(
                        color = HudAmber,
                        start = Offset(pos.x - 11f - tickLen, pos.y),
                        end = Offset(pos.x - 11f, pos.y),
                        strokeWidth = 1.2f
                    )
                    drawLine(
                        color = HudAmber,
                        start = Offset(pos.x + 11f, pos.y),
                        end = Offset(pos.x + 11f + tickLen, pos.y),
                        strokeWidth = 1.2f
                    )
                    drawLine(
                        color = HudAmber,
                        start = Offset(pos.x, pos.y - 11f - tickLen),
                        end = Offset(pos.x, pos.y - 11f),
                        strokeWidth = 1.2f
                    )
                    drawLine(
                        color = HudAmber,
                        start = Offset(pos.x, pos.y + 11f),
                        end = Offset(pos.x, pos.y + 11f + tickLen),
                        strokeWidth = 1.2f
                    )
                }
                isTrail1 -> {
                    // Lingering phosphor warm glow
                    drawCircle(
                        color = HudCyan.copy(alpha = 0.4f),
                        radius = 5.5f,
                        center = pos,
                        style = Stroke(width = 1.2f)
                    )
                    drawCircle(
                        color = HudCyan.copy(alpha = 0.75f),
                        radius = 3.2f,
                        center = pos
                    )
                }
                isTrail2 -> {
                    drawCircle(
                        color = HudCyan.copy(alpha = 0.5f),
                        radius = 2.8f,
                        center = pos
                    )
                }
                else -> {
                    // Standby province node
                    drawCircle(
                        color = HudCyan.copy(alpha = 0.28f),
                        radius = 2.4f,
                        center = pos
                    )
                }
            }
        }

        // 4. Draw Sweeping Radar Line Centered on Iran
        val iranCenter = toScreen(32.5f, 53.5f)
        val radarRadius = w * 0.48f
        val rad = (radarAngle * PI / 180f).toFloat()
        val radarEnd = Offset(
            iranCenter.x + cos(rad) * radarRadius,
            iranCenter.y + sin(rad) * radarRadius
        )

        drawLine(
            color = HudCyan.copy(alpha = 0.18f),
            start = iranCenter,
            end = radarEnd,
            strokeWidth = 1.2f
        )

        // 5. Tactical Monospace HUD Overlay Text on Active Province
        drawActiveProvinceHud(
            activeProvince = activeProvince,
            activePos = activeOffset,
            canvasW = w,
            canvasH = h
        )
    }
}

/**
 * Draws background longitude/latitude graticules with cyber HUD styling
 */
private fun DrawScope.drawGraticule(w: Float, h: Float) {
    val gridStepX = w / 6f
    val gridStepY = h / 9f

    // Vertical longitude lines
    for (i in 1..5) {
        val x = i * gridStepX
        drawLine(
            color = HudCyan.copy(alpha = 0.05f),
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 0.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)
        )
    }

    // Horizontal latitude lines
    for (j in 1..8) {
        val y = j * gridStepY
        drawLine(
            color = HudCyan.copy(alpha = 0.05f),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 0.8f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f)
        )
    }
}

/**
 * Draws stylized cyber boundaries for Caspian Sea and Persian Gulf
 */
private fun DrawScope.drawCyberWaterContours(toScreen: (Float, Float) -> Offset) {
    // Persian Gulf path (Kuwait -> Bushehr -> Hormozgan -> Strait of Hormuz -> Gulf of Oman)
    val pgPoints = listOf(
        Pair(30.0f, 48.5f),
        Pair(29.0f, 50.5f),
        Pair(27.0f, 52.5f),
        Pair(26.5f, 54.5f),
        Pair(26.2f, 56.5f),
        Pair(25.5f, 57.5f),
        Pair(25.0f, 60.5f)
    )
    val pgPath = Path()
    pgPoints.forEachIndexed { idx, pt ->
        val p = toScreen(pt.first, pt.second)
        if (idx == 0) pgPath.moveTo(p.x, p.y) else pgPath.lineTo(p.x, p.y)
    }
    drawPath(
        path = pgPath,
        color = HudCyan.copy(alpha = 0.12f),
        style = Stroke(width = 1.0f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
    )

    // Caspian Sea path (Gilan -> Mazandaran -> Golestan coast)
    val caspianPoints = listOf(
        Pair(38.4f, 48.8f),
        Pair(37.4f, 49.8f),
        Pair(36.7f, 51.5f),
        Pair(36.8f, 53.5f),
        Pair(37.0f, 54.2f)
    )
    val caspianPath = Path()
    caspianPoints.forEachIndexed { idx, pt ->
        val p = toScreen(pt.first, pt.second)
        if (idx == 0) caspianPath.moveTo(p.x, p.y) else caspianPath.lineTo(p.x, p.y)
    }
    drawPath(
        path = caspianPath,
        color = HudCyan.copy(alpha = 0.12f),
        style = Stroke(width = 1.0f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f))
    )
}

/**
 * Draws tactical native HUD text on canvas for the active lighting province
 */
private fun DrawScope.drawActiveProvinceHud(
    activeProvince: GeoNode,
    activePos: Offset,
    canvasW: Float,
    canvasH: Float
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 0, 240, 255) // Cyan
            textSize = 24f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
            isFakeBoldText = true
            letterSpacing = 0.12f
        }

        val subPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(160, 255, 179, 0) // Amber
            textSize = 18f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
            letterSpacing = 0.08f
        }

        // Position text label smartly near the active province node
        val offsetX = if (activePos.x > canvasW * 0.65f) -190f else 18f
        val offsetY = if (activePos.y > canvasH * 0.75f) -28f else 32f

        val label = "[${activeProvince.provinceCode}: ${activeProvince.name}]"
        val coords = "LAT: ${String.format("%.1f", activeProvince.lat)}°N  LON: ${String.format("%.1f", activeProvince.lon)}°E"

        drawText(label, activePos.x + offsetX, activePos.y + offsetY, paint)
        drawText(coords, activePos.x + offsetX, activePos.y + offsetY + 22f, subPaint)

        // Top corner status readout
        val topStatusPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(110, 0, 240, 255)
            textSize = 20f
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
            letterSpacing = 0.1f
        }
        drawText("SECTOR: MIDDLE EAST // IRAN PROVINCIAL GRID [31/31]", 36f, 52f, topStatusPaint)
    }
}
