package com.dndtimetable.system

import com.dndtimetable.system.AppLog

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import android.media.AudioManager

/**
 * L1：免打扰（Do Not Disturb）。依赖系统权限 ACCESS_NOTIFICATION_POLICY，用户首次手动授权一次。
 */
class DndController(private val context: Context) {

    private val nm: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun isAccessGranted(): Boolean = nm.isNotificationPolicyAccessGranted

    fun currentFilter(): Int = nm.currentInterruptionFilter

    fun currentRingerMode(): Int =
        context.getSystemService(AudioManager::class.java).ringerMode

    /**
     * 本地设置铃声模式（SILENT/VIBRATE/NORMAL）。
     * 持有「通知使用权」的 app 在 Android 9+ 允许改铃声模式；设置后读回校验。
     * 免打扰激活/关闭的异步状态机可能覆盖 ringer，读回失败时重试（最多约 1.5s）。
     */
    fun setRingerModeLocal(mode: Int): Boolean = try {
        val audio = context.getSystemService(AudioManager::class.java)
        repeat(6) {
            audio.ringerMode = mode
            if (audio.ringerMode == mode) return true
            Thread.sleep(250)
        }
        false
    } catch (e: Exception) {
        false
    }

    /**
     * 当前是否处于静音/免打扰状态（含系统级定时免打扰）：
     * 只要过滤器不是「全部允许」即视为静音中（PRIORITY/ALARMS/NONE 都算）。
     */
    fun isSilentNow(): Boolean =
        isAccessGranted() &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    /** 开启静默（等效静音：不响铃、不震动、无通知音、闹钟与来电也静音）。 */
    fun setSilentAll(): Boolean {
        if (!isAccessGranted()) return false
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        return true
    }

    /**
     * 自定义勿扰：PRIORITY 模式 + 仅放行媒体音的策略。
     * 来电/通知/提醒全部不优先（被屏蔽），媒体音明确放行 → 「无通知无通知音，但有媒体音」。
     * 注意：会覆盖系统全局策略，调用方需在课前保存策略、结束后还原。
     * 多机型适配：部分 ROM 可能忽略写入的策略/过滤器——写后读回校验，失败自动降级全静默（INTERRUPTION_FILTER_NONE）。
     * 返回 true 表示免打扰已开启（PRIORITY 或降级的 NONE 均算成功）。
     */
    fun setDndFiltered(): Boolean {
        if (!isAccessGranted()) return false
        runCatching {
            nm.setNotificationPolicy(
                Policy(NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, 0, 0)
            )
            val p = nm.notificationPolicy
            val mediaHonored = p != null && (p.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA) != 0
            if (mediaHonored) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) return true
            }
        }
        AppLog.w("dndtimetable", "DND 策略未被尊重（ROM 忽略策略），降级为全静默")
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        return true
    }

    /** 当前全局 DND 策略（3 个核心字段）。 */
    fun currentPolicy3(): Triple<Int, Int, Int>? = runCatching {
        val p = nm.notificationPolicy
        Triple(p.priorityCategories, p.priorityCallSenders, p.priorityMessageSenders)
    }.getOrNull()

    fun restorePolicy3(p: Triple<Int, Int, Int>?) {
        if (p == null) return
        runCatching { nm.setNotificationPolicy(Policy(p.first, p.second, p.third)) }
    }

    /** 关闭免打扰（含用户手动开的）：写后读回校验+重试，防部分 ROM 异步回滚覆盖。返回是否确认关闭。 */
    fun clear(): Boolean {
        if (!isAccessGranted()) return false
        repeat(4) {
            runCatching { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
            if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) return true
            Thread.sleep(200)
        }
        AppLog.w("dndtimetable", "clear: 读回失败，filter=${nm.currentInterruptionFilter}")
        return false
    }

    /** 恢复为指定过滤级别（用于「回到上课前状态」）。 */
    fun setFilter(filter: Int): Boolean {
        if (!isAccessGranted()) return false
        nm.setInterruptionFilter(filter)
        return true
    }
}
