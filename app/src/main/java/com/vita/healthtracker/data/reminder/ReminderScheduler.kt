package com.vita.healthtracker.data.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.vita.healthtracker.R
import com.vita.healthtracker.data.prefs.SettingsPreferences
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first

/**
 * 记录提醒调度器。用 AlarmManager 的非精确每日重复闹钟 (无需精确闹钟特殊权限, 省电),
 * 在用户设定的时刻触发 [MoodReminderReceiver] 弹通知。重启后闹钟会丢, 由 [BootReceiver] 重建。
 */
class ReminderScheduler(
    private val context: Context,
    private val prefs: SettingsPreferences,
) {
    private val alarmManager: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    /** 取消旧闹钟, 按当前开关与时刻重新登记。开关关闭时只取消。 */
    suspend fun reschedule() {
        cancelAll()
        if (!prefs.reminderEnabled.first()) return
        prefs.reminderTimes.first().take(MAX_REMINDERS).forEachIndexed { index, time ->
            schedule(index, time)
        }
    }

    private fun schedule(index: Int, time: LocalTime) {
        val now = ZonedDateTime.now()
        var trigger = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!trigger.isAfter(now)) trigger = trigger.plusDays(1)
        runCatching {
            alarmManager?.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                trigger.toInstant().toEpochMilli(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent(index),
            )
        }
    }

    private fun cancelAll() {
        (0 until MAX_REMINDERS).forEach { runCatching { alarmManager?.cancel(pendingIntent(it)) } }
    }

    private fun pendingIntent(index: Int): PendingIntent {
        val intent = Intent(context, MoodReminderReceiver::class.java).setAction("$ACTION_FIRE#$index")
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + index,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val MAX_REMINDERS = 4
        private const val ACTION_FIRE = "com.vita.healthtracker.MOOD_REMINDER"
        private const val REQUEST_BASE = 9200
        private const val ChannelId = "vita_mood_reminder"
        private const val NotificationId = 8202

        /** 弹一条「记一下心情」的通知; 没有通知权限时静默跳过。 */
        fun showReminder(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(manager)
            val openIntent = PendingIntent.getActivity(
                context,
                0,
                context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = Notification.Builder(context, ChannelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("现在心情怎么样？")
                .setContentText("花几秒记一下此刻的状态，记得越随手，趋势越准。")
                .setContentIntent(openIntent)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(NotificationId, notification) }
        }

        private fun ensureChannel(manager: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        ChannelId,
                        "心情记录提醒",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "在你设定的时刻提醒记录心情"
                    },
                )
            }
        }
    }
}
