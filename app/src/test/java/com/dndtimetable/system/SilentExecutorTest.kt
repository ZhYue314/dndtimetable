package com.dndtimetable.system

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 下课还原决策（decideEndRinger）纯逻辑测试。
 *
 * 回归背景（已修）：旧实现在 start() 只对「勾选静音」写 saveApplied，
 * 仅媒体/全未勾选时 loadApplied() 返回默认 APPLIED_DND，end() 走
 * `APPLIED_DND && !appliedFilter -> dnd.clear()`，把用户手动开的免打扰关掉。
 */
class SilentExecutorTest {

    @Test
    fun `本次未动铃声免打扰时下课不做任何事`() {
        assertEquals(EndRingerAction.NOTHING, decideEndRinger(DndStateStore.APPLIED_NONE, false))
        assertEquals(EndRingerAction.NOTHING, decideEndRinger(DndStateStore.APPLIED_NONE, true))
    }

    @Test
    fun `仅媒体维度配置下课不清用户手动免打扰`() {
        // 仅媒体：start() 会显式写入 APPLIED_NONE → 下课不得 clear
        assertEquals(EndRingerAction.NOTHING, decideEndRinger(DndStateStore.APPLIED_NONE, false))
    }

    @Test
    fun `主动勾选filter时下课由restoreFilter还原不走clear`() {
        // restoreFilter 本体需 Context 无法在 JVM 单测；此处锁定不再误走 clear 分支
        assertEquals(EndRingerAction.NOTHING, decideEndRinger(DndStateStore.APPLIED_DND, true))
    }

    @Test
    fun `本地铃声静音下课还原课前铃声`() {
        assertEquals(EndRingerAction.RESTORE_PREV_RINGER, decideEndRinger(DndStateStore.APPLIED_LOCAL_RINGER, false))
        // 即使同时勾了 filter（filter 由 restoreFilter 处理），铃声仍要还原
        assertEquals(EndRingerAction.RESTORE_PREV_RINGER, decideEndRinger(DndStateStore.APPLIED_LOCAL_RINGER, true))
    }

    @Test
    fun `铃声降级为免打扰且未主动勾选filter时下课关闭免打扰`() {
        // 该分支仅在「课前无用户手动 DND」时命中（有则走借用路径）
        assertEquals(EndRingerAction.CLEAR_DND, decideEndRinger(DndStateStore.APPLIED_DND, false))
    }

    @Test
    fun `借用户手动免打扰时下课不清理用户的DND`() {
        // 铃声写入失败但已有用户手动 DND：借用 → filter 由 restoreFilter 按课前状态还原，绝不清空
        assertEquals(EndRingerAction.NOTHING, decideEndRinger(DndStateStore.APPLIED_BORROW_DND, false))
        assertEquals(EndRingerAction.NOTHING, decideEndRinger(DndStateStore.APPLIED_BORROW_DND, true))
    }

    @Test
    fun `持久化编码约定不被改动`() {
        // SharedPreferences 里的 int 是持久化契约，改动会导致已安装用户还原行为跳变
        assertEquals(0, DndStateStore.APPLIED_LOCAL_RINGER)
        assertEquals(1, DndStateStore.APPLIED_NONE)
        assertEquals(2, DndStateStore.APPLIED_DND)
        assertEquals(3, DndStateStore.APPLIED_BORROW_DND)
    }

    // --- 孤儿归零自愈（shouldHealZeroedVolume）：条件必须保守，宁可漏救绝不擅改用户音量 ---

    @Test
    fun `守卫有效且音量仍为零时自愈`() {
        assertTrue(shouldHealZeroedVolume(guardActive = true, guardPreVol = 10, sessionActive = false, currentVolume = 0, guardAgeMs = 1000))
    }

    @Test
    fun `会话进行中绝不自愈`() {
        // 课中音量 0 是"正在屏蔽"的正常状态
        assertFalse(shouldHealZeroedVolume(guardActive = true, guardPreVol = 10, sessionActive = true, currentVolume = 0, guardAgeMs = 1000))
    }

    @Test
    fun `音量已被改过时不动（用户手动调整优先）`() {
        assertFalse(shouldHealZeroedVolume(guardActive = true, guardPreVol = 10, sessionActive = false, currentVolume = 5, guardAgeMs = 1000))
        assertFalse(shouldHealZeroedVolume(guardActive = true, guardPreVol = 10, sessionActive = false, currentVolume = 15, guardAgeMs = 1000))
    }

