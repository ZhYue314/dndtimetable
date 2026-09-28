package com.dndtimetable.system

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.dndtimetable.MainActivity
import com.dndtimetable.R
import com.dndtimetable.notify.StatusNotification
import com.dndtimetable.scheduling.AlarmKind
import com.dndtimetable.scheduling.AlarmScheduler
import com.dndtimetable.scheduling.ScheduleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "dndtimetable"

/**
 * 媒体屏蔽保活：仅上课时运行 MediaKeepaliveService 保住进程——
 * 拔/插耳机事件由 AudioDeviceCallback 即时响应（亚秒级）。
 * 进程被杀/冻结会打断事件驱动（HyperOS 4/Android 17 划后台会延迟重启前台服务），
 * 故由划后台时的立即自愈（onTaskRemoved：重拉服务 + 1 秒一次性闹钟）兜底，
 * 不做周期闹钟（课中反复唤醒耗电）。
 */
object MediaKeepalive {

    /** 会话激活时拉起前台服务（上课 start() 调用）。 */
    suspend fun maybeStart(context: Context) {
        val store = DndStateStore(context)
        if (store.loadFilter() == null || store.isSessionStale()) return
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                context, Intent(context, MediaKeepaliveService::class.java)
            )
        }
    }

    /** 结束服务（下课/会话结束/总开关关闭时调用）。 */
    fun stop(context: Context) {
        runCatching { context.stopService(Intent(context, MediaKeepaliveService::class.java)) }
    }

    /**
     * 划后台自愈：服务 [MediaKeepaliveService.onTaskRemoved] 与开发者「模拟划后台」共用同一条逻辑——
     * ① 原地重断言媒体屏蔽；② 重拉前台服务（进程存活则通知/监听立即恢复）；
     * ③ 排 1 秒一次性 MEDIA_REASSERT 闹钟（进程随后被 ROM 回收也能秒级拉回）。返回是否触发（无活动会话=false）。
     */
    fun healAfterSwipe(context: Context): Boolean {
        val store = DndStateStore(context)
        if (store.loadFilter() == null || store.isSessionStale()) return false
        AppLog.w("dndtimetable", "keepalive: 划后台自愈（重断言+重拉服务+1s闹钟）")
        runCatching { SilentExecutor.reapplyMediaMute(context) }
        runCatching {
            androidx.core.content.ContextCompat.startForegroundService(
                context, Intent(context, MediaKeepaliveService::class.java)
            )
        }
        runCatching {
            AlarmScheduler.scheduleQuiet(context, System.currentTimeMillis() + 1000L, AlarmKind.MEDIA_REASSERT)
        }
        return true
    }
}

/**
 * 仅上课时运行的前台服务。作用：保住进程，使拔/插耳机、DND 变化等事件回调始终可用
 * （进程被杀是纯事件驱动失效的根因）。
 * 前台通知 = 与「常驻状态卡」同 ID / 同通道 / 同文案（一致样式）：
 * 上课期间状态卡 update() 刷新的就是这条，通知栏只有一张，不会被划掉。
 * 被系统重启时（START_STICKY）自愈一次——会话已结束则自停。
 */
class MediaKeepaliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val store = DndStateStore(this)
        AppLog.w(TAG, "keepalive: onStartCommand（系统重启=${intent == null}）")
        // 会话已结束（下课/强停后无会话）→ 自停
        if (store.loadFilter() == null || store.isSessionStale()) {
            AppLog.w(TAG, "keepalive: 会话已结束，自停")
            stopSelf()
            return START_NOT_STICKY
        }
        // 系统重启（START_STICKY 时 intent 为 null）：HyperOS 划掉后台可能连闹钟链一起清，
        // 重排一次调度（含媒体兜底闹钟），保证自愈链完整；显式 start 的 intent 非空，不会递归。
        if (intent == null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { ScheduleHelper.scheduleNext(this@MediaKeepaliveService) }
            }
        }
        // 进程被系统重启/长期休眠后：立即按当前路由自愈一次，并确保音量监听在会话内生效
        runCatching { SilentExecutor.reapplyMediaMute(this) }
        runCatching { if (DndStateStore(this).loadAppliedMedia()) VolumeWatcher.start(this) }
        StatusNotification.ensureChannel(this)
        // 与状态卡同款内容（IN_CLASS 文案 + 当前课程/关闭时间）+ 媒体屏蔽按钮；
        // 此后 update()/DND 事件刷新同一条
        val svc = this
        val (title, text) = runCatching {
            runBlocking { StatusNotification.currentCardContent(svc) }
        }.getOrElse { "上课中" to "保持媒体屏蔽监听" }
        val notif = StatusNotification.buildCardNotification(this, title, text, StatusNotification.currentMediaAction(this))
        startForeground(StatusNotification.NOTIF_ID, notif)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 用户从最近任务划掉 App：HyperOS 4 / Android 17 会把前台服务连同通知一起杀掉，
     * 且数秒~数分钟后才重启（START_STICKY），空窗期音量键解除媒体静音无人回弹。这里立即自愈：
     * ① 原地重断言并把服务再拉一次（进程若存活，通知立即回来、监听立即恢复）；
     * ② 排 1 秒后的静默精确闹钟——即使进程马上被 ROM 回收，闹钟也会把 App 拉起来经
     *    ScheduleReceiver 重断言并重排调度，把空窗从分钟级压到秒级（一次性，不周期唤醒）。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        MediaKeepalive.healAfterSwipe(this)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        AppLog.w(TAG, "keepalive: onDestroy（服务停止）")
        super.onDestroy()
    }
}
