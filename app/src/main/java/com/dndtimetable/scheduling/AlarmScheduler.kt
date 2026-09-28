package com.dndtimetable.scheduling

import com.dndtimetable.system.AppLog

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** 调度事件类型。 */
enum class AlarmKind(val requestCode: Int) {
    DND_START(0),        // 上课：开启免打扰
    DND_END(1),          // 下课：恢复免打扰
    NOTICE_PRE(2),       // 课前提醒：会话首节课前 lead 分钟（上午/下午第一节课）
    NOTICE_CONTENT(3),   // 内容刷新：上课中 / 小课间 / 大课间（按节次规则分类，触发时重算）
    NOTICE_HIDE(4),      // 当天最后一节课结束：撤掉通知
    NOTICE_SPECIAL(5),   // 调休补课日头天 20:00 提醒（文案随闹钟 extras 带入）
    MEDIA_REASSERT(6)    // 媒体兜底（静默精确闹钟）：仅会话内排——进程被杀/冻结时拉起重断言
}

/**
 * 使用系统精确闹钟 setAlarmClock 进行调度（Doze 下也能唤醒，误差 <1 分钟）。
 * 免打扰与通知各自排「下一个」事件。
 */
object AlarmScheduler {
    private const val REQUEST_BASE = 1001
    private const val NOTICE_KEY_MAX = 31   // 每天最多 16 个上课链，show/hide 各占一码

    /** 是否已获得「精确闹钟」权限（Android 12+ 需要）。 */
    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, timeMillis: Long, kind: AlarmKind, key: Int = 0, extras: Map<String, String> = emptyMap()) {
        // 模拟时间（开发者模式）可能把事件推到真实过去：跳过，状态由 scheduleNext 的 silentNow 兜底
        if (timeMillis < System.currentTimeMillis() - 1000) return
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, kind, key, extras)
        try {
            if (canScheduleExact(context)) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(timeMillis, pi), pi)
            } else {
                // 降级为准精确：Doze 下可能延迟数分钟（需引导用户开启「精确闹钟」权限，见首页横幅）
                AppLog.w("dndtimetable", "schedule: 无精确闹钟权限，降级 setAndAllowWhileIdle（kind=$kind，可能延迟）")
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
            }
        } catch (e: Exception) {
            AppLog.w("dndtimetable", "schedule: setAlarmClock 异常，降级 setAndAllowWhileIdle", e)
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi) }
        }
    }

    /**
     * 静默精确闹钟（无状态栏闹钟图标）：用于划后台自愈的一次性媒体重断言
     * （onTaskRemoved 触发，仅会话内，不周期排）。setAlarmClock 会显示/闪烁系统闹钟图标；
     * setExactAndAllowWhileIdle 无图标且同样精确，Doze 下可能被推迟（该场景屏幕常在用，无碍）。
     */
    fun scheduleQuiet(context: Context, timeMillis: Long, kind: AlarmKind, key: Int = 0) {
        if (timeMillis < System.currentTimeMillis() - 1000) return
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, kind, key)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi)
            }
        } catch (e: Exception) {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeMillis, pi) }
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        AlarmKind.values().forEach { kind ->
            val keys = when (kind) {
                AlarmKind.NOTICE_PRE, AlarmKind.NOTICE_CONTENT, AlarmKind.NOTICE_HIDE -> 0..NOTICE_KEY_MAX
                else -> 0..0
            }
            keys.forEach { key -> am.cancel(pendingIntent(context, kind, key)) }
        }
    }

    private fun pendingIntent(context: Context, kind: AlarmKind, key: Int = 0, extras: Map<String, String> = emptyMap()): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).putExtra("kind", kind.requestCode)
        extras.forEach { (k, v) -> intent.putExtra(k, v) }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + kind.requestCode * 100 + key,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
