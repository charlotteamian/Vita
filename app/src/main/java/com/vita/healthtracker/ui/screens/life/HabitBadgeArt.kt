package com.vita.healthtracker.ui.screens.life

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vita.healthtracker.domain.HabitBadge
import com.vita.healthtracker.domain.HabitBadgeStyle

private val BadgeGold = Color(0xFFF5CB66)
private val BadgeCream = Color(0xFFF4E8C8)
private val BadgeDemon = Color(0xFFE43D32)
private val BadgeTime = Color(0xFF70C8FF)
private val BadgeVortex = Color(0xFF8D72FF)
private val BadgeStage = Color(0xFFC44A57)
private val BadgeInk = Color(0xFF070A12)

// —— 实验室多元宇宙 调色 ——
private val BadgeAcid       = Color(0xFF8FFF55)
private val BadgeAcidDeep   = Color(0xFF1A6B2A)
private val BadgePlasma     = Color(0xFFB07BFF)
private val BadgePlasmaDeep = Color(0xFF4E1F8F)
private val BadgeRift       = Color(0xFF6CD8FF)
private val BadgeRiftDeep   = Color(0xFF134A78)
private val BadgeHazard     = Color(0xFFF5A24A)
private val BadgeHazardDeep = Color(0xFF6E3411)
private val BadgeCaution    = Color(0xFFF0D04A)

/** 徽章主色：用于 3D 币的边缘金属色等。 */
fun habitBadgeRimColor(badge: HabitBadge): Color = when (badge.style) {
    HabitBadgeStyle.Crowley -> BadgeDemon
    HabitBadgeStyle.Aziraphale -> BadgeGold
    HabitBadgeStyle.Doctor -> BadgeTime
    HabitBadgeStyle.Stage -> BadgeStage
    HabitBadgeStyle.Legendary -> BadgeGold
    HabitBadgeStyle.LabAcid -> BadgeAcid
    HabitBadgeStyle.PortalPlasma -> BadgePlasma
    HabitBadgeStyle.ReactorHazard -> BadgeHazard
    HabitBadgeStyle.RiftBreak -> BadgeRift
    HabitBadgeStyle.LabApex -> BadgeGold
}

