package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.ExerciseSession
import java.time.Instant
import java.time.ZoneId

object ExerciseClassifier {
    private const val IntenseFitness = "intense_fitness"
    private const val GentleWalking = "gentle_walking"
    private const val Running = "running"
    private const val Cycling = "cycling"
    private const val Strength = "strength"
    private const val Swimming = "swimming"
    private const val Yoga = "yoga"
    private const val Other = "other"

    data class CategoryOption(val key: String, val label: String)

    val editableCategories = listOf(
        CategoryOption(GentleWalking, "步行"),
        CategoryOption(IntenseFitness, "强度健身"),
        CategoryOption(Running, "跑步"),
        CategoryOption(Cycling, "骑行"),
        CategoryOption(Strength, "力量训练"),
        CategoryOption(Swimming, "游泳"),
        CategoryOption(Yoga, "瑜伽/拉伸"),
        CategoryOption(Other, "其他"),
    )

    fun displayCategory(exercise: ExerciseSession): String {
        exercise.customCategory?.takeIf { it.isNotBlank() }?.let { return canonicalCategory(it, exercise.name.orEmpty()) }
        val key = exercise.type.lowercase()
        val name = exercise.name.orEmpty().lowercase()
        val caloriesPerMinute = (exercise.activeCalories ?: exercise.totalCalories ?: 0.0) /
            exercise.durationMinutes.coerceAtLeast(1).toDouble()
        val avgHr = exercise.avgHeartRate ?: 0
        val isWalking = key.contains("walking") || key == "52" || name.contains("walk") || name.contains("步行")
        val isIndoorCardio = key.contains("cardio") ||
            key.contains("fitness") ||
            key.contains("treadmill") ||
            key.contains("elliptical") ||
            key.contains("floor_climbing") ||
            name.contains("cardio") ||
            name.contains("treadmill")

        return when {
            isIndoorCardio -> IntenseFitness
            isWalking && (avgHr >= 108 || caloriesPerMinute >= 4.5 || (exercise.elevationGainMeters ?: 0.0) >= 15.0) -> IntenseFitness
            isWalking -> GentleWalking
            else -> canonicalCategory(key, name)
        }
    }

    fun displayName(category: String): String = when (category) {
        IntenseFitness -> "强度健身"
        GentleWalking -> "步行"
        Running -> "跑步"
        Cycling -> "骑行"
        Strength -> "力量训练"
        Swimming -> "游泳"
        Yoga -> "瑜伽/拉伸"
        Other -> "其他"
        else -> ExerciseTypeNames.nameOf(category)
    }

    fun displayTitle(exercise: ExerciseSession): String =
        exercise.customTitle
            ?.takeIf { it.isNotBlank() }
            ?: exercise.name?.takeIf { it.isNotBlank() }
            ?: displayName(displayCategory(exercise))

    fun shouldShowInStats(exercise: ExerciseSession, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val category = displayCategory(exercise)
        val hour = Instant.ofEpochMilli(exercise.startEpochMs).atZone(zone).hour
        val calories = exercise.activeCalories ?: exercise.totalCalories ?: 0.0
        val avgHr = exercise.avgHeartRate ?: 0
        val suspiciousNightAutoWalk = category == GentleWalking &&
            hour in 0..5 &&
            exercise.durationMinutes <= 12L &&
            calories <= 25.0 &&
            avgHr < 100
        return !suspiciousNightAutoWalk
    }

    private fun canonicalCategory(key: String, name: String): String {
        val k = key.lowercase()
        val n = name.lowercase()
        return when {
            k == IntenseFitness || k.contains("cardio") || k.contains("fitness") ||
                k.contains("hiit") || k.contains("elliptical") || k.contains("floor_climbing") ||
                n.contains("cardio") || n.contains("有氧") -> IntenseFitness
            k == GentleWalking || k.contains("walking") || k == "52" || n.contains("步行") || n.contains("walk") -> GentleWalking
            k == Running || k.contains("running") || k == "37" || k == "38" || n.contains("跑") -> Running
            k == Cycling || k.contains("cycling") || k.contains("biking") || k == "8" || n.contains("骑") -> Cycling
            k == Strength || k.contains("strength") || k.contains("weight") || k == "54" || k == "75" || n.contains("力量") -> Strength
            k == Swimming || k.contains("swimming") || k == "46" || k == "47" || n.contains("游泳") -> Swimming
            k == Yoga || k.contains("yoga") || k.contains("pilates") || k.contains("stretch") ||
                k == "56" || k == "76" || n.contains("瑜伽") || n.contains("拉伸") -> Yoga
            k == Other -> Other
            else -> key
        }
    }
}
