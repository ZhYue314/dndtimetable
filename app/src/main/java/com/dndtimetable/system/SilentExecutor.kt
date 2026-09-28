package com.dndtimetable.system

import com.dndtimetable.system.AppLog

import android.app.NotificationManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.dndtimetable.di.AppGraph
import com.dndtimetable.scheduling.StatusSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 按设置的「静音方式」执行上课开启 / 下课还原。
 * 三个维度（可任意组合）：
 * - silent：铃声模式 SILENT——来电/通知无声但通知横幅仍显示
 * - media：屏蔽媒体音——**只屏蔽手机扬声器的媒体音**（有耳机/蓝牙/USB 等收听设备时放行，耳机内照常播放）
 * - filter：免打扰——来电和通知被屏蔽（PRIORITY + 仅媒体放行策略，媒体音放行但可被 media 维度硬静音）
 * 铃声模式用本地 AudioManager（app 持有通知使用权即可改），失败降级为免打扰。
 * 下课时按 DndStateStore 记录的实际机制还原。
 */

/** 课程结束时铃声/免打扰的还原动作（纯决策逻辑，可 JVM 单测）。 */
enum class EndRingerAction { NOTHING, RESTORE_PREV_RINGER, CLEAR_DND }

/**
 * 根据「本次会话实际应用的机制」决定下课时的铃声/免打扰动作：
 * - 本地铃声静音 → 还原课前铃声（RESTORE_PREV_RINGER）；
 * - 铃声静音失败降级为免打扰（且未主动勾选 filter）→ 关闭免打扰（CLEAR_DND），
 *   此时课前必然没有用户手动 DND（有则走借用路径，见 applyRingerSilent）；
 * - 本次未动/借用用户手动 DND（filter 已在上方按课前状态还原或清空）/仅媒体 → 什么都不做，
 *   绝不抢用户手动开的免打扰。
 */
internal fun decideEndRinger(applied: Int, appliedFilter: Boolean): EndRingerAction = when (applied) {
    DndStateStore.APPLIED_LOCAL_RINGER -> EndRingerAction.RESTORE_PREV_RINGER
    DndStateStore.APPLIED_DND -> if (appliedFilter) EndRingerAction.NOTHING else EndRingerAction.CLEAR_DND
    else -> EndRingerAction.NOTHING   // APPLIED_NONE / APPLIED_BORROW_DND
}

/** 归零自愈窗口：守卫超过该时长未处理即视为过期（避免很久以后误改用户手动调低的音量）。 */
internal const val ZERO_GUARD_WINDOW_MS = 24 * 60 * 60 * 1000L

/**
 * 孤儿归零自愈判定（纯逻辑，可 JVM 单测）：仅当**全部**条件成立才动手——
 * 守卫有效、有可用的课前音量、当前无会话、音量仍恰为 0、守卫未过期。
 * 条件保守是刻意的：宁可漏救，绝不擅改用户自己调过的音量。
 */
internal fun shouldHealZeroedVolume(
    guardActive: Boolean,
    guardPreVol: Int?,
    sessionActive: Boolean,
    currentVolume: Int,
    guardAgeMs: Long,
    windowMs: Long = ZERO_GUARD_WINDOW_MS
): Boolean = guardActive &&
    guardPreVol != null && guardPreVol > 0 &&
    !sessionActive &&
    currentVolume == 0 &&
    guardAgeMs in 0..windowMs

/**
 * 手动关闭是否仍处于抑制期（纯逻辑）：用户在课中主动关掉后，本节剩余时间不再自动静音。
 * untilMs<=0 视为未抑制。
 */
internal fun isManualSuppressed(untilMs: Long, nowMs: Long): Boolean = untilMs > 0 && nowMs < untilMs

/**
 * 当前全局 DND 策略是否是「我们为免打扰模式写入」的特征值：PRIORITY 类别仅放行媒体、来电/消息发送者 0。
 * 系统 UI 无法配置出这种组合（媒体类别不在用户可勾选列表中），故命中即几乎必然是我们的残留。
 */
internal fun isOurDndPolicySignature(cats: Int, callSenders: Int, msgSenders: Int): Boolean =
    cats == NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA && callSenders == 0 && msgSenders == 0

