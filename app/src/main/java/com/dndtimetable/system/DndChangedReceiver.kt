package com.dndtimetable.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 免打扰过滤级别变化接收器：用户在控制中心手动开/关免打扰后，
 * 系统可能重置媒体静音/还原铃声——会话激活时补打（媒体屏蔽 + 铃声静音）。
 * 与 AudioRouteReceiver 同模式：goAsync + IO 协程，异常不外泄。
 */
class DndChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SilentExecutor.reapplyAfterDndChange(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
