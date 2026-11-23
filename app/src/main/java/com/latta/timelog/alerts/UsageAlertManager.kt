package com.latta.timelog.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.latta.timelog.R

class UsageAlertManager(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "timelog_usage_alerts"
        private const val PREFS = "timelog_alerts"
    }

    fun notifyLimitReached(appName: String, message: String, openingCount: Int, threshold: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Usage alerts", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timelog)
            .setContentTitle("$appName opening limit reached")
            .setContentText(message.ifBlank { "You have opened $appName $openingCount times today." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "$message\nOpenings today: $openingCount / $threshold"
            ))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(appName.hashCode(), notification)
    }

    fun shouldNotify(packageName: String, dateKey: String, count: Int, threshold: Int): Boolean {
        if (threshold <= 0 || count < threshold) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "notified_${dateKey}_$packageName"
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }
}
