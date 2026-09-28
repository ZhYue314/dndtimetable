package com.dndtimetable.scheduling

import com.dndtimetable.system.AppLog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 系统时间/时区变化后重排闹钟（用户在设置里改时间、跨时区旅行、自动时区跳变等）。
 * 与 BootReceiver 同模式：goAsync + IO 协程，执行完 finish。异常绝不外泄。
 */
class TimeChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ScheduleHelper.scheduleNext(context)
            } catch (e: Exception) {
                AppLog.e("dndtimetable", "time changed reschedule failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
