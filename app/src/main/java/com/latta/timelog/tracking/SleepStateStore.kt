package com.latta.timelog.tracking

import android.content.Context

class SleepStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("timelog_sleep", Context.MODE_PRIVATE)
    fun screenOffAt(): Long = prefs.getLong("screen_off_at", 0L)
    fun setScreenOffAt(value: Long) = prefs.edit().putLong("screen_off_at", value).apply()
    fun sleepStart(): Long = prefs.getLong("sleep_start", 0L)
    fun setSleepStart(value: Long) = prefs.edit().putLong("sleep_start", value).apply()
    fun pendingWakeAt(): Long = prefs.getLong("pending_wake_at", 0L)
    fun setPendingWakeAt(value: Long) = prefs.edit().putLong("pending_wake_at", value).apply()
    fun awakenAt(): Long = prefs.getLong("awaken_at", 0L)
    fun setAwakenAt(value: Long) = prefs.edit().putLong("awaken_at", value).apply()
}
