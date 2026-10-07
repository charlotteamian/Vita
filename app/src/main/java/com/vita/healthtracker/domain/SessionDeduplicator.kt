package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.ExerciseSession
import com.vita.healthtracker.data.local.entity.SleepSession
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SessionMergeResult<T>(
    val sessions: List<T>,
    val duplicateIds: Set<String>,
)

object SessionDeduplicator {
    private const val SleepToleranceMs = 30L * 60L * 1000L
    private const val ExerciseToleranceMs = 2L * 60L * 1000L
    private const val ExerciseDurationToleranceMinutes = 3L

    fun dedupeSleepSessions(sessions: List<SleepSession>): List<SleepSession> =
        mergeSleepSessions(sessions).sessions

    fun mergeSleepSessions(sessions: List<SleepSession>): SessionMergeResult<SleepSession> {
        if (sessions.size <= 1) return SessionMergeResult(sessions, emptySet())
        val clusters = clusterBy(sessions.sortedBy { it.startEpochMs }, ::isSameSleep)
        val merged = clusters.map(::mergeSleepCluster).sortedBy { it.startEpochMs }
        val keptIds = merged.map { it.id }.toSet()
        return SessionMergeResult(
            sessions = merged,
            duplicateIds = sessions.map { it.id }.toSet() - keptIds,
        )
    }

    fun dedupeExerciseSessions(sessions: List<ExerciseSession>): List<ExerciseSession> =
        mergeExerciseSessions(sessions).sessions

    fun mergeExerciseSessions(sessions: List<ExerciseSession>): SessionMergeResult<ExerciseSession> {
        if (sessions.size <= 1) return SessionMergeResult(sessions, emptySet())
        val clusters = clusterBy(sessions.sortedBy { it.startEpochMs }, ::isSameExercise)
        val merged = clusters.map(::mergeExerciseCluster).sortedByDescending { it.startEpochMs }
        val keptIds = merged.map { it.id }.toSet()
        return SessionMergeResult(
            sessions = merged,
            duplicateIds = sessions.map { it.id }.toSet() - keptIds,
        )
    }

    private fun <T> clusterBy(items: List<T>, sameSession: (T, T) -> Boolean): List<List<T>> {
        val clusters = mutableListOf<MutableList<T>>()
        items.forEach { item ->
            val cluster = clusters.firstOrNull { existing -> existing.any { sameSession(it, item) } }
            if (cluster == null) clusters.add(mutableListOf(item)) else cluster.add(item)
        }
        return clusters
    }

