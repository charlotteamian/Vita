package com.vita.healthtracker.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vita.healthtracker.VitaApplication
import com.vita.healthtracker.data.repository.HealthRepository
import java.time.LocalDate

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as VitaApplication).container
        return try {
            val outcome = container.healthRepository.syncFromHealthConnect()
            if (outcome is HealthRepository.SyncOutcome.Synced) {
                runCatching { container.cycleRepository.syncFromHealthConnect() }
            }
            if (container.garminAuthClient.loggedDomains().isNotEmpty()) {
                val toDate = LocalDate.now()
                val fromDate = toDate.minusDays(7)
                container.garminAuthClient.loggedDomains().forEach { domain ->
                    runCatching {
                        container.garminAuthClient.activateDomain(domain)
                        container.garminSyncManager.syncAll(fromDate, toDate)
                    }
                }
                container.healthRepository.markSyncedAt()
            }
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "vita_periodic_sync"
    }
}
