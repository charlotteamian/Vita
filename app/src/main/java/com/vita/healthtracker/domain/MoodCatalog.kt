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
 * @param isCustom 是否用户自定义 (选择器里长按可修改/删除)。
 */
data class Mood(
    val id: String,
    val label: String,
    val colorHex: Long,
    val shape: MoodShape,
    val isCustom: Boolean = false,
)

/** 一条自定义情绪在目录里的注册信息 (由 MoodRepository 从 custom_mood 表灌入)。 */
data class CustomMoodRegistration(
    val mood: Mood,
    val valence: Int,
    val isArchived: Boolean,
)

/**
 * 情绪目录: 内置 8 种 + 用户自定义。
 * 自定义部分是运行时注册表, 由 MoodRepository 在 app scope 里观察 custom_mood 表持续灌入,
 * 这样 [valence] / [POSITIVE_MOODS] / [NEGATIVE_MOODS] 等分析入口对自定义情绪同样生效,
 * 不必把自定义列表穿透进每个分析器。UI 的选择器请用 ViewModel 状态里的列表 (可随库变化重组)。
 */
object MoodCatalog {
    /** 内置 8 种情绪, 顺序即选择器展示顺序。 */
    val builtIn: List<Mood> = listOf(
        Mood("joy", "愉悦", 0xFFFFD166, MoodShape.HEXAGON),
        Mood("calm", "平静", 0xFF74C0FC, MoodShape.CIRCLE),
        Mood("content", "满足", 0xFF8CE99A, MoodShape.DROPLET),
        Mood("neutral", "平常", 0xFF9AA4BF, MoodShape.CIRCLE),
        Mood("tired", "疲惫", 0xFF5C8DD6, MoodShape.DIAMOND),
        Mood("stressed", "压力", 0xFFB197FC, MoodShape.PENTAGON),
        Mood("down", "低落", 0xFF7A82C9, MoodShape.CHEVRON_DOWN),
        Mood("irritated", "烦躁", 0xFFFF6B6B, MoodShape.STAR),
    )

    private val builtInById: Map<String, Mood> = builtIn.associateBy { it.id }

    @Volatile
    private var customById: Map<String, CustomMoodRegistration> = emptyMap()

    /** 内置 + 未归档的自定义 (归档的仍可 byId 解析, 只是不再出现在这里)。 */
    val moods: List<Mood>
        get() = builtIn + customById.values.filter { !it.isArchived }.map { it.mood }

    /** 由 MoodRepository 每次 custom_mood 表变化时整体替换。 */
    fun setCustomMoods(registrations: List<CustomMoodRegistration>) {
        customById = registrations.associateBy { it.mood.id }
    }

    fun byId(id: String?): Mood? = id?.let { builtInById[it] ?: customById[it]?.mood }

    /**
     * 情绪「价」(1..5, 越高越正向), 聚合与本地关联分析共用的单一来源。
     * 自定义情绪的价由用户创建时选择的倾向决定。
     */
    fun valence(id: String?): Int? = when (id) {
        "joy" -> 5
        "content", "calm" -> 4
        "neutral" -> 3
        "tired", "stressed" -> 2
        "down", "irritated" -> 1
        else -> id?.let { customById[it]?.valence?.coerceIn(1, 5) }
    }

    private val BUILT_IN_POSITIVE: Set<String> = setOf("joy", "calm", "content")
    private val BUILT_IN_NEGATIVE: Set<String> = setOf("tired", "stressed", "down", "irritated")

    /** 正/负向集合按「价」派生, 自动覆盖自定义情绪 (价≥4 正向, ≤2 负向)。 */
    val POSITIVE_MOODS: Set<String>
        get() = BUILT_IN_POSITIVE + customById.values.filter { it.valence >= 4 }.map { it.mood.id }
    val NEGATIVE_MOODS: Set<String>
        get() = BUILT_IN_NEGATIVE + customById.values.filter { it.valence <= 2 }.map { it.mood.id }
}
