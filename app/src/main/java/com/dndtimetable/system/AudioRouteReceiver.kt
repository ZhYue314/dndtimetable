package com.dndtimetable.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 耳机拔插广播：部分系统在音频输出设备变化时（拔下/插上耳机）重置媒体流静音/音量，
 * 导致「屏蔽扬声器媒体音」失效。这里在拔/插时按当前路由重断言（路由感知：拔下→重新屏蔽
 * 扬声器；插上→放行给耳机听，见 SilentExecutor.muteMedia）。
 * 注意：Android 8+ 隐式广播限制下该接收器不一定能收到（进程被杀时），
 * 因此 DndTimetableApp 里的 AudioDeviceCallback 与「会话内 10 分钟看门狗」是双保险。
 */
class AudioRouteReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AudioManager.ACTION_HEADSET_PLUG) return
        // state: 0=拔出，1=插入；两个方向都按路由重断言
        val state = intent.getIntExtra("state", -1)
        if (state != 0 && state != 1) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SilentExecutor.reapplyMediaMute(context)
            } finally {
                pending.finish()
            }
        }
    }
}
