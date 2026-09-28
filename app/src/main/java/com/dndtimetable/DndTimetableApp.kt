package com.dndtimetable

import android.app.Application
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.dndtimetable.di.AppGraph
import com.dndtimetable.system.AppLog
import com.dndtimetable.system.CrashLog
import com.dndtimetable.system.DevClock
import com.dndtimetable.system.MediaMuteMode
import com.dndtimetable.system.SilentExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class DndTimetableApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)   // 必须最先：其后所有日志按档位落盘
        CrashLog.install(this)
        AppGraph.init(this)
        // 开发者模式：把模拟时间偏移同步到全局时钟
        CoroutineScope(Dispatchers.Default).launch {
            AppGraph.settings.devOffsetMin.collect { DevClock.offsetMs = it * 60_000L }
        }
        // 媒体屏蔽方式缓存（muteMedia 可能在主线程被事件回调调用，不能在那里读 DataStore）
        CoroutineScope(Dispatchers.Default).launch {
            AppGraph.settings.forceZeroVolume.collect { MediaMuteMode.forceZeroVolume = it }
        }
        // 音频设备变化（拔/插耳机）后按当前路由重断言媒体音（路由感知：有耳机放行、无耳机屏蔽）。
        // 拔下 → 重新屏蔽扬声器；插上 → 放行给耳机听。进程由 MediaKeepaliveService 在课中保活。
        val audio = getSystemService(AudioManager::class.java)
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                SilentExecutor.reapplyMediaMute(this@DndTimetableApp)
            }

            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                SilentExecutor.reapplyMediaMute(this@DndTimetableApp)
            }
        }, Handler(Looper.getMainLooper()))
        // App 启动即自愈：① 上次下课没跑完导致媒体音量卡 0（孤儿归零）→ 还原课前音量；
        // ② 上次下课没跑完导致全局 DND 策略残留成"仅放行媒体" → 据守卫原值还原
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { SilentExecutor.healOrphanZeroedVolume(this@DndTimetableApp) }
            runCatching { SilentExecutor.healOrphanDndPolicy(this@DndTimetableApp) }
        }
        // 分享/应用内更新留下的临时 APK（每份 ~2MB，同名覆盖但会一直占着）：启动时清掉，
        // 上一轮的接收方早已拿走；还没装的更新也会重新检查下载
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { File(cacheDir, "share").listFiles()?.forEach { it.delete() } }
        }
    }
}