/** 八角徽章奖牌轮廓（160 坐标系），3D 币的厚度层复用。 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBadgeOctagon(color: Color) {
    val scale = kotlin.math.min(size.width, size.height) / 160f
    val dx = (size.width - 160f * scale) / 2f
    val dy = (size.height - 160f * scale) / 2f
    val point: (Float, Float) -> Offset = { x, y -> Offset(dx + x * scale, dy + y * scale) }
    drawPath(octagon(point), color)
}

@Composable
fun HabitBadgeImage(
    badge: HabitBadge,
    earned: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 82.dp,
    plate: Boolean = true,
) {
    val alpha = if (earned) 1f else 0.34f
    val base = modifier.size(iconSize).alpha(alpha)
    Box(
        modifier = if (plate) {
            base
                .clip(RoundedCornerShape(iconSize / 5))
                .background(Color.White.copy(alpha = if (earned) 0.045f else 0.025f))
        } else {
            base
        },
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val scale = kotlin.math.min(size.width, size.height) / 160f
            val dx = (this.size.width - 160f * scale) / 2f
            val dy = (this.size.height - 160f * scale) / 2f
            val point: (Float, Float) -> Offset = { x, y -> Offset(dx + x * scale, dy + y * scale) }
            fun stroke(color: Color, width: Float = 5f) = Stroke(
                width = width * scale,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )

            val (tone, tone2) = when (badge.style) {
                HabitBadgeStyle.Crowley -> BadgeDemon to BadgeGold
                HabitBadgeStyle.Aziraphale -> BadgeCream to BadgeGold
                HabitBadgeStyle.Doctor -> BadgeTime to BadgeVortex
                HabitBadgeStyle.Stage -> BadgeStage to BadgeGold
                HabitBadgeStyle.Legendary -> BadgeGold to BadgeTime
                HabitBadgeStyle.LabAcid -> BadgeAcid to BadgeAcidDeep
                HabitBadgeStyle.PortalPlasma -> BadgePlasma to BadgeRift
                HabitBadgeStyle.ReactorHazard -> BadgeHazard to BadgeCaution
                HabitBadgeStyle.RiftBreak -> BadgeRift to BadgePlasma
                HabitBadgeStyle.LabApex -> BadgeGold to BadgeRift
            }

            drawPath(
                path = octagon(point),
                brush = Brush.linearGradient(
                    colors = listOf(tone, tone2, BadgeInk),
                    start = point(16f, 10f),
                    end = point(146f, 152f),
                ),
            )
            drawPath(octagon(point), color = Color.White.copy(alpha = 0.34f), style = stroke(Color.White, 1.2f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tone.copy(alpha = 0.34f), Color(0xFF101523)),
                    center = point(76f, 62f),
                    radius = 62f * scale,
                ),
                radius = 55f * scale,
                center = point(80f, 80f),
            )
            drawCircle(color = Color.White.copy(alpha = 0.16f), radius = 55f * scale, center = point(80f, 80f), style = stroke(Color.White, 1.4f))

            when (badge.id) {
                "red_mirror" -> drawRedMirror(point, scale, ::stroke)
                "old_book_halo" -> drawOldBookHalo(point, scale, ::stroke)
                "time_runner" -> drawTimeRunner(point, scale, ::stroke)
                "double_rehearsal" -> drawDoubleRehearsal(point, scale, ::stroke)
                "serpent_wings" -> drawSerpentWings(point, scale, ::stroke)
                "blue_rift" -> drawBlueRift(point, scale, ::stroke)
                "night_guard" -> drawNightGuard(point, scale, ::stroke)
                "miracle_crown" -> drawMiracleCrown(point, scale, ::stroke)
                // —— 健身运动 徽章 ——
                "fit_first_sweat" -> drawFirstSweat(point, scale, ::stroke)
                "fit_portal_entry" -> drawPortalEntry(point, scale, ::stroke)
                "fit_rhythm" -> drawTrainingRhythm(point, scale, ::stroke)
                "fit_week_combo" -> drawWeekCombo(point, scale, ::stroke)
                "fit_force_atom" -> drawForceAtom(point, scale, ::stroke)
                "fit_beast_subject" -> drawBeastSubject(point, scale, ::stroke)
                "fit_core_battery" -> drawCoreBattery(point, scale, ::stroke)
                "fit_step_warp" -> drawStepWarp(point, scale, ::stroke)
                "fit_climb_rift" -> drawClimbRift(point, scale, ::stroke)
                "fit_burn_lab" -> drawBurnLab(point, scale, ::stroke)
                "fit_blue_rift" -> drawBlueRiftII(point, scale, ::stroke)
                "fit_lab_king" -> drawLabKing(point, scale, ::stroke)
                else -> drawMiracleCrown(point, scale, ::stroke)
            }
        }
    }
}

private fun octagon(p: (Float, Float) -> Offset): Path = Path().apply {
    moveTo(p(80f, 7f).x, p(80f, 7f).y)
    lineTo(p(124f, 23f).x, p(124f, 23f).y)
    lineTo(p(151f, 65f).x, p(151f, 65f).y)
    lineTo(p(141f, 116f).x, p(141f, 116f).y)
    lineTo(p(101f, 153f).x, p(101f, 153f).y)
    lineTo(p(59f, 153f).x, p(59f, 153f).y)
    lineTo(p(19f, 116f).x, p(19f, 116f).y)
    lineTo(p(9f, 65f).x, p(9f, 65f).y)
    lineTo(p(36f, 23f).x, p(36f, 23f).y)
    close()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSerpent(
    p: (Float, Float) -> Offset,
    style: Stroke,
    color: Color = BadgeDemon,
) {
    val path = Path().apply {
        moveTo(p(49f, 106f).x, p(49f, 106f).y)
        cubicTo(p(83f, 121f).x, p(83f, 121f).y, p(114f, 101f).x, p(114f, 101f).y, p(94f, 78f).x, p(94f, 78f).y)
        cubicTo(p(80f, 62f).x, p(80f, 62f).y, p(55f, 69f).x, p(55f, 69f).y, p(60f, 88f).x, p(60f, 88f).y)
        cubicTo(p(64f, 102f).x, p(64f, 102f).y, p(86f, 100f).x, p(86f, 100f).y, p(88f, 85f).x, p(88f, 85f).y)
    }
    drawPath(path, color = color, style = style)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlasses(p: (Float, Float) -> Offset, scale: Float, style: Stroke) {
    drawLine(BadgeCream.copy(alpha = 0.78f), p(45f, 58f), p(115f, 58f), 2.2f * scale, StrokeCap.Round)
    drawRoundRect(BadgeDemon.copy(alpha = 0.48f), topLeft = p(47f, 58f), size = androidx.compose.ui.geometry.Size(28f * scale, 17f * scale), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * scale))
    drawRoundRect(BadgeDemon.copy(alpha = 0.48f), topLeft = p(85f, 58f), size = androidx.compose.ui.geometry.Size(28f * scale, 17f * scale), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * scale))
    drawRoundRect(BadgeGold, topLeft = p(47f, 58f), size = androidx.compose.ui.geometry.Size(28f * scale, 17f * scale), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * scale), style = style)
    drawRoundRect(BadgeGold, topLeft = p(85f, 58f), size = androidx.compose.ui.geometry.Size(28f * scale, 17f * scale), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f * scale), style = style)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRedMirror(p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke) {
    drawSerpent(p, stroke(BadgeDemon, 5f))
    drawGlasses(p, scale, stroke(BadgeGold, 2f))
    drawStar(p, BadgeGold)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOldBookHalo(p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke) {
    drawOval(BadgeGold, topLeft = p(54f, 33f), size = androidx.compose.ui.geometry.Size(52f * scale, 18f * scale), style = stroke(BadgeGold, 4f))
    drawWing(p, left = true)
    drawWing(p, left = false)
    val book = Path().apply {
        moveTo(p(58f, 80f).x, p(58f, 80f).y)
        cubicTo(p(68f, 70f).x, p(68f, 70f).y, p(76f, 71f).x, p(76f, 71f).y, p(80f, 77f).x, p(80f, 77f).y)
        cubicTo(p(84f, 71f).x, p(84f, 71f).y, p(92f, 70f).x, p(92f, 70f).y, p(102f, 80f).x, p(102f, 80f).y)
        lineTo(p(102f, 113f).x, p(102f, 113f).y)
        cubicTo(p(92f, 107f).x, p(92f, 107f).y, p(84f, 107f).x, p(84f, 107f).y, p(80f, 113f).x, p(80f, 113f).y)
        cubicTo(p(76f, 107f).x, p(76f, 107f).y, p(68f, 107f).x, p(68f, 107f).y, p(58f, 113f).x, p(58f, 113f).y)
        close()
    }
    drawPath(book, BadgeCream)
    drawPath(book, BadgeGold, style = stroke(BadgeGold, 2f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTimeRunner(p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke) {
    drawVortex(p, stroke(BadgeTime, 4f))
    val coat = Path().apply {
        moveTo(p(65f, 52f).x, p(65f, 52f).y)
        lineTo(p(95f, 52f).x, p(95f, 52f).y)
        lineTo(p(105f, 109f).x, p(105f, 109f).y)
        lineTo(p(55f, 109f).x, p(55f, 109f).y)
        close()
    }
    drawPath(coat, Color(0xFF8A643A))
    drawPath(coat, BadgeGold, style = stroke(BadgeGold, 2f))
    listOf(71f, 80f, 89f).forEach { x -> drawLine(Color.White.copy(alpha = 0.72f), p(x, 58f), p(x, 100f), 2f * scale, StrokeCap.Round) }
    drawLine(BadgeDemon, p(56f, 118f), p(78f, 118f), 7f * scale, StrokeCap.Round)
    drawLine(BadgeDemon, p(85f, 118f), p(107f, 118f), 7f * scale, StrokeCap.Round)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDoubleRehearsal(p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke) {
    drawStageCurtain(p, true)
    drawStageCurtain(p, false)
    drawRoundRect(BadgeCream, topLeft = p(58f, 58f), size = androidx.compose.ui.geometry.Size(20f * scale, 22f * scale), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale))
    drawRoundRect(BadgeInk, topLeft = p(82f, 58f), size = androidx.compose.ui.geometry.Size(20f * scale, 22f * scale), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f * scale))
    drawLine(Color.White.copy(alpha = 0.78f), p(62f, 66f), p(74f, 66f), 2f * scale, StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.78f), p(86f, 66f), p(98f, 66f), 2f * scale, StrokeCap.Round)
    drawLine(BadgeGold, p(53f, 119f), p(107f, 119f), 5f * scale, StrokeCap.Round)
    drawCircle(BadgeGold, radius = 8f * scale, center = p(80f, 38f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSerpentWings(p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke) {
    drawWing(p, true)
    drawWing(p, false)
    drawSerpent(p, stroke(BadgeDemon, 5f))
    drawOval(BadgeGold, topLeft = p(56f, 40f), size = androidx.compose.ui.geometry.Size(48f * scale, 16f * scale), style = stroke(BadgeGold, 4f))
    drawLine(Color.White.copy(alpha = 0.75f), p(60f, 121f), p(100f, 121f), 2f * scale, StrokeCap.Round)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlueRift(p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke) {
    drawVortex(p, stroke(BadgeTime, 4f))
    val bolt = Path().apply {
        moveTo(p(83f, 31f).x, p(83f, 31f).y)
        lineTo(p(70f, 70f).x, p(70f, 70f).y)
        lineTo(p(92f, 78f).x, p(92f, 78f).y)
        lineTo(p(61f, 130f).x, p(61f, 130f).y)
    }
    drawPath(bolt, BadgeGold, style = stroke(BadgeGold, 5f))
    drawLine(BadgeDemon, p(54f, 111f), p(75f, 98f), 6f * scale, StrokeCap.Round)
    drawLine(BadgeDemon, p(106f, 111f), p(85f, 98f), 6f * scale, StrokeCap.Round)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNightGuard(p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke) {
    drawSerpent(p, stroke(BadgeDemon, 5f))
    drawGlasses(p, scale, stroke(BadgeGold, 2f))
    val arc = Path().apply {
        moveTo(p(44f, 126f).x, p(44f, 126f).y)
        cubicTo(p(66f, 112f).x, p(66f, 112f).y, p(94f, 112f).x, p(94f, 112f).y, p(116f, 126f).x, p(116f, 126f).y)
    }
    drawPath(arc, BadgeTime, style = stroke(BadgeTime, 4f))
    drawCircle(BadgeGold, radius = 6f * scale, center = p(80f, 31f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMiracleCrown(p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke) {
    drawWing(p, true)
    drawWing(p, false)
    drawVortex(p, stroke(BadgeTime, 3.5f))
    drawSerpent(p, stroke(BadgeDemon, 4f))
    val crown = Path().apply {
        moveTo(p(46f, 111f).x, p(46f, 111f).y)
        lineTo(p(58f, 65f).x, p(58f, 65f).y)
        lineTo(p(74f, 87f).x, p(74f, 87f).y)
        lineTo(p(80f, 44f).x, p(80f, 44f).y)
        lineTo(p(86f, 87f).x, p(86f, 87f).y)
        lineTo(p(102f, 65f).x, p(102f, 65f).y)
        lineTo(p(114f, 111f).x, p(114f, 111f).y)
        close()
    }
    drawPath(crown, BadgeGold.copy(alpha = 0.86f))
    drawOval(BadgeGold, topLeft = p(51f, 28f), size = androidx.compose.ui.geometry.Size(58f * scale, 20f * scale), style = stroke(BadgeGold, 4f))
    drawLine(BadgeStage, p(55f, 128f), p(105f, 128f), 7f * scale, StrokeCap.Round)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWing(p: (Float, Float) -> Offset, left: Boolean) {
    val wing = Path().apply {
        if (left) {
            moveTo(p(40f, 88f).x, p(40f, 88f).y)
            cubicTo(p(55f, 63f).x, p(55f, 63f).y, p(71f, 58f).x, p(71f, 58f).y, p(80f, 74f).x, p(80f, 74f).y)
            cubicTo(p(63f, 76f).x, p(63f, 76f).y, p(53f, 88f).x, p(53f, 88f).y, p(47f, 102f).x, p(47f, 102f).y)
            cubicTo(p(42f, 100f).x, p(42f, 100f).y, p(39f, 94f).x, p(39f, 94f).y, p(40f, 88f).x, p(40f, 88f).y)
        } else {
            moveTo(p(120f, 88f).x, p(120f, 88f).y)
            cubicTo(p(105f, 63f).x, p(105f, 63f).y, p(89f, 58f).x, p(89f, 58f).y, p(80f, 74f).x, p(80f, 74f).y)
            cubicTo(p(97f, 76f).x, p(97f, 76f).y, p(107f, 88f).x, p(107f, 88f).y, p(113f, 102f).x, p(113f, 102f).y)
            cubicTo(p(118f, 100f).x, p(118f, 100f).y, p(121f, 94f).x, p(121f, 94f).y, p(120f, 88f).x, p(120f, 88f).y)
        }
        close()
    }
    drawPath(wing, BadgeCream)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStageCurtain(p: (Float, Float) -> Offset, left: Boolean) {
    val curtain = Path().apply {
        if (left) {
            moveTo(p(38f, 44f).x, p(38f, 44f).y)
            cubicTo(p(56f, 58f).x, p(56f, 58f).y, p(57f, 104f).x, p(57f, 104f).y, p(38f, 122f).x, p(38f, 122f).y)
            close()
        } else {
            moveTo(p(122f, 44f).x, p(122f, 44f).y)
            cubicTo(p(104f, 58f).x, p(104f, 58f).y, p(103f, 104f).x, p(103f, 104f).y, p(122f, 122f).x, p(122f, 122f).y)
            close()
        }
    }
    drawPath(curtain, BadgeStage)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVortex(p: (Float, Float) -> Offset, style: Stroke) {
    val vortex = Path().apply {
        moveTo(p(114f, 45f).x, p(114f, 45f).y)
        cubicTo(p(86f, 28f).x, p(86f, 28f).y, p(42f, 40f).x, p(42f, 40f).y, p(37f, 73f).x, p(37f, 73f).y)
        cubicTo(p(31f, 112f).x, p(31f, 112f).y, p(92f, 128f).x, p(92f, 128f).y, p(119f, 94f).x, p(119f, 94f).y)
        cubicTo(p(141f, 66f).x, p(141f, 66f).y, p(101f, 37f).x, p(101f, 37f).y, p(73f, 58f).x, p(73f, 58f).y)
        cubicTo(p(51f, 75f).x, p(51f, 75f).y, p(67f, 103f).x, p(67f, 103f).y, p(96f, 89f).x, p(96f, 89f).y)
    }
    drawPath(vortex, BadgeTime, style = style)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(p: (Float, Float) -> Offset, color: Color) {
    val star = Path().apply {
        moveTo(p(80f, 30f).x, p(80f, 30f).y)
        lineTo(p(84f, 42f).x, p(84f, 42f).y)
        lineTo(p(97f, 42f).x, p(97f, 42f).y)
        lineTo(p(86f, 49f).x, p(86f, 49f).y)
        lineTo(p(91f, 62f).x, p(91f, 62f).y)
        lineTo(p(80f, 54f).x, p(80f, 54f).y)
        lineTo(p(69f, 62f).x, p(69f, 62f).y)
        lineTo(p(74f, 49f).x, p(74f, 49f).y)
        lineTo(p(63f, 42f).x, p(63f, 42f).y)
        lineTo(p(76f, 42f).x, p(76f, 42f).y)
        close()
    }
    drawPath(star, color)
}

// ════════════════════════════════════════════════════════════════════
// 健身运动徽章 · 实验室多元宇宙
// ════════════════════════════════════════════════════════════════════

// 01 第一滴汗：埃伦迈耶烧瓶 + 上方汗滴 + 气泡
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFirstSweat(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    val flask = Path().apply {
        moveTo(p(68f, 50f).x, p(68f, 50f).y)
        lineTo(p(68f, 70f).x, p(68f, 70f).y)
        lineTo(p(52f, 110f).x, p(52f, 110f).y)
        cubicTo(p(50f, 122f).x, p(50f, 122f).y, p(58f, 124f).x, p(58f, 124f).y, p(64f, 122f).x, p(64f, 122f).y)
        lineTo(p(98f, 122f).x, p(98f, 122f).y)
        cubicTo(p(106f, 124f).x, p(106f, 124f).y, p(110f, 122f).x, p(110f, 122f).y, p(108f, 110f).x, p(108f, 110f).y)
        lineTo(p(92f, 70f).x, p(92f, 70f).y)
        lineTo(p(92f, 50f).x, p(92f, 50f).y)
        close()
    }
    drawPath(flask, Color.White.copy(alpha = 0.06f))
    drawPath(flask, BadgeCream, style = stroke(BadgeCream, 2.4f))
    val liquid = Path().apply {
        moveTo(p(62f, 92f).x, p(62f, 92f).y)
        lineTo(p(98f, 92f).x, p(98f, 92f).y)
        lineTo(p(106f, 110f).x, p(106f, 110f).y)
        cubicTo(p(108f, 122f).x, p(108f, 122f).y, p(102f, 124f).x, p(102f, 124f).y, p(96f, 122f).x, p(96f, 122f).y)
        lineTo(p(64f, 122f).x, p(64f, 122f).y)
        cubicTo(p(58f, 124f).x, p(58f, 124f).y, p(52f, 122f).x, p(52f, 122f).y, p(54f, 110f).x, p(54f, 110f).y)
        close()
    }
    drawPath(liquid, BadgeAcid.copy(alpha = 0.85f))
    drawOval(BadgeCream.copy(alpha = 0.6f), topLeft = p(62f, 90f), size = androidx.compose.ui.geometry.Size(36f * scale, 4.8f * scale))
    drawRoundRect(
        BadgeGold,
        topLeft = p(64f, 46f),
        size = androidx.compose.ui.geometry.Size(32f * scale, 6f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale)
    )
    val drop = Path().apply {
        moveTo(p(80f, 22f).x, p(80f, 22f).y)
        cubicTo(p(70f, 36f).x, p(70f, 36f).y, p(76f, 44f).x, p(76f, 44f).y, p(80f, 42f).x, p(80f, 42f).y)
        cubicTo(p(84f, 44f).x, p(84f, 44f).y, p(90f, 36f).x, p(90f, 36f).y, p(80f, 22f).x, p(80f, 22f).y)
        close()
    }
    drawPath(drop, BadgeAcid)
    drawCircle(BadgeCream.copy(alpha = 0.7f), radius = 3f * scale, center = p(80f, 35f))
    drawCircle(BadgeCream.copy(alpha = 0.7f), radius = 2.2f * scale, center = p(70f, 104f))
    drawCircle(BadgeCream.copy(alpha = 0.6f), radius = 1.6f * scale, center = p(88f, 112f))
    drawCircle(BadgeCream.copy(alpha = 0.5f), radius = 1.2f * scale, center = p(78f, 116f))
}

// 02 维度入口：三层椭圆传送门 + 粒子
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPortalEntry(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    drawOval(
        BadgeAcid.copy(alpha = 0.35f),
        topLeft = p(44f, 52f),
        size = androidx.compose.ui.geometry.Size(72f * scale, 64f * scale)
    )
    drawOval(
        BadgeAcid,
        topLeft = p(44f, 52f),
        size = androidx.compose.ui.geometry.Size(72f * scale, 64f * scale),
        style = stroke(BadgeAcid, 2.4f)
    )
    drawOval(
        BadgeCream.copy(alpha = 0.55f),
        topLeft = p(52f, 60f),
        size = androidx.compose.ui.geometry.Size(56f * scale, 48f * scale),
        style = stroke(BadgeCream, 1.4f)
    )
    drawOval(
        BadgeCream.copy(alpha = 0.4f),
        topLeft = p(62f, 70f),
        size = androidx.compose.ui.geometry.Size(36f * scale, 28f * scale),
        style = stroke(BadgeCream, 1f)
    )
    drawCircle(BadgeAcid, radius = 1.6f * scale, center = p(46f, 60f))
    drawCircle(BadgeCream, radius = 1.4f * scale, center = p(120f, 56f))
    drawCircle(BadgeRift, radius = 1.8f * scale, center = p(36f, 106f))
    drawCircle(BadgeAcid, radius = 1.2f * scale, center = p(124f, 112f))
    drawCircle(BadgeGold, radius = 2.4f * scale, center = p(80f, 36f))
    drawCircle(BadgeCream.copy(alpha = 0.7f), radius = 1.6f * scale, center = p(80f, 132f))
}

// 03 训练节奏：节拍器 + 摆杆 + 后置 ECG
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrainingRhythm(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    val ecg = Path().apply {
        moveTo(p(22f, 36f).x, p(22f, 36f).y)
        lineTo(p(40f, 36f).x, p(40f, 36f).y)
        lineTo(p(46f, 22f).x, p(46f, 22f).y)
        lineTo(p(52f, 52f).x, p(52f, 52f).y)
        lineTo(p(58f, 30f).x, p(58f, 30f).y)
        lineTo(p(64f, 36f).x, p(64f, 36f).y)
        lineTo(p(138f, 36f).x, p(138f, 36f).y)
    }
    drawPath(ecg, BadgeRift.copy(alpha = 0.55f), style = stroke(BadgeRift, 1.6f))
    val base = Path().apply {
        moveTo(p(58f, 122f).x, p(58f, 122f).y)
        lineTo(p(102f, 122f).x, p(102f, 122f).y)
        lineTo(p(94f, 64f).x, p(94f, 64f).y)
        lineTo(p(66f, 64f).x, p(66f, 64f).y)
        close()
    }
    drawPath(base, Color.White.copy(alpha = 0.06f))
    drawPath(base, BadgeCream, style = stroke(BadgeCream, 2.4f))
    drawLine(BadgeCream.copy(alpha = 0.5f), p(68f, 80f), p(92f, 80f), 1f * scale, StrokeCap.Round)
    drawLine(BadgeCream.copy(alpha = 0.4f), p(69f, 92f), p(91f, 92f), 1f * scale, StrokeCap.Round)
    drawLine(BadgeCream.copy(alpha = 0.3f), p(70f, 104f), p(90f, 104f), 1f * scale, StrokeCap.Round)
    drawLine(BadgeAcid, p(80f, 122f), p(98f, 46f), 3.6f * scale, StrokeCap.Round)
    drawCircle(BadgeGold, radius = 6f * scale, center = p(98f, 46f))
    drawCircle(BadgeCream, radius = 3f * scale, center = p(80f, 122f))
}

// 04 一周连击：七格能量条（5 高 2 低）
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWeekCombo(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    val pattern = listOf(true, true, true, false, true, true, false)
    pattern.forEachIndexed { i, on ->
        val x = 26f + i * 16f
        val h = if (on) 56f else 28f
        val y = 116f - h
        val fill = if (on) BadgeAcid else Color.White.copy(alpha = 0.08f)
        val strokeColor = if (on) BadgeAcidDeep else Color.White.copy(alpha = 0.18f)
        drawRoundRect(
            fill,
            topLeft = p(x, y),
            size = androidx.compose.ui.geometry.Size(10f * scale, h * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale)
        )
        drawRoundRect(
            strokeColor,
            topLeft = p(x, y),
            size = androidx.compose.ui.geometry.Size(10f * scale, h * scale),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale),
            style = stroke(strokeColor, 1.2f)
        )
        if (on) {
            drawRoundRect(
                BadgeCream.copy(alpha = 0.5f),
                topLeft = p(x + 1f, y + 2f),
                size = androidx.compose.ui.geometry.Size(3f * scale, (h - 4f) * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * scale)
            )
        }
    }
    drawLine(BadgeGold, p(22f, 126f), p(138f, 126f), 2.4f * scale, StrokeCap.Round)
}

// 05 力量分子：双向原子轨道 + 中央哑铃
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawForceAtom(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    // 横轨
    drawOval(
        BadgeCaution.copy(alpha = 0.85f),
        topLeft = p(38f, 66f),
        size = androidx.compose.ui.geometry.Size(84f * scale, 28f * scale),
        style = stroke(BadgeCaution, 2f)
    )
    // 竖轨
    drawOval(
        BadgeCaution.copy(alpha = 0.7f),
        topLeft = p(66f, 38f),
        size = androidx.compose.ui.geometry.Size(28f * scale, 84f * scale),
        style = stroke(BadgeCaution, 2f)
    )
    // 哑铃杆
    drawRoundRect(
        BadgeHazardDeep,
        topLeft = p(58f, 74f),
        size = androidx.compose.ui.geometry.Size(44f * scale, 12f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale)
    )
    drawRoundRect(
        BadgeGold,
        topLeft = p(58f, 74f),
        size = androidx.compose.ui.geometry.Size(44f * scale, 12f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale),
        style = stroke(BadgeGold, 1.4f)
    )
    // 哑铃片
    drawRoundRect(
        BadgeGold,
        topLeft = p(48f, 64f),
        size = androidx.compose.ui.geometry.Size(12f * scale, 32f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale)
    )
    drawRoundRect(
        BadgeGold,
        topLeft = p(100f, 64f),
        size = androidx.compose.ui.geometry.Size(12f * scale, 32f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale)
    )
    drawRoundRect(
        BadgeHazard,
        topLeft = p(44f, 70f),
        size = androidx.compose.ui.geometry.Size(6f * scale, 20f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale)
    )
    drawRoundRect(
        BadgeHazard,
        topLeft = p(110f, 70f),
        size = androidx.compose.ui.geometry.Size(6f * scale, 20f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale)
    )
    // 电子粒子
    drawCircle(BadgeGold, radius = 3f * scale, center = p(122f, 80f))
    drawCircle(BadgeCream.copy(alpha = 0.9f), radius = 2.4f * scale, center = p(46f, 56f))
    drawCircle(BadgeCream.copy(alpha = 0.8f), radius = 2f * scale, center = p(116f, 108f))
}

// 06 怪兽实验体：试管 + 红液 + 怪兽剪影
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBeastSubject(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    val tube = Path().apply {
        moveTo(p(60f, 40f).x, p(60f, 40f).y)
        lineTo(p(60f, 116f).x, p(60f, 116f).y)
        cubicTo(p(60f, 128f).x, p(60f, 128f).y, p(70f, 132f).x, p(70f, 132f).y, p(80f, 128f).x, p(80f, 128f).y)
        cubicTo(p(90f, 132f).x, p(90f, 132f).y, p(100f, 128f).x, p(100f, 128f).y, p(100f, 116f).x, p(100f, 116f).y)
        lineTo(p(100f, 40f).x, p(100f, 40f).y)
        close()
    }
    drawPath(tube, Color.White.copy(alpha = 0.05f))
    drawPath(tube, BadgeCream, style = stroke(BadgeCream, 2.4f))
    drawRoundRect(
        BadgeGold,
        topLeft = p(56f, 36f),
        size = androidx.compose.ui.geometry.Size(48f * scale, 8f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * scale)
    )
    // 红液
    val liquid = Path().apply {
        moveTo(p(60f, 78f).x, p(60f, 78f).y)
        lineTo(p(100f, 78f).x, p(100f, 78f).y)
        lineTo(p(100f, 116f).x, p(100f, 116f).y)
        cubicTo(p(100f, 128f).x, p(100f, 128f).y, p(90f, 132f).x, p(90f, 132f).y, p(80f, 128f).x, p(80f, 128f).y)
        cubicTo(p(70f, 132f).x, p(70f, 132f).y, p(60f, 128f).x, p(60f, 128f).y, p(60f, 116f).x, p(60f, 116f).y)
        close()
    }
    drawPath(liquid, BadgeDemon.copy(alpha = 0.75f))
    drawOval(BadgeCream.copy(alpha = 0.6f), topLeft = p(60f, 76f), size = androidx.compose.ui.geometry.Size(40f * scale, 4.8f * scale))
    // 怪兽身体
    drawCircle(BadgeInk, radius = 14f * scale, center = p(80f, 98f))
    drawCircle(BadgeCaution, radius = 3f * scale, center = p(74f, 94f))
    drawCircle(BadgeCaution, radius = 3f * scale, center = p(86f, 94f))
    val mouth = Path().apply {
        moveTo(p(72f, 106f).x, p(72f, 106f).y)
        cubicTo(p(76f, 110f).x, p(76f, 110f).y, p(84f, 110f).x, p(84f, 110f).y, p(88f, 106f).x, p(88f, 106f).y)
    }
    drawPath(mouth, BadgeCream, style = stroke(BadgeCream, 1.6f))
    // 举重小臂
    drawLine(BadgeInk, p(66f, 100f), p(58f, 86f), 4f * scale, StrokeCap.Round)
    drawLine(BadgeInk, p(94f, 100f), p(102f, 86f), 4f * scale, StrokeCap.Round)
    drawRoundRect(
        BadgeGold,
        topLeft = p(52f, 80f),
        size = androidx.compose.ui.geometry.Size(12f * scale, 4f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * scale)
    )
    drawRoundRect(
        BadgeGold,
        topLeft = p(96f, 80f),
        size = androidx.compose.ui.geometry.Size(12f * scale, 4f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * scale)
    )
    // 气泡
    drawCircle(BadgeCream.copy(alpha = 0.7f), radius = 2f * scale, center = p(68f, 118f))
    drawCircle(BadgeCream.copy(alpha = 0.6f), radius = 1.6f * scale, center = p(90f, 122f))
}

// 07 续航之核：六边形反应堆 + 中央亮核
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoreBattery(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    val hex = Path().apply {
        moveTo(p(80f, 36f).x, p(80f, 36f).y)
        lineTo(p(114f, 56f).x, p(114f, 56f).y)
        lineTo(p(114f, 104f).x, p(114f, 104f).y)
        lineTo(p(80f, 124f).x, p(80f, 124f).y)
        lineTo(p(46f, 104f).x, p(46f, 104f).y)
        lineTo(p(46f, 56f).x, p(46f, 56f).y)
        close()
    }
    drawPath(hex, BadgeRift.copy(alpha = 0.1f))
    drawPath(hex, BadgeRift, style = stroke(BadgeRift, 2.4f))
    val innerHex = Path().apply {
        moveTo(p(80f, 44f).x, p(80f, 44f).y)
        lineTo(p(106f, 60f).x, p(106f, 60f).y)
        lineTo(p(106f, 100f).x, p(106f, 100f).y)
        lineTo(p(80f, 116f).x, p(80f, 116f).y)
        lineTo(p(54f, 100f).x, p(54f, 100f).y)
        lineTo(p(54f, 60f).x, p(54f, 60f).y)
        close()
    }
    drawPath(innerHex, BadgeCream.copy(alpha = 0.5f), style = stroke(BadgeCream, 1.2f))
    // 光晕
    drawCircle(BadgeAcid.copy(alpha = 0.35f), radius = 22f * scale, center = p(80f, 80f))
    drawCircle(BadgeAcid, radius = 14f * scale, center = p(80f, 80f))
    drawCircle(BadgeCream, radius = 6f * scale, center = p(80f, 80f))
    drawCircle(BadgeAcid.copy(alpha = 0.6f), radius = 20f * scale, center = p(80f, 80f), style = stroke(BadgeAcid, 1f))
    // 侧弧
    val leftArc = Path().apply {
        moveTo(p(30f, 70f).x, p(30f, 70f).y)
        cubicTo(p(30f, 80f).x, p(30f, 80f).y, p(30f, 90f).x, p(30f, 90f).y, p(30f, 90f).x, p(30f, 90f).y)
    }
    drawPath(leftArc, BadgeRift.copy(alpha = 0.7f), style = stroke(BadgeRift, 2f))
    val rightArc = Path().apply {
        moveTo(p(130f, 70f).x, p(130f, 70f).y)
        cubicTo(p(130f, 80f).x, p(130f, 80f).y, p(130f, 90f).x, p(130f, 90f).y, p(130f, 90f).x, p(130f, 90f).y)
    }
    drawPath(rightArc, BadgeRift.copy(alpha = 0.7f), style = stroke(BadgeRift, 2f))
}

// 08 万步穿越：传送门 + 三组脚印
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStepWarp(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    drawOval(
        BadgeAcid.copy(alpha = 0.45f),
        topLeft = p(40f, 54f),
        size = androidx.compose.ui.geometry.Size(80f * scale, 60f * scale)
    )
    drawOval(
        BadgeAcid,
        topLeft = p(40f, 54f),
        size = androidx.compose.ui.geometry.Size(80f * scale, 60f * scale),
        style = stroke(BadgeAcid, 2f)
    )
    drawOval(
        BadgePlasma.copy(alpha = 0.5f),
        topLeft = p(52f, 66f),
        size = androidx.compose.ui.geometry.Size(56f * scale, 36f * scale),
        style = stroke(BadgePlasma, 1.2f)
    )
    // 脚印 - 后
    drawOval(BadgeCream.copy(alpha = 0.9f), topLeft = p(54f, 94f), size = androidx.compose.ui.geometry.Size(8f * scale, 12f * scale))
    drawCircle(BadgeCream.copy(alpha = 0.9f), radius = 1.6f * scale, center = p(55f, 90f))
    drawCircle(BadgeCream.copy(alpha = 0.9f), radius = 1.4f * scale, center = p(60f, 89f))
    // 脚印 - 中
    drawOval(BadgeAcid, topLeft = p(72f, 80f), size = androidx.compose.ui.geometry.Size(8.8f * scale, 12.8f * scale))
    drawCircle(BadgeAcid, radius = 1.6f * scale, center = p(74f, 76f))
    drawCircle(BadgeAcid, radius = 1.4f * scale, center = p(79f, 75f))
    // 脚印 - 前（即将穿过）
    drawOval(BadgeCream.copy(alpha = 0.5f), topLeft = p(94f, 68f), size = androidx.compose.ui.geometry.Size(7.2f * scale, 10.8f * scale))
}

// 09 坡度裂隙：山峦 + 山尖闪电 + 旗帜
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClimbRift(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    val backRange = Path().apply {
        moveTo(p(30f, 124f).x, p(30f, 124f).y)
        lineTo(p(60f, 84f).x, p(60f, 84f).y)
        lineTo(p(80f, 106f).x, p(80f, 106f).y)
        lineTo(p(100f, 70f).x, p(100f, 70f).y)
        lineTo(p(132f, 124f).x, p(132f, 124f).y)
        close()
    }
    drawPath(backRange, BadgeHazardDeep.copy(alpha = 0.55f))
    drawPath(backRange, BadgeCream.copy(alpha = 0.55f), style = stroke(BadgeCream, 1.6f))
    val frontRange = Path().apply {
        moveTo(p(36f, 130f).x, p(36f, 130f).y)
        lineTo(p(70f, 78f).x, p(70f, 78f).y)
        lineTo(p(84f, 96f).x, p(84f, 96f).y)
        lineTo(p(108f, 62f).x, p(108f, 62f).y)
        lineTo(p(130f, 130f).x, p(130f, 130f).y)
        close()
    }
    drawPath(frontRange, BadgeHazard)
    drawPath(frontRange, BadgeGold, style = stroke(BadgeGold, 2.4f))
    // 雪冠高光
    val snow = Path().apply {
        moveTo(p(104f, 68f).x, p(104f, 68f).y)
        lineTo(p(108f, 62f).x, p(108f, 62f).y)
        lineTo(p(114f, 72f).x, p(114f, 72f).y)
    }
    drawPath(snow, BadgeCream, style = stroke(BadgeCream, 2f))
    // 闪电
    val bolt = Path().apply {
        moveTo(p(108f, 24f).x, p(108f, 24f).y)
        lineTo(p(98f, 60f).x, p(98f, 60f).y)
        lineTo(p(114f, 60f).x, p(114f, 60f).y)
        lineTo(p(96f, 96f).x, p(96f, 96f).y)
    }
    drawPath(bolt, BadgeCaution, style = stroke(BadgeCaution, 3.6f))
    // 旗
    drawLine(BadgeCream, p(108f, 62f), p(108f, 44f), 1.6f * scale)
    val flag = Path().apply {
        moveTo(p(108f, 44f).x, p(108f, 44f).y)
        lineTo(p(120f, 48f).x, p(120f, 48f).y)
        lineTo(p(108f, 52f).x, p(108f, 52f).y)
        close()
    }
    drawPath(flag, BadgeDemon)
}

// 10 燃烧实验：本生灯火焰 + 烧瓶
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBurnLab(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    val flame = Path().apply {
        moveTo(p(80f, 22f).x, p(80f, 22f).y)
        cubicTo(p(66f, 40f).x, p(66f, 40f).y, p(70f, 56f).x, p(70f, 56f).y, p(70f, 56f).x, p(70f, 56f).y)
        cubicTo(p(60f, 50f).x, p(60f, 50f).y, p(64f, 66f).x, p(64f, 66f).y, p(64f, 66f).x, p(64f, 66f).y)
        cubicTo(p(52f, 72f).x, p(52f, 72f).y, p(64f, 86f).x, p(64f, 86f).y, p(64f, 86f).x, p(64f, 86f).y)
        cubicTo(p(60f, 96f).x, p(60f, 96f).y, p(80f, 92f).x, p(80f, 92f).y, p(80f, 92f).x, p(80f, 92f).y)
        cubicTo(p(100f, 96f).x, p(100f, 96f).y, p(96f, 86f).x, p(96f, 86f).y, p(96f, 86f).x, p(96f, 86f).y)
        cubicTo(p(108f, 72f).x, p(108f, 72f).y, p(96f, 66f).x, p(96f, 66f).y, p(96f, 66f).x, p(96f, 66f).y)
        cubicTo(p(100f, 50f).x, p(100f, 50f).y, p(90f, 56f).x, p(90f, 56f).y, p(90f, 56f).x, p(90f, 56f).y)
        cubicTo(p(94f, 40f).x, p(94f, 40f).y, p(80f, 22f).x, p(80f, 22f).y, p(80f, 22f).x, p(80f, 22f).y)
        close()
    }
    drawPath(flame, BadgeHazard)
    val innerFlame = Path().apply {
        moveTo(p(80f, 36f).x, p(80f, 36f).y)
        cubicTo(p(72f, 50f).x, p(72f, 50f).y, p(76f, 64f).x, p(76f, 64f).y, p(76f, 64f).x, p(76f, 64f).y)
        cubicTo(p(70f, 60f).x, p(70f, 60f).y, p(74f, 74f).x, p(74f, 74f).y, p(74f, 74f).x, p(74f, 74f).y)
        cubicTo(p(68f, 78f).x, p(68f, 78f).y, p(78f, 84f).x, p(78f, 84f).y, p(78f, 84f).x, p(78f, 84f).y)
        cubicTo(p(88f, 78f).x, p(88f, 78f).y, p(82f, 74f).x, p(82f, 74f).y, p(82f, 74f).x, p(82f, 74f).y)
        cubicTo(p(86f, 60f).x, p(86f, 60f).y, p(84f, 64f).x, p(84f, 64f).y, p(84f, 64f).x, p(84f, 64f).y)
        cubicTo(p(88f, 50f).x, p(88f, 50f).y, p(80f, 36f).x, p(80f, 36f).y, p(80f, 36f).x, p(80f, 36f).y)
        close()
    }
    drawPath(innerFlame, BadgeCaution)
    // 烧瓶
    val beaker = Path().apply {
        moveTo(p(58f, 92f).x, p(58f, 92f).y)
        lineTo(p(102f, 92f).x, p(102f, 92f).y)
        lineTo(p(98f, 124f).x, p(98f, 124f).y)
        cubicTo(p(96f, 132f).x, p(96f, 132f).y, p(92f, 132f).x, p(92f, 132f).y, p(88f, 132f).x, p(88f, 132f).y)
        lineTo(p(72f, 132f).x, p(72f, 132f).y)
        cubicTo(p(68f, 132f).x, p(68f, 132f).y, p(64f, 132f).x, p(64f, 132f).y, p(62f, 124f).x, p(62f, 124f).y)
        close()
    }
    drawPath(beaker, Color.White.copy(alpha = 0.06f))
    drawPath(beaker, BadgeCream, style = stroke(BadgeCream, 2.4f))
    drawRoundRect(
        BadgeGold,
        topLeft = p(56f, 88f),
        size = androidx.compose.ui.geometry.Size(48f * scale, 6f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale)
    )
    val liq = Path().apply {
        moveTo(p(62f, 110f).x, p(62f, 110f).y)
        lineTo(p(98f, 110f).x, p(98f, 110f).y)
        lineTo(p(96f, 124f).x, p(96f, 124f).y)
        cubicTo(p(94f, 130f).x, p(94f, 130f).y, p(90f, 130f).x, p(90f, 130f).y, p(88f, 130f).x, p(88f, 130f).y)
        lineTo(p(72f, 130f).x, p(72f, 130f).y)
        cubicTo(p(70f, 130f).x, p(70f, 130f).y, p(66f, 130f).x, p(66f, 130f).y, p(64f, 124f).x, p(64f, 124f).y)
        close()
    }
    drawPath(liq, BadgeDemon.copy(alpha = 0.75f))
    drawLine(BadgeCream.copy(alpha = 0.6f), p(70f, 100f), p(76f, 100f), 1f * scale, StrokeCap.Round)
    drawLine(BadgeCream.copy(alpha = 0.6f), p(84f, 100f), p(90f, 100f), 1f * scale, StrokeCap.Round)
}

// 11 蓝色裂隙 II：大椭圆传送门 + 之字闪电
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlueRiftII(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    drawOval(
        BadgeRift.copy(alpha = 0.35f),
        topLeft = p(36f, 44f),
        size = androidx.compose.ui.geometry.Size(88f * scale, 72f * scale)
    )
    // 上下虚弧
    val topArc = Path().apply {
        moveTo(p(48f, 70f).x, p(48f, 70f).y)
        cubicTo(p(60f, 50f).x, p(60f, 50f).y, p(80f, 50f).x, p(80f, 50f).y, p(80f, 50f).x, p(80f, 50f).y)
        cubicTo(p(100f, 50f).x, p(100f, 50f).y, p(112f, 70f).x, p(112f, 70f).y, p(112f, 70f).x, p(112f, 70f).y)
    }
    drawPath(topArc, BadgeRift.copy(alpha = 0.85f), style = stroke(BadgeRift, 2f))
    val botArc = Path().apply {
        moveTo(p(48f, 90f).x, p(48f, 90f).y)
        cubicTo(p(60f, 110f).x, p(60f, 110f).y, p(80f, 110f).x, p(80f, 110f).y, p(80f, 110f).x, p(80f, 110f).y)
        cubicTo(p(100f, 110f).x, p(100f, 110f).y, p(112f, 90f).x, p(112f, 90f).y, p(112f, 90f).x, p(112f, 90f).y)
    }
    drawPath(botArc, BadgeRift.copy(alpha = 0.85f), style = stroke(BadgeRift, 2f))
    // 闪电
    val bolt = Path().apply {
        moveTo(p(86f, 38f).x, p(86f, 38f).y)
        lineTo(p(70f, 76f).x, p(70f, 76f).y)
        lineTo(p(92f, 76f).x, p(92f, 76f).y)
        lineTo(p(66f, 122f).x, p(66f, 122f).y)
    }
    drawPath(bolt, BadgeCaution, style = stroke(BadgeCaution, 4f))
    // 两侧火花
    drawLine(BadgeCream.copy(alpha = 0.7f), p(32f, 80f), p(44f, 80f), 1.4f * scale, StrokeCap.Round)
    drawLine(BadgeCream.copy(alpha = 0.7f), p(116f, 80f), p(128f, 80f), 1.4f * scale, StrokeCap.Round)
}

// 12 实验室之王：双形翼 + 大光环 + 五尖冠 + 闪电 + 蛇
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabKing(
    p: (Float, Float) -> Offset, scale: Float, stroke: (Color, Float) -> Stroke
) {
    // 左圣翼
    drawWing(p, true)
    // 右恶魔翼
    val demonWing = Path().apply {
        moveTo(p(122f, 92f).x, p(122f, 92f).y)
        cubicTo(p(108f, 68f).x, p(108f, 68f).y, p(92f, 64f).x, p(92f, 64f).y, p(84f, 80f).x, p(84f, 80f).y)
        cubicTo(p(100f, 82f).x, p(100f, 82f).y, p(110f, 92f).x, p(110f, 92f).y, p(116f, 104f).x, p(116f, 104f).y)
        cubicTo(p(120f, 102f).x, p(120f, 102f).y, p(124f, 98f).x, p(124f, 98f).y, p(122f, 92f).x, p(122f, 92f).y)
        close()
    }
    drawPath(demonWing, BadgeDemon.copy(alpha = 0.9f))
    // 大光环
    drawOval(
        BadgeGold,
        topLeft = p(40f, 28f),
        size = androidx.compose.ui.geometry.Size(80f * scale, 20f * scale),
        style = stroke(BadgeGold, 3.6f)
    )
    drawOval(
        BadgeCream.copy(alpha = 0.55f),
        topLeft = p(48f, 32f),
        size = androidx.compose.ui.geometry.Size(64f * scale, 12f * scale),
        style = stroke(BadgeCream, 1.2f)
    )
    // 五尖冠
    val crown = Path().apply {
        moveTo(p(46f, 110f).x, p(46f, 110f).y)
        lineTo(p(54f, 70f).x, p(54f, 70f).y)
        lineTo(p(66f, 90f).x, p(66f, 90f).y)
        lineTo(p(72f, 60f).x, p(72f, 60f).y)
        lineTo(p(80f, 36f).x, p(80f, 36f).y)
        lineTo(p(88f, 60f).x, p(88f, 60f).y)
        lineTo(p(94f, 90f).x, p(94f, 90f).y)
        lineTo(p(106f, 70f).x, p(106f, 70f).y)
        lineTo(p(114f, 110f).x, p(114f, 110f).y)
        close()
    }
    drawPath(crown, BadgeGold)
    drawPath(crown, BadgeGold, style = stroke(BadgeGold, 2f))
    // 冠顶宝石
    drawCircle(BadgeDemon, radius = 3.2f * scale, center = p(54f, 70f))
    drawCircle(BadgeCream, radius = 3f * scale, center = p(72f, 60f))
    drawCircle(BadgeGold, radius = 3.6f * scale, center = p(80f, 36f))
    drawCircle(BadgeRift, radius = 3f * scale, center = p(88f, 60f))
    drawCircle(BadgeDemon, radius = 3.2f * scale, center = p(106f, 70f))
    // 冠底带
    drawRect(BadgeGold, topLeft = p(46f, 106f), size = androidx.compose.ui.geometry.Size(68f * scale, 6f * scale))
    // 底部蛇
    val baseSnake = Path().apply {
        moveTo(p(46f, 124f).x, p(46f, 124f).y)
        cubicTo(p(60f, 116f).x, p(60f, 116f).y, p(100f, 116f).x, p(100f, 116f).y, p(114f, 124f).x, p(114f, 124f).y)
    }
    drawPath(baseSnake, BadgeDemon, style = stroke(BadgeDemon, 3.6f))
    drawCircle(BadgeDemon, radius = 2.4f * scale, center = p(46f, 124f))
    // APEX 金条
    drawRoundRect(
        BadgeGold,
        topLeft = p(56f, 134f),
        size = androidx.compose.ui.geometry.Size(48f * scale, 8f * scale),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * scale)
    )
}
