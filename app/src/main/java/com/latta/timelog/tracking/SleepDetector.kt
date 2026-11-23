package com.latta.timelog.tracking

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.latta.timelog.data.SleepRecordEntity
import com.latta.timelog.data.TimeLogDatabase

/**
 * Estimates a phone-inactivity sleep window using system usage events.
 * Notifications/network/background sync do not create KEYGUARD_HIDDEN events.
 * This is an estimate, not a physiological sleep measurement.
 */
class SleepDetector(private val context: Context) {
    suspend fun refreshLast24Hours(): SleepRecordEntity? {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - 24L * 60L * 60L * 1000L
        val events = manager.queryEvents(start, end)
        val event = UsageEvents.Event()
        var screenOffAt = 0L
        var candidateUnlock = 0L
        val foregroundStarts = mutableMapOf<String, Long>()
        var foregroundMillisAfterUnlock = 0L
        var result: SleepRecordEntity? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (candidateUnlock > 0L) {
                        // A new screen-off before five minutes of confirmed use means
                        // that wake attempt did not qualify; begin a fresh sleep candidate.
                        screenOffAt = event.timeStamp
                        candidateUnlock = 0L
                        foregroundStarts.clear()
                        foregroundMillisAfterUnlock = 0L
                    } else if (screenOffAt == 0L) {
                        // Notification-only wake cycles do not replace the first screen-off.
                        screenOffAt = event.timeStamp
                    }
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> {
                    if (screenOffAt > 0L && event.timeStamp - screenOffAt >= 65L * 60L * 1000L) {
                        candidateUnlock = event.timeStamp
                        foregroundStarts.clear()
                        foregroundMillisAfterUnlock = 0L
                    } else {
                        // An unlock before the one-hour inactivity confirmation ends the candidate.
                        screenOffAt = 0L
                        candidateUnlock = 0L
                        foregroundStarts.clear()
                        foregroundMillisAfterUnlock = 0L
                    }
                }
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    val pkg = event.packageName ?: continue
                    if (candidateUnlock > 0L && event.timeStamp >= candidateUnlock &&
                        event.timeStamp <= candidateUnlock + 15L * 60L * 1000L
                    ) {
                        foregroundStarts[pkg] = event.timeStamp
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val pkg = event.packageName ?: continue
                    val began = foregroundStarts.remove(pkg)
                    if (began != null && candidateUnlock > 0L) {
                        foregroundMillisAfterUnlock += (event.timeStamp - began).coerceAtLeast(0L)
                    }
                }
            }
            if (candidateUnlock > 0L && foregroundMillisAfterUnlock >= 5L * 60L * 1000L) {
                result = SleepRecordEntity(
                    sleepStart = screenOffAt,
                    awakenAt = candidateUnlock,
                    sleepWindowMillis = candidateUnlock - screenOffAt,
                    interruptionCount = 0,
                    confidence = "phone-inactivity estimate",
                    createdAt = System.currentTimeMillis()
                )
            }
        }

        if (candidateUnlock > 0L) {
            val now = System.currentTimeMillis()
            foregroundStarts.values.forEach { began ->
                foregroundMillisAfterUnlock += (now - began).coerceAtLeast(0L)
            }
            if (foregroundMillisAfterUnlock >= 5L * 60L * 1000L) {
                result = SleepRecordEntity(
                    sleepStart = screenOffAt,
                    awakenAt = candidateUnlock,
                    sleepWindowMillis = candidateUnlock - screenOffAt,
                    interruptionCount = 0,
                    confidence = "phone-inactivity estimate",
                    createdAt = System.currentTimeMillis()
                )
            }
        }

        if (result != null) {
            val prefs = context.getSharedPreferences("timelog_sleep", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("sleep_start", result.sleepStart)
                .putLong("awaken_at", result.awakenAt)
                .putLong("screen_off_at", result.sleepStart)
                .apply()
            val dao = TimeLogDatabase.get(context).dao()
            val previous = dao.allSleepRecords().firstOrNull { it.sleepStart == result.sleepStart }
            if (previous == null) dao.insertSleep(result)
        }
        return result
    }
}
