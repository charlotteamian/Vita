package com.vita.healthtracker.domain

/** 情绪对应的几何造型, 由 MoodArt 绘制成发光 3D 体。 */
enum class MoodShape {
    HEXAGON,
    CIRCLE,
    DROPLET,
    DIAMOND,
    PENTAGON,
    CHEVRON_DOWN,
    STAR,
}

/**
 * 一种情绪。
 * @param id      持久化用的稳定标识 (写进 mood_entry.moodId)。
 * @param label   中文名。
 * @param colorHex 发光主色 (ARGB Long)。
 * @param shape   绘制造型。
 */
data class Mood(
    val id: String,
    val label: String,
    val colorHex: Long,
    val shape: MoodShape,
)

/** 8 种情绪, 顺序即选择器展示顺序。 */
object MoodCatalog {
    val moods: List<Mood> = listOf(
        Mood("joy", "愉悦", 0xFFFFD166, MoodShape.HEXAGON),
        Mood("calm", "平静", 0xFF74C0FC, MoodShape.CIRCLE),
        Mood("content", "满足", 0xFF8CE99A, MoodShape.DROPLET),
        Mood("neutral", "平常", 0xFF9AA4BF, MoodShape.CIRCLE),
        Mood("tired", "疲惫", 0xFF5C8DD6, MoodShape.DIAMOND),
        Mood("stressed", "压力", 0xFFB197FC, MoodShape.PENTAGON),
        Mood("down", "低落", 0xFF7A82C9, MoodShape.CHEVRON_DOWN),
        Mood("irritated", "烦躁", 0xFFFF6B6B, MoodShape.STAR),
    )

    private val byId: Map<String, Mood> = moods.associateBy { it.id }

    fun byId(id: String?): Mood? = id?.let { byId[it] }
}
