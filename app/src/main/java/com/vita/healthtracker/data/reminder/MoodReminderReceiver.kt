package com.vita.healthtracker.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 到点触发: 弹一条记录心情的通知。每日重复由 AlarmManager 处理, 无需在此重排。 */
class MoodReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReminderScheduler.showReminder(context.applicationContext)
    }
}