/**
 * 下课竞态（检测到新会话）时，本次会话的媒体屏蔽是否还需要还原：
 * 新会话已声明接管媒体屏蔽（appliedMedia）→ 跳过（否则会破坏新会话）；否则仍还原（防静音/0 音量残留）。
 */
internal fun decideEndMediaRestore(appliedMedia: Boolean, newSessionMediaActive: Boolean): Boolean =
    appliedMedia && !newSessionMediaActive

/**
 * 媒体屏蔽方式的内存缓存（由 DndTimetableApp 收集设置后写入）：
 * muteMedia 可能在主线程（AudioDeviceCallback）被调用，不能在那里读 DataStore（会卡主线程）。
 */
object MediaMuteMode {
    /** 兼容模式：强制降音量屏蔽（跳过静音标记），面向"标记谎报成功"的 ROM。 */
    @Volatile
    var forceZeroVolume: Boolean = false
}

object SilentExecutor {

    /**
     * 孤儿归零自愈：曾把媒体音量降零，但会话已结束（end() 未跑完 / 进程被杀 / 强停 / 闹钟丢失）
     * → 音量会一直卡在 0，且下一次上课会把 0 记成"课前音量"导致永久回不来（历史 bug）。
     * 由 App 启动、每次 scheduleNext、每次 start() 之前调用；条件保守（见 shouldHealZeroedVolume）。
     */
    fun healOrphanZeroedVolume(context: Context) {
        runCatching {
            val store = DndStateStore(context)
            if (!store.loadZeroGuardActive()) return
            val audio = context.getSystemService(AudioManager::class.java)
            val guardPre = store.loadZeroGuardPreVol()
            val ageMs = System.currentTimeMillis() - store.loadZeroGuardTs()
            val sessionActive = store.loadFilter() != null && !store.isSessionStale()
            val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (!shouldHealZeroedVolume(store.loadZeroGuardActive(), guardPre, sessionActive, cur, ageMs)) {
                // 过期或用户已自行调整过音量：清掉过期守卫，不再干预
                if (ageMs > ZERO_GUARD_WINDOW_MS) store.clearZeroGuard()
                return
            }
            AppLog.w("dndtimetable", "自愈：检出孤儿归零（音量 0，守卫 $guardPre），还原课前音量")
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, guardPre ?: return, 0)
            store.clearZeroGuard()
        }
    }

    /**
     * 孤儿 DND 策略自愈：end() 未跑完时全局策略会残留成"仅放行媒体"（用户以后手动开勿扰会
     * 来电/通知不响、媒体却响——静默污染系统设置）。无会话 + 当前策略命中我们的特征值 +
     * 有跨会话保存的原值（策略守卫）→ 还原原值；无守卫记录时**只记日志、绝不动系统设置**。
     * 调用点：App 启动、每次 scheduleNext。
     */
    fun healOrphanDndPolicy(context: Context) {
        runCatching {
            val store = DndStateStore(context)
            val dnd = DndController(context)
            if (!dnd.isAccessGranted()) return
            if (store.loadFilter() != null && !store.isSessionStale()) return   // 会话中：策略正在使用
            val p = dnd.currentPolicy3() ?: return
            if (!isOurDndPolicySignature(p.first, p.second, p.third)) return
            val guard = store.loadPolicyGuard()
            if (guard == null) {
                AppLog.w("dndtimetable", "自愈：检出疑似残留的 DND 策略，但无守卫原值 → 不改动系统设置")
                return
            }
            AppLog.w("dndtimetable", "自愈：还原残留的自定义 DND 策略 → $guard")
            dnd.restorePolicy3(guard)
            store.clearPolicyGuard()
        }
    }

    /**
     * 按设置的静音配置（可组合）执行上课开启：
     * - silent：铃声静音（本地 AudioManager，失败则免打扰兜底）
     * - media：屏蔽媒体音（setStreamMute，被拒则音量归零兜底）
     * - filter：免打扰（PRIORITY+仅媒体放行策略：来电/通知屏蔽，媒体音放行——若同时勾了 media 则媒体仍被硬静音）
     * 下课时按实际应用的维度逐一还原（DndStateStore 记录）。
     */
    suspend fun start(context: Context) {
        // 单门课可单独指定方式（如只有静音、不屏蔽媒体）：上课事件触发时取当前课的方式，否则用全局
        val cfg = StatusSource.effectiveDndConfig(context)
        val dnd = DndController(context)
        val store = DndStateStore(context)

        // 用户本节课已主动关闭（首页按钮）→ 抑制期内不再自动拉起静音，直到本节窗口结束（下一节恢复自动）
        if (isManualSuppressed(store.loadManualSuppressUntil(), System.currentTimeMillis())) {
            AppLog.w(
                "dndtimetable",
                "start: 用户已手动关闭本节自动静音（抑制至 ${store.loadManualSuppressUntil()}），跳过"
            )
            return
        }

        // 上课前先自愈"孤儿归零"：否则会把当前音量 0 记成课前音量，导致课后永久回不来（历史 bug）
        healOrphanZeroedVolume(context)

        // 已在有效会话中：不重复记录课前状态（否则恢复时会还原成"静音中"）；
        // 快照陈旧（无快照/强停或关机遗留的上一会话）则按新会话重记，避免按过期状态还原。
        if (store.loadFilter() == null || store.isSessionStale()) {
            store.savePreStart(dnd.currentFilter(), dnd.currentRingerMode())
            store.savePrePolicy(dnd.currentPolicy3())   // PRIORITY+策略会覆盖全局策略，课后必须还原
            // 记录会话所属课表：切表后 scheduleNext 据此收尾旧会话（否则旧表免打扰/通知一直挂着）
            store.saveSessionScheduleId(AppGraph.settings.getActiveScheduleId())
            // 跨会话策略守卫：同上原值再存一份（不被 clear 清除），供 end() 未跑完时自愈还原
            if (cfg.filter) dnd.currentPolicy3()?.let { store.savePolicyGuard(it) }
            // 会话开始即锁定还原机制：未勾选 silent = APPLIED_NONE。旧实现仅在勾选时写入，
            // loadApplied 默认值会让下课误判为「降级了免打扰」而 clear 掉用户手动开的免打扰。
            store.saveApplied(
                if (cfg.silent) applyRingerSilent(dnd, allowDndFallback = true) else DndStateStore.APPLIED_NONE
            )
        } else {
            // 会话已激活：按当前配置补应用静音，但不覆盖开始时的还原机制（避免课上改配置导致下课行为跳变）
            if (cfg.silent) applyRingerSilent(dnd, allowDndFallback = true)
        }
        if (cfg.filter) {
            dnd.setDndFiltered()   // 来电/通知屏蔽，媒体音（策略里）放行
            store.saveAppliedFilter(true)
        }
        if (cfg.media && !store.loadMediaManualOff()) {
            store.saveAppliedMedia(true)
            muteMedia(context, store)
            // 课中音量键/系统改音量可能解除静音标记：会话内监听音量变化并回弹（退出会话时注销）
            VolumeWatcher.start(context)
        }
        // 前台服务保活模式：会话激活期间保住进程（Event 回调常驻），下课由 end() 停止
        MediaKeepalive.maybeStart(context)
    }

    /**
     * 用户在课中从通知按钮主动解除「屏蔽媒体音」（只解除媒体维度，铃声/免打扰保留）：
     * - 置会话内 mediaManualOff → 后续事件（拔/插耳机、音量变化、DND 变化）与 start() 都不会再自动屏蔽；
     * - appliedMedia 置 false → 下课时不会再"还原"它；
     * - 立即解除静音并还原课前音量（若曾降零），清归零守卫；停音量监听避免与用户对着干。
     * 下节课（新会话）恢复自动屏蔽（该标记随 clear 清除）。
     */
    fun unmuteMediaForSession(context: Context) {
        runCatching {
            val store = DndStateStore(context)
            store.saveMediaManualOff(true)
            store.saveAppliedMedia(false)
            VolumeWatcher.stop()
            restoreMedia(context, store.loadPreMediaVolume())
            AppLog.w("dndtimetable", "媒体屏蔽：用户手动解除（本节不再自动屏蔽，下节恢复）")
        }
    }

    /**
     * [unmuteMediaForSession] 的反向：用户在课中从通知按钮重新开启「屏蔽媒体音」
     * （本节内可反复切换；下节照常自动屏蔽）。
     * - 清 mediaManualOff、置 appliedMedia → 后续事件与下课还原按"已屏蔽"处理；
     * - 立即按当前路由屏蔽媒体音，恢复音量监听（防音量键解除后不回弹）；
     * - 会话已结束则不动（防止课间误点导致媒体静音残留到没有 end() 还原的时段）。
     */
    fun remuteMediaForSession(context: Context) {
        runCatching {
            val store = DndStateStore(context)
            if (store.loadFilter() == null || store.isSessionStale()) {
                AppLog.w("dndtimetable", "媒体屏蔽：非会话中，忽略「屏蔽媒体音」")
                return
            }
            store.saveMediaManualOff(false)
            store.saveAppliedMedia(true)
            muteMedia(context, store)
            VolumeWatcher.start(context)
            AppLog.w("dndtimetable", "媒体屏蔽：用户手动恢复（本节内继续屏蔽）")
        }
    }

    /**
     * 音频设备变化（拔/插耳机）后按当前路由重断言媒体音：无耳机→屏蔽扬声器，
     * 有耳机→放行（耳机内可听）。会话中屏蔽了媒体音才处理。
     * 事件驱动 + 5 秒内加固：立即屏蔽后于 0.8s / 3s 各补打一次（防 ROM 异步重置），完毕即停——
     * 不依赖持续轮询；进程被杀时由前台服务重启 / 划后台时的一次性重断言闹钟兜底。
     */
    fun reapplyMediaMute(context: Context) {
        val store = DndStateStore(context)
        if (!store.loadAppliedMedia()) return
        if (store.isSessionStale()) return   // 跨天残留的旧会话快照：不再补打（应由下一场会话重记）
        AppLog.w("dndtimetable", "reapplyMediaMute: 按路由重断言媒体音")
        // 任何重断言路径都确保音量监听在会话内生效（进程重启/服务自愈后事件回调不丢）
        VolumeWatcher.start(context)
        muteMedia(context, store)
        CoroutineScope(Dispatchers.IO).launch {
            delay(800)
            if (store.loadAppliedMedia() && !store.isSessionStale()) muteMedia(context, store)
            delay(2200)   // ≈3s：异步重置尘埃落定后最后一次加固
            if (store.loadAppliedMedia() && !store.isSessionStale()) muteMedia(context, store)
        }
    }

    /**
     * 屏蔽媒体音（针对扬声器）：
     * - 有个人收听设备（有线/蓝牙/USB 耳机）→ **放行**：解除已生效的静音、不动音量（耳机内照常播放）；
     * - 无个人设备 → ① setStreamMute(true)（静音标记，同「手动静音按钮」的灰色状态，音量值不变）；
     *   ② 读回未生效（华为/荣耀等 ROM 不实现该标记）→ 再试系统音量面板静音同款 API `ADJUST_MUTE`；
     *   ③ 仍读回未生效 → **降音量到 0 兜底**（可用性优先），同时写「归零守卫」
     *      （跨会话保留：若下课没跑完，下次唤醒可自愈还原，防"音量回不来"）。
     * 课前音量只记录一次（会话内快照）。
     */
    private fun muteMedia(context: Context, store: DndStateStore) {
        val audio = context.getSystemService(AudioManager::class.java)
        // 诊断：当前输出设备（判断"是否误判耳机仍在"）
        val devs = runCatching {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.joinToString(",")
        }.getOrDefault("?")
        AppLog.w("dndtimetable", "muteMedia: 输出设备=$devs")
        if (hasHearingDevice(context)) {
            runCatching { audio.setStreamMute(AudioManager.STREAM_MUSIC, false) }
            // 曾降零的会话：耳机路径恢复课前音量（仅当前恰为 0 时）；音量已还原 → 清归零守卫
            val pre = store.loadPreMediaVolume()
            if (pre != null && pre > 0 && audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, pre, 0) }
                store.clearZeroGuard()
            }
            AppLog.w("dndtimetable", "媒体屏蔽：检测到耳机，放行（不静音）")
            return
        }
        runCatching { audio.setStreamMute(AudioManager.STREAM_MUSIC, true) }
        if (!MediaMuteMode.forceZeroVolume && audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
            AppLog.w("dndtimetable", "媒体屏蔽：setStreamMute 生效")
            return
        }
        // 部分 ROM（华为/荣耀等）setStreamMute 不实现：改用系统音量面板「静音按钮」同款 API。
        // 注意：HyperOS 4 上该 API 会把媒体音量清 0 而非只打静音标记（实测导致耳机也被静音），
        // 故只作为后备，不做首选。
        if (!MediaMuteMode.forceZeroVolume) {
            runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0) }
            if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
                AppLog.w("dndtimetable", "媒体屏蔽：adjustStreamVolume(ADJUST_MUTE) 生效")
                return
            }
        }
        // 降音量到 0 兜底：ROM 不支持静音标记，或用户/开发模式显式开启「兼容模式（强制降零）」
        val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (store.loadPreMediaVolume() == null) store.savePreMediaVolume(cur)
        if (cur > 0) store.saveZeroGuard(cur)   // 守卫只在"确实降了非零音量"时写，音量本就是 0 时无需自愈
        store.saveMediaZeroed(true)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        AppLog.w(
            "dndtimetable",
            if (MediaMuteMode.forceZeroVolume) "媒体屏蔽：兼容模式（强制降零）→ 音量 0（下课还原，已写归零守卫）"
            else "媒体屏蔽：本机 ROM 不支持静音标记，降音量到 0 兜底（下课还原，已写归零守卫）"
        )
    }

    /** 是否有个人收听设备（耳机/蓝牙/USB 音频/线路输出）：有则媒体音不走扬声器，应放行。 */
    private fun hasHearingDevice(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        return runCatching {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { d ->
                d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    d.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    d.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    d.type == AudioDeviceInfo.TYPE_LINE_ANALOG
            }
        }.getOrDefault(false)
    }

    /**
     * 解除媒体屏蔽：取消静音（读回校验+重试，防系统异步回滚把静音又顶回来）、回到课前音量。
     * 部分 ROM 的 setStreamMute 是异步生效：写后读回未确认则重试（最多约 1s），确认后再还原音量。
     * 课前音量有记录时一律还原——尤其 ROM 不支持静音标记而降音量到 0 的机型（防"音量回不来"）。
     */
    private fun restoreMedia(context: Context, preMediaVol: Int?) {
        val audio = context.getSystemService(AudioManager::class.java)
        for (i in 0 until 4) {
            runCatching { audio.setStreamMute(AudioManager.STREAM_MUSIC, false) }
            if (!audio.isStreamMute(AudioManager.STREAM_MUSIC)) break
            Thread.sleep(250)
        }
        preMediaVol?.let { v ->
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
            // 音量已按课前值还原 → 归零守卫使命完成（保留会让下次唤醒误判"孤儿归零"）
            runCatching { DndStateStore(context).clearZeroGuard() }
        }
        AppLog.w(
            "dndtimetable",
            "restoreMedia: mute=${audio.isStreamMute(AudioManager.STREAM_MUSIC)} vol=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} pre=$preMediaVol"
        )
    }

    /** 媒体是否仍需还原（静音标记仍在，或曾降零且音量为 0）：供下课后的延迟复核判定。 */
    private fun needsMediaRestore(context: Context, preMediaVol: Int?): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC)) return true
        return preMediaVol != null && preMediaVol > 0 && audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
    }

    /** 「下课后媒体音量」：用户指定百分比覆盖课前还原值。 */
    private suspend fun applyAfterClassVol(context: Context) {
        if (AppGraph.settings.afterClassVolEnabled.first()) {
            val pct = AppGraph.settings.afterClassVolPercent.first()
            val audio = context.getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val vol = (max * pct / 100f).roundToInt()
            runCatching {
                audio.setStreamMute(AudioManager.STREAM_MUSIC, false)
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
            }
            AppLog.w(
                "dndtimetable",
                "下课媒体音量：目标 $pct%（$vol/$max）→ 读回 mute=${audio.isStreamMute(AudioManager.STREAM_MUSIC)} vol=${audio.getStreamVolume(AudioManager.STREAM_MUSIC)}"
            )
            // 音量已交给用户指定的课后音量 → 归零守卫使命完成
            runCatching { DndStateStore(context).clearZeroGuard() }
        }
    }

    /** 是否开启「下课后媒体音量 = 指定百分比」。开启时下课后的复核只维持该音量，不回退课前音量。 */
    private suspend fun afterClassVolPercentOrNull(): Int? =
        if (AppGraph.settings.afterClassVolEnabled.first()) AppGraph.settings.afterClassVolPercent.first() else null

    suspend fun end(context: Context) {
        val dnd = DndController(context)
        val store = DndStateStore(context)
        // 一次性读出快照后立即失效会话：后续 filter 写操作触发的
        // INTERRUPTION_FILTER_CHANGED 广播不会再被 reapplyAfterDndChange 补打回去（防"关不掉"）
        val prevFilter = store.loadFilter()
        val prevRinger = store.loadRinger()
        val appliedFilter = store.loadAppliedFilter()
        val applied = store.loadApplied()
        val appliedMedia = store.loadAppliedMedia()
        val preMediaVol = store.loadPreMediaVolume()
        val prePolicy = store.loadPrePolicy()
        store.clear()
        // 关闭行为由课前状态自动判定：
        // - 课前有用户手动免打扰（filter 非正常/未知）→ 保留：还原课前（手动 DND 留着）；
        // - 课前只有手动静音/震动（无免打扰）或完全正常 → 不保留：全部关闭（DND 全关 + 铃声正常）。
        val keepPrev = prevFilter != null &&
            prevFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            prevFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN

        if (appliedFilter || applied == DndStateStore.APPLIED_BORROW_DND) {
            // 主动 filter 或「借用的用户手动 DND」：还原到课前 filter（保留）或清空（不保留，含手动 DND）
            if (keepPrev && prevFilter != null) dnd.setFilter(prevFilter) else dnd.clear()
        }
        delay(350)   // 等 DND 关闭的异步回滚尘埃落定，再还原铃声（避免被覆盖）
        // 竞态防护：结束尚未完成时用户又按了「打开」（新会话快照已写入）→ 放弃"会被新会话接管"的还原，
        // 否则会把自己刚打开的新会话关掉（表现为"打开后掉了/没打开成"）。
        // 但**全局 DND 策略**与**媒体屏蔽**必须处理：
        // - 策略是我们改的全局设置，残留会静默污染用户以后的手动勿扰；
        // - 媒体若新会话未接管，残留静音/0 音量会一直卡到下一节课。
        val newSession = store.loadFilter() != null && !store.isSessionStale()
        if (newSession) {
            AppLog.w("dndtimetable", "end: 检出新会话，跳过铃声/免打扰还原（由新会话接管）")
            dnd.restorePolicy3(prePolicy)
            runCatching { store.clearPolicyGuard() }
            if (decideEndMediaRestore(appliedMedia, store.loadAppliedMedia())) {
                AppLog.w("dndtimetable", "end: 新会话未接管媒体屏蔽 → 仍还原媒体")
                restoreMedia(context, preMediaVol)
            }
            return   // 不应用课后音量、不停保活服务（新会话需要它）
        }
        if (!keepPrev) {
            // 不保留：全部关闭 → 免打扰全关（含用户手动开的，clear 带读回重试）+ 铃声恢复正常
            dnd.clear()
            dnd.setRingerModeLocal(AudioManager.RINGER_MODE_NORMAL)
        } else when (decideEndRinger(applied, appliedFilter)) {
            EndRingerAction.RESTORE_PREV_RINGER -> if (prevRinger != null) dnd.setRingerModeLocal(prevRinger)
            EndRingerAction.CLEAR_DND -> dnd.clear()   // 铃声降级成勿扰（课前无用户 DND）时清；勿扰是主动勾选时上面已还原
            EndRingerAction.NOTHING -> {}              // 本次未动/借用：绝不清用户手动开的免打扰
        }
        if (appliedMedia) {
            restoreMedia(context, preMediaVol)
            // 延迟复核：DND 关闭的异步状态机（部分 ROM >1s）可能把刚解除的媒体静音又顶回去
            //（真实下课点正是回滚活动窗口；模拟场景隔了几秒、状态早已稳定——这是两者差异所在）。
            // 3 秒内分多轮复核（无新会话才补还原，新课会话由它自己管理），确保最终解除。
            // 注意：若开启「课后指定音量」，复核必须维持该音量，**绝不能回退课前音量**（否则会把
            // 用户设置的指定音量覆盖掉——部分 ROM（如 iQOO）setStreamMute 谎报成功时必然触发）。
            val afterPct = afterClassVolPercentOrNull()
            CoroutineScope(Dispatchers.IO).launch {
                for (round in 1..4) {
                    delay(700L * round)
                    if (store.loadFilter() != null || DndStateStore(context).loadAppliedMedia()) return@launch
                    val audio = context.getSystemService(AudioManager::class.java)
                    if (afterPct != null) {                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val want = (max * afterPct / 100f).roundToInt()
                        if (audio.isStreamMute(AudioManager.STREAM_MUSIC) || audio.getStreamVolume(AudioManager.STREAM_MUSIC) != want) {
                            AppLog.w("dndtimetable", "end: 维持课后指定音量（第${round}轮）$afterPct% → $want")
                            runCatching {
                                audio.setStreamMute(AudioManager.STREAM_MUSIC, false)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, want, 0)
                            }
                        } else {
                            break   // 已是目标音量且未静音：稳定
                        }
                    } else if (needsMediaRestore(context, preMediaVol)) {
                        AppLog.w("dndtimetable", "end: 媒体屏蔽被系统回滚（第${round}轮），补还原")
                        restoreMedia(context, preMediaVol)
                    } else {
                        break   // 已解除且稳定：停止复核
                    }
                }
            }
        }
        // 我们在勿扰模式下改过全局 DND 策略 → 一律还原（否则影响用户以后手动开勿扰），
        // 成功后清除跨会话策略守卫（守卫使命完成）
        dnd.restorePolicy3(prePolicy)
        runCatching { store.clearPolicyGuard() }
        applyAfterClassVol(context)
        // 下课：停止音量监听与前台保活服务（会话已结束）
        VolumeWatcher.stop()
        MediaKeepalive.stop(context)
    }

    /** 把铃声设为 SILENT：本地 AudioManager（多次读回重试）→ 已有用户手动免打扰则借用 →（可选）免打扰兜底。返回实际采用的机制。 */
    private fun applyRingerSilent(dnd: DndController, allowDndFallback: Boolean): Int {
        if (dnd.setRingerModeLocal(AudioManager.RINGER_MODE_SILENT)) {
            AppLog.w("dndtimetable", "铃声静音：本地 AudioManager 生效")
            return DndStateStore.APPLIED_LOCAL_RINGER
        }
        // 本地铃声写入失败（部分 ROM 在 DND 状态下主导铃声模式），但已有用户手动免打扰生效：
        // 手机本就静默，借用它——绝不 setSilentAll() 覆盖用户配置（否则下课时还要帮用户清 DND）。
        if (dnd.isSilentNow()) {
            AppLog.w("dndtimetable", "铃声写入失败，借用户手动免打扰（不覆盖其配置）")
            return DndStateStore.APPLIED_BORROW_DND
        }
        if (allowDndFallback) {
            AppLog.w("dndtimetable", "铃声静音失败，降级为免打扰")
            dnd.setSilentAll()
        }
        return DndStateStore.APPLIED_DND
    }

    /**
     * 免打扰过滤级别变化后（用户手动开/关 DND）系统可能重置媒体静音、还原铃声：
     * 会话激活时按已应用的机制补打（媒体立即，铃声延迟补一次防异步覆盖）。
     * 由 DndChangedReceiver（ACTION_INTERRUPTION_FILTER_CHANGED）触发。
     */
    fun reapplyAfterDndChange(context: Context) {
        val store = DndStateStore(context)
        if (store.loadFilter() == null || store.isSessionStale()) return
        if (store.loadAppliedMedia()) {
            AppLog.w("dndtimetable", "DND 变化：补打媒体静音")
            muteMedia(context, store)
        }
        if (store.loadApplied() == DndStateStore.APPLIED_LOCAL_RINGER) {
            CoroutineScope(Dispatchers.IO).launch {
                delay(500)
                if (store.loadApplied() == DndStateStore.APPLIED_LOCAL_RINGER) {
                    AppLog.w("dndtimetable", "DND 变化：补打铃声静音")
                    DndController(context).setRingerModeLocal(AudioManager.RINGER_MODE_SILENT)
                }
            }
        }
    }
}
