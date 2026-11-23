package com.latta.timelog.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

class LocalBackupManager(private val context: Context) {
    private val dao = TimeLogDatabase.get(context).dao()

    suspend fun exportTo(uri: Uri) {
        val root = JSONObject()
        root.put("format", "TIMELOG_BACKUP")
        root.put("version", 1)
        root.put("createdAt", System.currentTimeMillis())

        val daily = JSONArray()
        dao.allUsage().forEach { row ->
            daily.put(JSONObject().apply {
                put("dateKey", row.dateKey); put("packageName", row.packageName)
                put("appName", row.appName); put("usageMillis", row.usageMillis)
                put("launchCount", row.launchCount); put("firstOpenedAt", row.firstOpenedAt)
                put("lastOpenedAt", row.lastOpenedAt); put("updatedAt", row.updatedAt)
            })
        }
        root.put("dailyUsage", daily)

        val hourly = JSONArray()
        dao.allHourlyUsage().forEach { row ->
            hourly.put(JSONObject().apply {
                put("hourStart", row.hourStart); put("packageName", row.packageName)
                put("appName", row.appName); put("usageMillis", row.usageMillis)
                put("launchCount", row.launchCount)
            })
        }
        root.put("hourlyUsage", hourly)

        val sleeps = JSONArray()
        dao.allSleepRecords().forEach { row ->
            sleeps.put(JSONObject().apply {
                put("sleepStart", row.sleepStart); put("awakenAt", row.awakenAt)
                put("sleepWindowMillis", row.sleepWindowMillis)
                put("interruptionCount", row.interruptionCount)
                put("confidence", row.confidence); put("createdAt", row.createdAt)
            })
        }
        root.put("sleepRecords", sleeps)

        val settings = JSONObject()
        listOf("timelog_settings", "timelog_alerts", "timelog_focus", "timelog_sleep").forEach { name ->
            val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all
            val objectValues = JSONObject()
            values.forEach { (key, value) ->
                when (value) {
                    is String, is Int, is Long, is Boolean, is Float -> objectValues.put(key, value)
                }
            }
            settings.put(name, objectValues)
        }
        root.put("preferences", settings)

        val output = context.contentResolver.openOutputStream(uri)
            ?: error("Unable to open selected backup destination")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
    }

    suspend fun importFrom(uri: Uri) {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open selected backup file")
        val root = input.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        require(root.optString("format") == "TIMELOG_BACKUP") { "This is not a TIMELOG backup." }
        require(root.optInt("version", -1) == 1) { "Unsupported backup version." }

        val daily = mutableListOf<DailyAppUsageEntity>()
        val dailyArray = root.optJSONArray("dailyUsage") ?: JSONArray()
        for (i in 0 until dailyArray.length()) {
            val item = dailyArray.getJSONObject(i)
            daily += DailyAppUsageEntity(
                dateKey = item.getString("dateKey"),
                packageName = item.getString("packageName"),
                appName = item.optString("appName"),
                usageMillis = item.optLong("usageMillis"),
                launchCount = item.optInt("launchCount"),
                firstOpenedAt = item.optLong("firstOpenedAt"),
                lastOpenedAt = item.optLong("lastOpenedAt"),
                updatedAt = item.optLong("updatedAt")
            )
        }

        val hourly = mutableListOf<HourlyUsageEntity>()
        val hourlyArray = root.optJSONArray("hourlyUsage") ?: JSONArray()
        for (i in 0 until hourlyArray.length()) {
            val item = hourlyArray.getJSONObject(i)
            hourly += HourlyUsageEntity(
                hourStart = item.optLong("hourStart"),
                packageName = item.getString("packageName"),
                appName = item.optString("appName"),
                usageMillis = item.optLong("usageMillis"),
                launchCount = item.optInt("launchCount")
            )
        }

        val sleeps = mutableListOf<SleepRecordEntity>()
        val sleepArray = root.optJSONArray("sleepRecords") ?: JSONArray()
        for (i in 0 until sleepArray.length()) {
            val item = sleepArray.getJSONObject(i)
            sleeps += SleepRecordEntity(
                sleepStart = item.optLong("sleepStart"),
                awakenAt = item.optLong("awakenAt"),
                sleepWindowMillis = item.optLong("sleepWindowMillis"),
                interruptionCount = item.optInt("interruptionCount"),
                confidence = item.optString("confidence", "estimated"),
                createdAt = item.optLong("createdAt")
            )
        }
        val preferences = root.optJSONObject("preferences")
        // Replace only after the complete file has parsed and passed validation.
        dao.replaceAll(daily, hourly, sleeps)
        if (preferences != null) {
            listOf("timelog_settings", "timelog_alerts", "timelog_focus", "timelog_sleep").forEach { name ->
                val values = preferences.optJSONObject(name) ?: return@forEach
                val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                val keys = values.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    when (val value = values.get(key)) {
                        is String -> editor.putString(key, value)
                        is Int -> editor.putInt(key, value)
                        is Long -> editor.putLong(key, value)
                        is Boolean -> editor.putBoolean(key, value)
                        is Double -> editor.putFloat(key, value.toFloat())
                    }
                }
                editor.apply()
            }
        }
    }
}
