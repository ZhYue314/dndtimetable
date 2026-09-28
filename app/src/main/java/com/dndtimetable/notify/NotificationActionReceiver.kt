package com.dndtimetable.notify

import com.dndtimetable.system.AppLog

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dndtimetable.system.DndStateStore
import com.dndtimetable.system.SilentExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 课中通知卡上的按钮动作：
 * - 「解除屏蔽媒体音」：只解除媒体维度（铃声/免打扰保留），本节不再自动屏蔽，下节课恢复
 *   （课中按音量键会被回弹以防误触，这里给用户一个明确的解除入口）。
 * - 「屏蔽媒体音」：上面动作的反向——本节内重新开启媒体屏蔽（可反复切换）。
 * - 「重新开启」：手动关闭本节自动静音后的反向入口：清除抑制并立即恢复自动静音。
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_UNMUTE_MEDIA && action != ACTION_MUTE_MEDIA && action != ACTION_REENABLE) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_UNMUTE_MEDIA -> {
                        AppLog.w("dndtimetable", "通知动作：收到「解除屏蔽媒体音」点击")
                        SilentExecutor.unmuteMediaForSession(context)
                        StatusNotification.refreshMediaAction(context)
                    }
                    ACTION_MUTE_MEDIA -> {
                        AppLog.w("dndtimetable", "通知动作：收到「屏蔽媒体音」点击")
                        SilentExecutor.remuteMediaForSession(context)
                        StatusNotification.refreshMediaAction(context)
                    }
                    ACTION_REENABLE -> {
                        AppLog.w("dndtimetable", "通知动作：收到「重新开启」点击")
                        DndStateStore(context).clearManualSuppress()
                        SilentExecutor.start(context)
                        StatusNotification.update(context)
                    }
                }
            } catch (e: Exception) {
                AppLog.e("dndtimetable", "通知动作失败", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_UNMUTE_MEDIA = "com.dndtimetable.action.UNMUTE_MEDIA"
        const val ACTION_MUTE_MEDIA = "com.dndtimetable.action.MUTE_MEDIA"
        const val ACTION_REENABLE = "com.dndtimetable.action.REENABLE"
    }
}
