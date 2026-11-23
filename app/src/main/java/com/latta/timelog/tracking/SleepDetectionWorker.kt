package com.latta.timelog.tracking

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking

class SleepDetectionWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val repo = com.latta.timelog.data.UsageRepository(applicationContext)
        if (!repo.hasUsageAccess()) return Result.success()
        return try {
            runBlocking { SleepDetector(applicationContext).refreshLast24Hours() }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
