package com.dndtimetable.system

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/** 当前系统音频/免打扰状态（用于展示与还原）。 */
data class AudioState(
    val ringerMode: Int,
    val dndFilter: Int
) {
    val ringerText: String
        get() = when (ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "静音"
            AudioManager.RINGER_MODE_VIBRATE -> "震动"
            else -> "响铃"
        }

    val dndText: String
        get() = when (dndFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "关闭"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "仅闹钟"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "优先"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "静默(全部静音)"
            else -> "未知($dndFilter)"
        }

    val summary: String
        get() = "铃声模式: $ringerText  |  免打扰: $dndText"
}

/** 读取当前系统状态。 */
fun readAudioState(context: Context): AudioState {
    val audio = context.getSystemService(AudioManager::class.java)
    val nm = context.getSystemService(NotificationManager::class.java)
    return AudioState(audio.ringerMode, nm.currentInterruptionFilter)
}