    /** 两个时间窗是否是同一觉 (强重叠或首尾都在容差内); 手动修正记录挡外部重导入时复用同一判定。 */
    fun isSameSleepWindow(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean =
        hasStrongOverlap(aStart, aEnd, bStart, bEnd) ||
            (abs(aStart - bStart) <= SleepToleranceMs && abs(aEnd - bEnd) <= SleepToleranceMs)

    private fun isSameSleep(a: SleepSession, b: SleepSession): Boolean =
        isSameSleepWindow(a.startEpochMs, a.endEpochMs, b.startEpochMs, b.endEpochMs)

    private fun isSameExercise(a: ExerciseSession, b: ExerciseSession): Boolean =
        hasStrongOverlap(a.startEpochMs, a.endEpochMs, b.startEpochMs, b.endEpochMs) ||
            (abs(a.startEpochMs - b.startEpochMs) <= ExerciseToleranceMs &&
                abs(a.endEpochMs - b.endEpochMs) <= ExerciseToleranceMs) ||
            (abs(a.startEpochMs - b.startEpochMs) <= ExerciseToleranceMs &&
                abs(a.durationMinutes - b.durationMinutes) <= ExerciseDurationToleranceMinutes)

    private fun hasStrongOverlap(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean {
        val aDuration = (aEnd - aStart).coerceAtLeast(1L)
        val bDuration = (bEnd - bStart).coerceAtLeast(1L)
        val overlap = min(aEnd, bEnd) - max(aStart, bStart)
        if (overlap <= 0L) return false
        return overlap.toDouble() / min(aDuration, bDuration).toDouble() >= 0.8
    }

    private fun mergeSleepCluster(cluster: List<SleepSession>): SleepSession {
        val preferred = cluster.maxBy { sleepInfoScore(it) }
        return preferred.copy(
            totalMinutes = cluster.maxOf { it.totalMinutes },
            deepMinutes = cluster.maxOf { it.deepMinutes },
            lightMinutes = cluster.maxOf { it.lightMinutes },
            remMinutes = cluster.maxOf { it.remMinutes },
            awakeMinutes = cluster.maxOf { it.awakeMinutes },
            sleepScore = cluster.mapNotNull { it.sleepScore?.takeIf { score -> score > 0 } }.maxOrNull(),
            source = mergeSources(cluster.map { it.source }),
        )
    }

    private fun mergeExerciseCluster(cluster: List<ExerciseSession>): ExerciseSession {
        val visible = cluster.filterNot { it.isDeleted }.takeIf { it.isNotEmpty() } ?: cluster
        val preferred = visible.maxBy { exerciseInfoScore(it) }
        val byScore = cluster.sortedByDescending { exerciseInfoScore(it) }

        fun firstText(value: (ExerciseSession) -> String?): String? =
            byScore.firstNotNullOfOrNull { value(it)?.trim()?.takeIf(String::isNotBlank) }

        fun bestLong(value: (ExerciseSession) -> Long?): Long? =
            cluster.mapNotNull { value(it)?.takeIf { number -> number > 0L } }.maxOrNull()

        fun bestDouble(value: (ExerciseSession) -> Double?): Double? =
            cluster.mapNotNull { value(it)?.takeIf { number -> number > 0.0 } }.maxOrNull()

        fun bestInt(value: (ExerciseSession) -> Int?): Int? =
            cluster.mapNotNull { value(it)?.takeIf { number -> number > 0 } }.maxOrNull()

        return preferred.copy(
            name = firstText { it.name },
            durationMinutes = cluster.maxOf { it.durationMinutes },
            elapsedMinutes = bestLong { it.elapsedMinutes },
            movingMinutes = bestLong { it.movingMinutes },
            distanceMeters = bestDouble { it.distanceMeters },
            activeCalories = bestDouble { it.activeCalories },
            bmrCalories = bestDouble { it.bmrCalories },
            totalCalories = bestDouble { it.totalCalories },
            avgHeartRate = bestInt { it.avgHeartRate },
            maxHeartRate = bestInt { it.maxHeartRate },
            steps = bestLong { it.steps },
            avgCadence = bestDouble { it.avgCadence },
            maxCadence = bestDouble { it.maxCadence },
            avgSpeed = bestDouble { it.avgSpeed },
            maxSpeed = bestDouble { it.maxSpeed },
            elevationGainMeters = bestDouble { it.elevationGainMeters },
            elevationLossMeters = bestDouble { it.elevationLossMeters },
            minElevationMeters = bestDouble { it.minElevationMeters },
            maxElevationMeters = bestDouble { it.maxElevationMeters },
            trainingEffect = bestDouble { it.trainingEffect },
            anaerobicTrainingEffect = bestDouble { it.anaerobicTrainingEffect },
            avgStrideLengthMeters = bestDouble { it.avgStrideLengthMeters },
            avgPowerWatts = bestDouble { it.avgPowerWatts },
            maxPowerWatts = bestDouble { it.maxPowerWatts },
            sweatLossMl = bestDouble { it.sweatLossMl },
            customTitle = firstText { it.customTitle },
            customCategory = firstText { it.customCategory },
            note = firstText { it.note },
            isDeleted = visible.all { it.isDeleted },
            source = mergeSources(cluster.map { it.source }),
        )
    }

    private fun sleepInfoScore(sleep: SleepSession): Int =
        sourcePriority(sleep.source) * 1_000 +
            listOf(sleep.deepMinutes, sleep.lightMinutes, sleep.remMinutes, sleep.awakeMinutes)
                .count { it > 0L } * 50 +
            if (sleep.sleepScore != null && sleep.sleepScore > 0) 250 else 0

    private fun exerciseInfoScore(exercise: ExerciseSession): Int =
        sourcePriority(exercise.source) * 1_000 +
            listOfNotNull(
                exercise.distanceMeters?.takeIf { it > 0.0 },
                exercise.activeCalories?.takeIf { it > 0.0 },
                exercise.totalCalories?.takeIf { it > 0.0 },
                exercise.avgHeartRate?.takeIf { it > 0 },
                exercise.maxHeartRate?.takeIf { it > 0 },
                exercise.steps?.takeIf { it > 0L },
                exercise.avgSpeed?.takeIf { it > 0.0 },
                exercise.trainingEffect?.takeIf { it > 0.0 },
            ).size * 80 +
            if (!exercise.name.isNullOrBlank()) 40 else 0

    private fun sourcePriority(source: String): Int {
        val lower = source.lowercase()
        return when {
            "garmin_api" in lower -> 6
            "garmin" in lower -> 5
            "samsung" in lower || "shealth" in lower -> 4
            "xiaomi" in lower || "mi.health" in lower || "huami" in lower -> 4
            "fitbit" in lower || "apple" in lower -> 4
            "health_connect" in lower || "healthconnect" in lower -> 2
            "mock" in lower -> 0
            else -> 3
        }
    }

    private fun mergeSources(sources: List<String>): String =
        sources.flatMap { it.split("+") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("+")
}
