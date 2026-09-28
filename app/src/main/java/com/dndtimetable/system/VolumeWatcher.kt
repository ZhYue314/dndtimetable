package com.dndtimetable.system

import com.dndtimetable.system.AppLog

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * 课中音量变化的兜底：用户按音量键（或系统/其它应用改媒体音量）可能解除静音标记，
 * 而音量变化不会触发 `AudioDeviceCallback`（那只管音频路由）——此前这类"解除"无人接管，
 * 本节课会一直不屏蔽（评审 P1-4）。
 *
 * 只监听公开的 `Settings.System` 内容变化（不依赖隐藏广播），且**仅在会话激活期间注册**
 * （start() 注册、end()/会话结束时注销），把开销限制在课内；
 * 检测到音量值或静音状态相对基线发生变化时，交给 [SilentExecutor.reapplyMediaMute] 按当前路由重断言
 * （戴耳机时内部放行、不动耳机音量；不戴耳机时把扬声器重新屏蔽——课上锁死外放是预期行为）。
 */
object VolumeWatcher {

    private var observer: ContentObserver? = null
    private var appContext: Context? = null
    private var lastVolume = -1
    private var lastMuted = false

    fun start(context: Context) {
        if (observer != null) return
        val app = context.applicationContext
        val audio = app.getSystemService(AudioManager::class.java)
        lastVolume = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(-1)
        lastMuted = runCatching { audio.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(false)
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                runCatching { checkAndReassert(app) }
            }
        }
        runCatching { app.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, o) }
        observer = o
        appContext = app
        AppLog.w("dndtimetable", "VolumeWatcher: 会话内开始监听音量变化（基线 vol=$lastVolume mute=$lastMuted）")
    }

    fun stop() {
        val o = observer ?: return
        val app = appContext
        observer = null
        appContext = null
        if (app != null) runCatching { app.contentResolver.unregisterContentObserver(o) }
        AppLog.w("dndtimetable", "VolumeWatcher: 停止监听音量变化")
    }

    private fun checkAndReassert(app: Context) {
        val store = DndStateStore(app)
        if (store.loadFilter() == null || store.isSessionStale()) {
            stop()   // 会话已结束：顺手注销，避免空转
            return
        }
        if (!store.loadAppliedMedia()) return
        val audio = app.getSystemService(AudioManager::class.java)
        val vol = runCatching { audio.getStreamVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(lastVolume)
        val muted = runCatching { audio.isStreamMute(AudioManager.STREAM_MUSIC) }.getOrDefault(lastMuted)
        if (vol == lastVolume && muted == lastMuted) return   // 与音量无关的系统设置变化：忽略
        AppLog.w(
            "dndtimetable",
            "VolumeWatcher: 检出音量变化 vol=$lastVolume→$vol mute=$lastMuted→$muted，重断言媒体屏蔽"
        )
        lastVolume = vol
        lastMuted = muted
        SilentExecutor.reapplyMediaMute(app)
    }
}
