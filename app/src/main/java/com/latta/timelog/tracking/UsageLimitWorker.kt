package com.latta.timelog.tracking

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.latta.timelog.alerts.UsageAlertManager
import com.latta.timelog.data.UsageRepository
import kotlinx.coroutines.runBlocking

class UsageLimitWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val repo = UsageRepository(applicationContext)
        if (!repo.hasUsageAccess()) return Result.success()
        return try {
            runBlocking { repo.persistTodaySnapshot() }
            checkConfiguredAlert(repo)
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun checkConfiguredAlert(repo: UsageRepository): Result {
        val prefs = applicationContext.getSharedPreferences("timelog_alerts", Context.MODE_PRIVATE)
        val rules = try {
            org.json.JSONObject(prefs.getString("rules_json", "{}").orEmpty())
        } catch (_: Exception) {
            org.json.JSONObject()
        }
        val usage = repo.todayUsage()
        val alerts = UsageAlertManager(applicationContext)
        val keys = rules.keys()
        while (keys.hasNext()) {
            val packageName = keys.next()
            val rule = rules.optJSONObject(packageName) ?: continue
            val threshold = rule.optInt("threshold", 0)
            val app = usage.firstOrNull { it.packageName == packageName } ?: continue
            if (alerts.shouldNotify(packageName, repo.dateKey(), app.launchCount, threshold)) {
                alerts.notifyLimitReached(
                    app.appName,
                    rule.optString("message"),
                    app.launchCount,
                    threshold
                )
            }
        }
        return Result.success()
    }
}
