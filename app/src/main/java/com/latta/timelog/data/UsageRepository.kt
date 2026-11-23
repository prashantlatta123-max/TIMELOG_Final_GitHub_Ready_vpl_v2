package com.latta.timelog.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext

class UsageRepository(private val context: Context) {
    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun todayUsage(): List<AppUsage> {
        val now = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return usageBetween(start, now)
    }

    fun usageBetween(start: Long, end: Long): List<AppUsage> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        val launches = mutableMapOf<String, Int>()
        val first = mutableMapOf<String, Long>()
        val last = mutableMapOf<String, Long>()
        var foregroundPackage: String? = null
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (isForegroundEvent(event.eventType)) {
                if (pkg != context.packageName && pkg != foregroundPackage) {
                    launches[pkg] = (launches[pkg] ?: 0) + 1
                    if (!first.containsKey(pkg)) first[pkg] = event.timeStamp
                    last[pkg] = event.timeStamp
                }
                foregroundPackage = pkg
            } else if (isBackgroundEvent(event.eventType) && foregroundPackage == pkg) {
                foregroundPackage = null
            }
        }
        val pm = context.packageManager
        return stats.orEmpty()
            .filter { it.totalTimeInForeground > 0L && it.packageName != context.packageName }
            .map { stat ->
                val label = try {
                    val info = pm.getApplicationInfo(stat.packageName, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    stat.packageName
                }
                AppUsage(
                    packageName = stat.packageName,
                    appName = label,
                    usageMillis = stat.totalTimeInForeground,
                    launchCount = launches[stat.packageName] ?: 0,
                    lastUsed = last[stat.packageName] ?: stat.lastTimeUsed,
                    firstOpenedAt = first[stat.packageName] ?: stat.firstTimeStamp
                )
            }.sortedByDescending { it.usageMillis }
    }

    /**
     * Reconstructs foreground sessions from ACTIVITY_RESUMED/PAUSED events and
     * splits each session at real wall-clock hour boundaries. This avoids
     * fabricating equal usage across 24 buckets.
     */
    fun hourlyAppUsageForLast24Hours(): List<HourlyAppUsage> {
        val end = System.currentTimeMillis()
        val alignedEnd = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val start = alignedEnd - 23L * 60L * 60L * 1000L
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        val active = mutableMapOf<String, Long>()
        val totals = mutableMapOf<Pair<Long, String>, Long>()

        fun addSession(packageName: String, rawStart: Long, rawEnd: Long) {
            var cursor = maxOf(rawStart, start)
            val finish = minOf(rawEnd, end)
            while (cursor < finish) {
                val hourStart = start + ((cursor - start) / 3_600_000L) * 3_600_000L
                val boundary = minOf(hourStart + 3_600_000L, finish)
                val key = hourStart to packageName
                totals[key] = (totals[key] ?: 0L) + (boundary - cursor).coerceAtLeast(0L)
                cursor = boundary
            }
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg == context.packageName) continue
            when {
                isForegroundEvent(event.eventType) -> active[pkg] = event.timeStamp
                isBackgroundEvent(event.eventType) -> {
                    val began = active.remove(pkg) ?: continue
                    addSession(pkg, began, event.timeStamp)
                }
            }
        }
        active.forEach { (pkg, began) -> addSession(pkg, began, end) }

        val pm = context.packageManager
        val labels = mutableMapOf<String, String>()
        return totals.mapNotNull { (key, millis) ->
            if (millis <= 0L) return@mapNotNull null
            val pkg = key.second
            val label = labels.getOrPut(pkg) {
                try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: PackageManager.NameNotFoundException) { pkg }
            }
            HourlyAppUsage(key.first, pkg, label, millis)
        }.sortedWith(compareBy<HourlyAppUsage> { it.hourStart }.thenByDescending { it.usageMillis })
    }

    fun hourlyUsageForLast24Hours(): List<HourUsage> {
        val alignedEnd = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val start = alignedEnd - 23L * 60L * 60L * 1000L
        val totals = LongArray(24)
        hourlyAppUsageForLast24Hours().forEach { item ->
            val index = ((item.hourStart - start) / 3_600_000L).toInt().coerceIn(0, 23)
            totals[index] += item.usageMillis
        }
        return totals.mapIndexed { index, millis -> HourUsage(index, millis) }
    }

    private fun isForegroundEvent(type: Int): Boolean =
        type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            type == UsageEvents.Event.ACTIVITY_RESUMED

    private fun isBackgroundEvent(type: Int): Boolean =
        type == UsageEvents.Event.MOVE_TO_BACKGROUND ||
            type == UsageEvents.Event.ACTIVITY_PAUSED


    suspend fun persistTodaySnapshot() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val rows = todayUsage().map { app ->
            DailyAppUsageEntity(
                dateKey = dateKey(now),
                packageName = app.packageName,
                appName = app.appName,
                usageMillis = app.usageMillis,
                launchCount = app.launchCount,
                firstOpenedAt = app.firstOpenedAt,
                lastOpenedAt = app.lastUsed,
                updatedAt = now
            )
        }
        val dao = TimeLogDatabase.get(context).dao()
        if (rows.isNotEmpty()) dao.upsertDaily(rows)
        val hours = hourlyAppUsageForLast24Hours().map { item ->
            HourlyUsageEntity(
                hourStart = item.hourStart,
                packageName = item.packageName,
                appName = item.appName,
                usageMillis = item.usageMillis,
                launchCount = 0
            )
        }
        dao.upsertHourly(hours)
    }

    fun dateKey(timestamp: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
}
