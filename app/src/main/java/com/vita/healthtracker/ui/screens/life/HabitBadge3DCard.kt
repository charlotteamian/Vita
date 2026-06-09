package com.vita.healthtracker.ui.screens.life

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vita.healthtracker.domain.HabitBadge
import com.vita.healthtracker.ui.theme.VitaActive
import com.vita.healthtracker.ui.theme.VitaOnSurfaceMuted
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * 徽章鉴赏卡 (卡片本身静止):
 *  - 只有徽章图案本身做成"有厚度的金属币", 绕 Y 轴持续旋转;
 *  - 手指左右拖动可手动转动徽章 (自由旋转, 不回弹);
 *  - 厚度由一摞渐变暗的同形底盘错位叠出, 转到侧面时露出边缘。
 */
@Composable
fun HabitBadge3DCard(
    badge: HabitBadge,
    earned: Boolean,
    conditionText: String,
    modifier: Modifier = Modifier,
    ordinalText: String? = null,
    unlockDateText: String? = null,
) {
    val scope = rememberCoroutineScope()

    val infinite = rememberInfiniteTransition(label = "badge3d")
    val autoSpin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "autoSpin",
    )
    val sheen by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "sheen",
    )

    // 手动拖动累加的角度 (自由旋转, 不回弹)
    val dragAngle = remember { Animatable(0f) }
    val rotationDeg = if (earned) autoSpin + dragAngle.value else 0f

    // 轻点徽章 → 翻出背面寄语 (badge.description); 再点收起。
    var showBack by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1B2336),
                        Color(0xFF11172A),
                        Color(0xFF080B16),
                    ),
                ),
            )
            .drawWithContent {
                drawContent()
                val shift = (sheen - 0.5f) * size.width * 1.6f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.05f),
                            Color.Transparent,
                        ),
                        start = Offset(shift, 0f),
                        end = Offset(shift + size.width * 0.5f, size.height),
                    ),
                )
            }
            .padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (ordinalText != null) {
            Text(
                ordinalText,
                style = MaterialTheme.typography.labelLarge,
                color = VitaActive,
                fontWeight = FontWeight.Bold,
            )
        }

        Box(
            modifier = Modifier
                .size(180.dp)
                .pointerInput(Unit) {
                    detectTapGestures { showBack = !showBack }
                }
                .pointerInput(earned) {
                    if (!earned) return@pointerInput
                    detectDragGestures { change, drag ->
                        change.consume()
                        scope.launch { dragAngle.snapTo(dragAngle.value + drag.x * 0.6f) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Badge3DGlyph(
                badge = badge,
                earned = earned,
                glyphSize = 150.dp,
                rotationDeg = rotationDeg,
            )

            // 背面寄语: 轻点徽章翻出 badge.description, 盖在金属币之上。
            if (showBack) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xE6080B16))
                        .padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = badge.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Text(
            badge.name,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "${badge.systemName} · $conditionText",
            style = MaterialTheme.typography.bodyMedium,
            color = VitaActive,
            textAlign = TextAlign.Center,
        )
        Text(
            text = when {
                showBack -> "轻点收起寄语"
                earned && unlockDateText != null -> "解锁于 $unlockDateText · 拖动鉴赏 · 轻点看寄语"
                earned -> "拖动 360° 鉴赏 · 轻点看寄语"
                else -> "尚未解锁 · 轻点看寄语"
            },
            style = MaterialTheme.typography.labelSmall,
            color = VitaOnSurfaceMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 有厚度的"金属币"徽章 (只有徽章图案本身, 不含卡片底):
 *  - 一摞同形八角奖牌沿旋转法线错位叠放 → 转到侧面时露出金属边缘;
 *  - 正面复用 [HabitBadgeImage] (plate=false, 只画奖牌), 按 cos(角度) 透视压缩,
 *    过 90° 自然翻到背面 (镜像), 形成连续 360° 旋转。
 */
@Composable
private fun Badge3DGlyph(
    badge: HabitBadge,
    earned: Boolean,
    glyphSize: Dp,
    rotationDeg: Float,
    modifier: Modifier = Modifier,
) {
    val rad = Math.toRadians(rotationDeg.toDouble())
    val cosT = cos(rad).toFloat()
    val sinT = sin(rad).toFloat()
    val rim = habitBadgeRimColor(badge)

    Box(modifier = modifier.size(glyphSize), contentAlignment = Alignment.Center) {
        if (earned) {
            // —— 厚度: 一摞同形八角奖牌, 沿屏幕水平方向 (Y 轴旋转的法线投影) 错位 ——
            val layers = 14
            for (i in 0 until layers) {
                val frac = i / (layers - 1f) // 0 = 最里(背面), 1 = 最靠前(正面)
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            // 厚度更薄: 0.16 → 0.09
                            val depth = (glyphSize * 0.09f).toPx()
                            translationX = (frac - 0.5f) * depth * sinT
                            // 边缘随旋转角横向压扁, 正对时几乎不可见
                            scaleX = abs(cosT).coerceAtLeast(0.05f)
                        },
                ) {
                    // 中间层暗、靠光面亮, 模拟金属圆周受光
                    val lit = 0.22f + 0.62f * kotlin.math.abs(frac - 0.5f) * 2f
                    drawBadgeOctagon(rim.darkenBy(1f - lit.coerceIn(0.18f, 0.95f)))
                }
            }
        }

        // —— 朝向我们的那一面: cos≥0 显示正面奖牌, cos<0 翻到背面 ——
        // 用 |cos| 做透视压缩, 不再用负 scaleX 镜像正面, 这样能真正翻到背面。
        val facingFront = cosT >= 0f
        val faceScale = abs(cosT).coerceAtLeast(0.05f)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val depth = (glyphSize * 0.09f).toPx()
                    translationX = 0.5f * depth * sinT
                    scaleX = faceScale
                },
        ) {
            if (facingFront) {
                HabitBadgeImage(
                    badge = badge,
                    earned = earned,
                    iconSize = glyphSize,
                    plate = false,
                )
            } else {
                Badge3DBackFace(rim = rim, earned = earned)
            }
        }
    }
}

/** 金属币的背面: 八角底盘 + 内圈 + 中心冲压点, 与正面图案明显不同。 */
@Composable
private fun Badge3DBackFace(rim: Color, earned: Boolean) {
    val alpha = if (earned) 1f else 0.34f
    Canvas(modifier = Modifier.fillMaxSize().alpha(alpha)) {
        // 外层八角金属盘 (略暗于边缘色)
        drawBadgeOctagon(rim.darkenBy(0.35f))
        // 同心内八角凹槽
        scale(0.74f, pivot = center) {
            drawBadgeOctagon(rim.darkenBy(0.55f))
        }
        scale(0.5f, pivot = center) {
            drawBadgeOctagon(rim.darkenBy(0.18f))
        }
        // 中心冲压点
        drawCircle(
            color = rim.darkenBy(0.62f),
            radius = size.minDimension * 0.07f,
            center = center,
        )
    }
}

private fun Color.darkenBy(f: Float): Color = Color(
    red = (red * (1f - f)).coerceIn(0f, 1f),
    green = (green * (1f - f)).coerceIn(0f, 1f),
    blue = (blue * (1f - f)).coerceIn(0f, 1f),
    alpha = alpha,
)
