package com.dndtimetable.scheduling

import com.dndtimetable.system.AppLog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dndtimetable.di.AppGraph
import com.dndtimetable.notify.StatusNotification
import com.dndtimetable.system.SilentExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kindId = intent.getIntExtra("kind", AlarmKind.DND_START.requestCode)
        val kind = AlarmKind.values().firstOrNull { it.requestCode == kindId } ?: AlarmKind.DND_START
        AppLog.d("dndtimetable", "onReceive kind=$kind")
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!AppGraph.settings.schedulingEnabled.first()) {
                    AppLog.d("dndtimetable", "调度已关闭，忽略")
                    AlarmScheduler.cancel(context)
                    StatusNotification.cancel(context)
                    return@launch
                }
                when (kind) {
                    AlarmKind.DND_START -> SilentExecutor.start(context)
                    AlarmKind.DND_END -> SilentExecutor.end(context)
                    AlarmKind.NOTICE_PRE -> {
                        // 上课前提醒：与上课中/课间共用同一条常驻卡（update 按 PRE 阶段显示「快要上课」），
                        // 不再发可划掉的独立提醒通知（避免被划掉后课上/课间通知缺失）
                        StatusNotification.update(context)
                    }
                    AlarmKind.NOTICE_CONTENT -> StatusNotification.update(context)   // 上课中/小课间/大课间内容刷新
                    AlarmKind.NOTICE_HIDE -> StatusNotification.cancel(context)
                    // 划后台自愈的一次性闹钟：拉起进程按当前路由重断言媒体屏蔽（不续排）
                    AlarmKind.MEDIA_REASSERT -> SilentExecutor.reapplyMediaMute(context)
                    // 调休补课提醒：一次性可划掉通知（独立 ID/渠道，不与常驻卡互相覆盖）
                    AlarmKind.NOTICE_SPECIAL -> StatusNotification.showOnce(
                        context,
                        intent.getStringExtra("title") ?: "调休提醒",
                        intent.getStringExtra("text") ?: ""
                    )
                }
                ScheduleHelper.scheduleNext(context)
            } catch (e: Exception) {
                AppLog.e("dndtimetable", "receiver failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
