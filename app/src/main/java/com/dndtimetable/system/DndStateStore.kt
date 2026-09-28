package com.dndtimetable.system

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/**
 * 判断会话快照是否陈旧（纯逻辑，可 JVM 单测）。
 * 规则：快照日期早于「现在」的日期 → 陈旧。上课链不会跨午夜，
 * 因此跨天必然是新会话；同天任意时长的课（含 12 小时连上）都视为有效。
 * 用于兜底「强停 App / 关机错过下课事件」后残留的过期快照。
 * 注意：这里用真实墙钟——快照代表的是系统真实状态，不随开发者模拟时间偏移。
 */
internal fun isSessionStale(tsMs: Long, nowMs: Long): Boolean {
    if (tsMs <= 0L) return true
    val zone = ZoneId.systemDefault()
    val tsDay = Instant.ofEpochMilli(tsMs).atZone(zone).toLocalDate().toEpochDay()
    val nowDay = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()
    return tsDay < nowDay
}

/** 记录「上课前」的免打扰过滤级别与铃声模式，用于下课后「回到上课前状态」。 */
class DndStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("dnd_state", Context.MODE_PRIVATE)

    fun savePreStart(filter: Int, ringer: Int) {
        prefs.edit()
            .putInt(KEY_FILTER, filter)
            .putInt(KEY_RINGER, ringer)
            .putLong(KEY_SESSION_TS, System.currentTimeMillis())
            .apply()
    }

    fun loadFilter(): Int? = if (prefs.contains(KEY_FILTER)) prefs.getInt(KEY_FILTER, 3) else null

    fun loadRinger(): Int? = if (prefs.contains(KEY_RINGER)) prefs.getInt(KEY_RINGER, 2) else null

    /** 会话所属课表 id：切表后据此收尾旧会话（否则旧课表的免打扰/通知一直挂着）。 */
    fun saveSessionScheduleId(id: Long) = prefs.edit().putLong(KEY_SESSION_SCHEDULE, id).apply()

    fun loadSessionScheduleId(): Long? =
        if (prefs.contains(KEY_SESSION_SCHEDULE)) prefs.getLong(KEY_SESSION_SCHEDULE, -1L) else null

    /** 快照是否陈旧（无快照/跨天遗留的上一会话快照）：陈旧时下一次 start 应重记课前状态。 */
    fun isSessionStale(): Boolean = isSessionStale(prefs.getLong(KEY_SESSION_TS, 0L), System.currentTimeMillis())

    /** 课前 DND 策略（勿扰模式用 PRIORITY+空策略时会覆盖全局策略，课后还原）。 */
    fun savePrePolicy(t: Triple<Int, Int, Int>?) {
        if (t == null) return
        prefs.edit().putString(KEY_POLICY, "${t.first},${t.second},${t.third}").apply()
    }

    fun loadPrePolicy(): Triple<Int, Int, Int>? {
        val s = prefs.getString(KEY_POLICY, null) ?: return null
        return runCatching {
            val a = s.split(",").map { it.toInt() }
            Triple(a[0], a[1], a[2])
        }.getOrNull()
    }

    /** 课前媒体音量（静音+屏蔽媒体模式用）。 */
    fun savePreMediaVolume(v: Int) = prefs.edit().putInt(KEY_MEDIA, v).apply()

    fun loadPreMediaVolume(): Int? = if (prefs.contains(KEY_MEDIA)) prefs.getInt(KEY_MEDIA, -1) else null

    /**
     * 本次会话是否因「ROM 不支持静音标记」而降音量到 0 兜底过。
     * 置位后下课时必须还原课前音量（历史 bug：音量回不来），并纳入下课后的延迟复核。
     */
    fun saveMediaZeroed(b: Boolean) = prefs.edit().putBoolean(KEY_MEDIA_ZEROED, b).apply()

    fun loadMediaZeroed(): Boolean = prefs.getBoolean(KEY_MEDIA_ZEROED, false)

    /** 上课开启时实际应用的静音机制（决定下课时如何还原）。 */
    fun saveApplied(v: Int) = prefs.edit().putInt(KEY_APPLIED, v).apply()

    /** 默认 NONE：本次会话未对铃声/免打扰做任何动作时，下课绝不清用户手动开的免打扰。 */
    fun loadApplied(): Int = prefs.getInt(KEY_APPLIED, APPLIED_NONE)

    /** 本次会话是否屏蔽了媒体音 / 应用了免打扰（组合模式：三者独立还原）。 */
    fun saveAppliedMedia(b: Boolean) = prefs.edit().putBoolean(KEY_APPLIED_MEDIA, b).apply()

    fun loadAppliedMedia(): Boolean = prefs.getBoolean(KEY_APPLIED_MEDIA, false)

    fun saveAppliedFilter(b: Boolean) = prefs.edit().putBoolean(KEY_APPLIED_FILTER, b).apply()

    fun loadAppliedFilter(): Boolean = prefs.getBoolean(KEY_APPLIED_FILTER, false)

    /**
     * 归零守卫（**跨会话保留**，不随 [clear] 清除）：
     * 记录「我们曾把媒体音量降零」这件事与降零前的音量，用于两次会话之间自愈
     * ——若某次 end() 未跑完（进程被杀/强停/闹钟丢失），音量会卡在 0，下次任何唤醒可据此还原，
     * 避免"0 被当成下一节课的课前音量"从而永久回不来（历史 bug）。
     */
    fun saveZeroGuard(preVol: Int) = prefs.edit()
        .putInt(KEY_GUARD_PRE_VOL, preVol)
        .putBoolean(KEY_GUARD_ACTIVE, true)
        .putLong(KEY_GUARD_TS, System.currentTimeMillis())
        .apply()

    fun clearZeroGuard() = prefs.edit()
        .remove(KEY_GUARD_ACTIVE).remove(KEY_GUARD_PRE_VOL).remove(KEY_GUARD_TS)
        .apply()

    fun loadZeroGuardActive(): Boolean = prefs.getBoolean(KEY_GUARD_ACTIVE, false)

    fun loadZeroGuardPreVol(): Int? = if (prefs.contains(KEY_GUARD_PRE_VOL)) prefs.getInt(KEY_GUARD_PRE_VOL, -1) else null

    fun loadZeroGuardTs(): Long = prefs.getLong(KEY_GUARD_TS, 0L)

    /**
     * DND 策略守卫（**跨会话保留**）：记录"我们为免打扰模式改策略前"的全局策略原值。
     * 场景：end() 未跑完（进程被杀/强停）→ 全局策略残留成"仅放行媒体"，用户以后手动开勿扰
     * 会觉得来电/通知不响、媒体却响（静默污染系统设置）。有了守卫，下次唤醒可据原值还原。
     */
    fun savePolicyGuard(p: Triple<Int, Int, Int>) = prefs.edit()
        .putString(KEY_POLICY_GUARD, "${p.first},${p.second},${p.third}")
        .putLong(KEY_POLICY_GUARD_TS, System.currentTimeMillis())
        .apply()

    fun loadPolicyGuard(): Triple<Int, Int, Int>? {
        val s = prefs.getString(KEY_POLICY_GUARD, null) ?: return null
        return runCatching {
            val a = s.split(",").map { it.toInt() }
            Triple(a[0], a[1], a[2])
        }.getOrNull()
    }

    fun loadPolicyGuardTs(): Long = prefs.getLong(KEY_POLICY_GUARD_TS, 0L)

    fun clearPolicyGuard() = prefs.edit()
        .remove(KEY_POLICY_GUARD).remove(KEY_POLICY_GUARD_TS)
        .apply()

    /**
     * 手动关闭抑制（**跨 clear 保留**）：用户在课中主动按首页按钮关闭后，本节剩余的自动静音不再被拉起
     * （抑制到 untilMs，通常是当前静音窗口结束；下一节恢复自动）。
     * 必须跨 clear 保留：否则 end() 的 clear() 立刻清掉它，scheduleNext 又 start() → "关了又自己开"。
     */
    fun saveManualSuppressUntil(untilMs: Long) = prefs.edit().putLong(KEY_MANUAL_SUPPRESS_UNTIL, untilMs).apply()

    fun loadManualSuppressUntil(): Long = prefs.getLong(KEY_MANUAL_SUPPRESS_UNTIL, 0L)

    fun clearManualSuppress() = prefs.edit().remove(KEY_MANUAL_SUPPRESS_UNTIL).apply()

    /** 会话内：媒体屏蔽已被用户从通知按钮手动解除（随 clear 清除 → 下节课恢复自动屏蔽）。 */
    fun saveMediaManualOff(b: Boolean) = prefs.edit().putBoolean(KEY_MEDIA_MANUAL_OFF, b).apply()

    fun loadMediaManualOff(): Boolean = prefs.getBoolean(KEY_MEDIA_MANUAL_OFF, false)

    /** 只清会话快照，**保留归零守卫/策略守卫/手动关闭抑制**（跨会话或跨 end() 有效）。 */
    fun clear() {
        prefs.edit()
            .remove(KEY_FILTER).remove(KEY_RINGER).remove(KEY_MEDIA).remove(KEY_MEDIA_ZEROED)
            .remove(KEY_POLICY).remove(KEY_APPLIED).remove(KEY_APPLIED_MEDIA)
            .remove(KEY_APPLIED_FILTER).remove(KEY_SESSION_TS).remove(KEY_MEDIA_MANUAL_OFF)
            .remove(KEY_SESSION_SCHEDULE)
            .apply()
    }

    companion object {
        private const val KEY_FILTER = "pre_start_filter"
        private const val KEY_RINGER = "pre_start_ringer"
        private const val KEY_MEDIA = "pre_start_media"
        private const val KEY_MEDIA_ZEROED = "media_zeroed"
        private const val KEY_POLICY = "pre_start_policy"
        private const val KEY_APPLIED = "applied"
        private const val KEY_APPLIED_MEDIA = "applied_media"
        private const val KEY_APPLIED_FILTER = "applied_filter"
        private const val KEY_SESSION_TS = "session_ts"
        private const val KEY_SESSION_SCHEDULE = "session_schedule"
        private const val KEY_GUARD_ACTIVE = "zero_guard_active"
        private const val KEY_GUARD_PRE_VOL = "zero_guard_pre_vol"
        private const val KEY_GUARD_TS = "zero_guard_ts"
        private const val KEY_POLICY_GUARD = "policy_guard"
        private const val KEY_POLICY_GUARD_TS = "policy_guard_ts"
        private const val KEY_MANUAL_SUPPRESS_UNTIL = "manual_suppress_until"
        private const val KEY_MEDIA_MANUAL_OFF = "media_manual_off"
        const val APPLIED_LOCAL_RINGER = 0   // 本地 AudioManager 改的铃声
        const val APPLIED_NONE = 1            // 本次会话未动铃声/免打扰（如仅媒体或全未勾选）
        const val APPLIED_DND = 2             // 只设置了免打扰（含铃声降级为全静默）
        const val APPLIED_BORROW_DND = 3      // 铃声写入失败但已有用户手动免打扰生效：借用，不覆盖用户配置
    }
}

