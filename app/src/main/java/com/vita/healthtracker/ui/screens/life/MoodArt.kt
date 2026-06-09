package com.vita.healthtracker.ui.screens.life

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vita.healthtracker.domain.Mood
import com.vita.healthtracker.domain.MoodShape
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 发光的 3D 情绪体: 外层柔光晕 + 主色填充造型 + 顶部高光, 营造立体球感。
 * [filled] = false 时画灰色暗调 (未记录的过去日)。
 */
@Composable
fun MoodGlyph(
    mood: Mood?,
    size: Dp,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val baseColor = if (mood != null && filled) Color(mood.colorHex) else Color(0xFF3A4257)
    val shape = mood?.shape ?: MoodShape.CIRCLE
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawMoodShape(shape, baseColor, glow = mood != null && filled)
        }
    }
}

private fun DrawScope.drawMoodShape(shape: MoodShape, color: Color, glow: Boolean) {
    val side = min(size.width, size.height)
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = side / 2f * 0.66f

    // —— 外层柔光晕 ——
    if (glow) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.45f), Color.Transparent),
                center = Offset(cx, cy),
                radius = side / 2f,
            ),
            radius = side / 2f,
            center = Offset(cx, cy),
        )
    }

    val path = moodPath(shape, cx, cy, r)

    // —— 主体填充 (上亮下暗的竖直渐变, 模拟受光) ——
    val top = if (glow) color.lighten(0.30f) else color.lighten(0.10f)
    val bottom = if (glow) color.darken(0.30f) else color.darken(0.20f)
    drawPath(
        path = path,
        brush = Brush.verticalGradient(
            colors = listOf(top, color, bottom),
            startY = cy - r,
            endY = cy + r,
        ),
        style = Fill,
    )

    // —— 顶部高光球点 ——
    if (glow) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(cx - r * 0.28f, cy - r * 0.34f),
                radius = r * 0.55f,
            ),
            radius = r * 0.55f,
            center = Offset(cx - r * 0.28f, cy - r * 0.34f),
        )
    }

    // —— 轮廓描边 ——
    drawPath(
        path = path,
        color = (if (glow) color.lighten(0.4f) else color).copy(alpha = 0.6f),
        style = Stroke(width = side * 0.02f),
    )
}

private fun moodPath(shape: MoodShape, cx: Float, cy: Float, r: Float): Path = when (shape) {
    MoodShape.CIRCLE -> Path().apply { addOvalCircle(cx, cy, r) }
    MoodShape.HEXAGON -> polygon(cx, cy, r, sides = 6, rotationDeg = -90f)
    MoodShape.PENTAGON -> polygon(cx, cy, r, sides = 5, rotationDeg = -90f)
    MoodShape.DIAMOND -> Path().apply {
        moveTo(cx, cy - r)
        lineTo(cx + r * 0.78f, cy)
        lineTo(cx, cy + r)
        lineTo(cx - r * 0.78f, cy)
        close()
    }
    MoodShape.DROPLET -> Path().apply {
        // 上尖下圆的水滴
        moveTo(cx, cy - r)
        cubicTo(cx + r * 0.92f, cy - r * 0.15f, cx + r * 0.78f, cy + r * 0.85f, cx, cy + r * 0.85f)
        cubicTo(cx - r * 0.78f, cy + r * 0.85f, cx - r * 0.92f, cy - r * 0.15f, cx, cy - r)
        close()
    }
    MoodShape.CHEVRON_DOWN -> Path().apply {
        val w = r * 0.95f
        val t = r * 0.42f
        moveTo(cx - w, cy - r * 0.45f)
        lineTo(cx, cy + r * 0.25f)
        lineTo(cx + w, cy - r * 0.45f)
        lineTo(cx + w, cy - r * 0.45f + t)
        lineTo(cx, cy + r * 0.25f + t)
        lineTo(cx - w, cy - r * 0.45f + t)
        close()
    }
    MoodShape.STAR -> starPath(cx, cy, outer = r, inner = r * 0.45f, points = 5, rotationDeg = -90f)
}

private fun Path.addOvalCircle(cx: Float, cy: Float, r: Float) {
    addArc(
        androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
        0f,
        360f,
    )
}

private fun polygon(cx: Float, cy: Float, r: Float, sides: Int, rotationDeg: Float): Path = Path().apply {
    for (i in 0 until sides) {
        val a = Math.toRadians((rotationDeg + i * 360f / sides).toDouble())
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float, points: Int, rotationDeg: Float): Path = Path().apply {
    val total = points * 2
    for (i in 0 until total) {
        val rad = if (i % 2 == 0) outer else inner
        val a = Math.toRadians((rotationDeg + i * 360f / total).toDouble())
        val x = cx + rad * cos(a).toFloat()
        val y = cy + rad * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun Color.lighten(f: Float): Color = Color(
    red = (red + (1f - red) * f).coerceIn(0f, 1f),
    green = (green + (1f - green) * f).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * f).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun Color.darken(f: Float): Color = Color(
    red = (red * (1f - f)).coerceIn(0f, 1f),
    green = (green * (1f - f)).coerceIn(0f, 1f),
    blue = (blue * (1f - f)).coerceIn(0f, 1f),
    alpha = alpha,
)
