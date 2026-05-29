package com.vita.healthtracker.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// 深色基调 (和 Fortuna 保持一致)
val VitaBackground = Color(0xFF06080F)
val VitaSurface = Color(0xFF0E1320)
val VitaSurfaceVariant = Color(0xFF161C2E)
val VitaOutline = Color(0xFF2A3149)

val VitaPrimary = Color(0xFF7CC4FF)         // 步数 / 主操作
val VitaSecondary = Color(0xFFF7A8C0)        // 月经周期
val VitaTertiary = Color(0xFFB8E0B8)         // 睡眠
val VitaError = Color(0xFFFF6B6B)
val VitaHeart = Color(0xFFFF7CA3)            // 心率
val VitaActive = Color(0xFFFFC678)           // 卡路里 / 活跃

val VitaOnBackground = Color(0xFFE7EAF3)
val VitaOnSurfaceMuted = Color(0xFF8A94B0)

// ─── 渐变 Brush ────────────────────────────────────────────────

/** 卡片边缘微光渐变 */
object VitaGradients {

    /** 通用卡片: surface → surfaceVariant 对角渐变 */
    val cardSurface: Brush = Brush.linearGradient(
        colors = listOf(
            VitaSurface,
            VitaSurfaceVariant.copy(alpha = 0.7f),
        ),
    )

    /** 强调色卡片背景 (用于预测卡、同步按钮等) */
    val primaryAccent: Brush = Brush.linearGradient(
        colors = listOf(
            VitaPrimary.copy(alpha = 0.15f),
            VitaSurface,
        ),
    )

    val heartAccent: Brush = Brush.linearGradient(
        colors = listOf(
            VitaHeart.copy(alpha = 0.12f),
            VitaSurface,
        ),
    )

    val sleepAccent: Brush = Brush.linearGradient(
        colors = listOf(
            VitaTertiary.copy(alpha = 0.10f),
            VitaSurface,
        ),
    )

    val cycleAccent: Brush = Brush.linearGradient(
        colors = listOf(
            VitaSecondary.copy(alpha = 0.15f),
            VitaPrimary.copy(alpha = 0.05f),
            VitaSurface,
        ),
    )

    /** 同步按钮渐变 */
    val syncButton: Brush = Brush.horizontalGradient(
        colors = listOf(
            VitaPrimary,
            VitaPrimary.copy(alpha = 0.8f),
            Color(0xFF5EAAFF),
        ),
    )

    /** icon 光晕 — 以 accent 色径向渐变到透明 */
    fun iconGlow(accent: Color): Brush = Brush.radialGradient(
        colors = listOf(
            accent.copy(alpha = 0.20f),
            accent.copy(alpha = 0.05f),
            Color.Transparent,
        ),
    )
}
