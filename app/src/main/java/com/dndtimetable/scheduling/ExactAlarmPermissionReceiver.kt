package com.dndtimetable.scheduling

import com.dndtimetable.system.AppLog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 「精确闹钟」权限变化后立即重排（Android 12+）。
 * 用户从系统设置授予/撤销该权限时会发这条系统广播：
 * - 授予后立刻重排 → 后续事件由 setAlarmClock 精确触发（不再受 Doze 批处理影响）；
 * - 撤销后重排 → 自动降级为 setAndAllowWhileIdle（准精确，可能延迟数分钟）。
 * action 用字面量而非 AlarmManager 常量：常量虽为编译期内联，字面量可彻底避免低版本类加载风险
 * （历史教训：LocalDate.ofInstant 是 API 34 方法，低版本直接崩溃）。
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_EXACT_ALARM_PERMISSION_CHANGED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppLog.w("dndtimetable", "精确闹钟权限变化 → 重排闹钟（授予=${AlarmScheduler.canScheduleExact(context)}）")
                ScheduleHelper.scheduleNext(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED（API 31+ 系统广播）。 */
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
