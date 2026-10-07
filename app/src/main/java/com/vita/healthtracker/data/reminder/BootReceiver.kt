package com.vita.healthtracker.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vita.healthtracker.VitaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 重启后重建提醒闹钟 (AlarmManager 的闹钟在关机时会丢)。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val container = (context.applicationContext as? VitaApplication)?.container
        if (container == null) {
            pending.finish()
            return
        }
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.reminderScheduler.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