    @Test
    fun `无守卫或无有效课前音量时不动作`() {
        assertFalse(shouldHealZeroedVolume(guardActive = false, guardPreVol = 10, sessionActive = false, currentVolume = 0, guardAgeMs = 1000))
        assertFalse(shouldHealZeroedVolume(guardActive = true, guardPreVol = null, sessionActive = false, currentVolume = 0, guardAgeMs = 1000))
        // 课前音量本就是 0（用户课前静音）：还原成 0 没有意义，也不该动
        assertFalse(shouldHealZeroedVolume(guardActive = true, guardPreVol = 0, sessionActive = false, currentVolume = 0, guardAgeMs = 1000))
    }

    @Test
    fun `守卫过期后不再自愈（防很久以后误改音量）`() {
        assertFalse(
            shouldHealZeroedVolume(
                guardActive = true, guardPreVol = 10, sessionActive = false, currentVolume = 0,
                guardAgeMs = ZERO_GUARD_WINDOW_MS + 1
            )
        )
        // 边界：恰好在窗口内仍自愈
        assertTrue(
            shouldHealZeroedVolume(
                guardActive = true, guardPreVol = 10, sessionActive = false, currentVolume = 0,
                guardAgeMs = ZERO_GUARD_WINDOW_MS
            )
        )
        // 时间戳异常（未来时间：时钟被改）视为不可信 → 不自愈
        assertFalse(shouldHealZeroedVolume(guardActive = true, guardPreVol = 10, sessionActive = false, currentVolume = 0, guardAgeMs = -50_000))
    }

    // --- 残留 DND 策略识别（isOurDndPolicySignature）：命中即认为是我们写入的 ---

    @Test
    fun `识别我们写入的仅放行媒体策略`() {
        assertTrue(
            isOurDndPolicySignature(
                NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, 0, 0
            )
        )
    }

    @Test
    fun `不误判系统默认或用户配置的策略`() {
        // 全放行（默认）
        assertFalse(isOurDndPolicySignature(0, 0, 0))
        // 来电+消息（用户可在系统 UI 配置出的组合；发送者取值 1=仅联系人，避免常量名跨版本差异）
        assertFalse(
            isOurDndPolicySignature(
                NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES,
                1, 1
            )
        )
        // 媒体 + 其它类别（不是我们的纯特征值）
        assertFalse(
            isOurDndPolicySignature(
                NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS, 0, 0
            )
        )
        // 媒体类别但发送者非 0（我们写入的恒为 0）
        assertFalse(isOurDndPolicySignature(NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, 1, 0))
    }

    // --- 下课竞态时的媒体还原决策（decideEndMediaRestore）---

    @Test
    fun `新会话接管媒体时下课竞态不再还原媒体`() {
        assertFalse(decideEndMediaRestore(appliedMedia = true, newSessionMediaActive = true))
    }

    @Test
    fun `新会话未接管媒体时下课竞态仍还原媒体`() {
        assertTrue(decideEndMediaRestore(appliedMedia = true, newSessionMediaActive = false))
    }

    @Test
    fun `本次未屏蔽媒体则无需还原`() {
        assertFalse(decideEndMediaRestore(appliedMedia = false, newSessionMediaActive = false))
        assertFalse(decideEndMediaRestore(appliedMedia = false, newSessionMediaActive = true))
    }

    // --- 手动关闭抑制（isManualSuppressed）：用户课中关掉后，本节不再自动拉起静音 ---

    @Test
    fun `未设置或已过期时不抑制`() {
        assertFalse(isManualSuppressed(untilMs = 0L, nowMs = 1_000L))
        assertFalse(isManualSuppressed(untilMs = 1_000L, nowMs = 1_000L))   // 到点即失效
        assertFalse(isManualSuppressed(untilMs = 1_000L, nowMs = 2_000L))
    }

    @Test
    fun `抑制期内不自动拉起`() {
        assertTrue(isManualSuppressed(untilMs = 10_000L, nowMs = 1_000L))
        assertTrue(isManualSuppressed(untilMs = 10_000L, nowMs = 9_999L))
    }
}
