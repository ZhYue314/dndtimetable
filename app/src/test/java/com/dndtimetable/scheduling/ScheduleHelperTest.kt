package com.dndtimetable.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知事件 key（进程内单调递增）测试。
 *
 * 背景：旧实现 key = timeMillis.hashCode() and 31，两个不同时刻哈希低位相同会被
 * FLAG_UPDATE_CURRENT 顶掉（提醒丢失）。修复：每次重排先全量 cancel，同轮内 key 递增互不冲突。
 */
class ScheduleHelperTest {

    @Test
    fun `一次重排内通知key互不冲突`() {
        val keys = (0 until 32).map { ScheduleHelper.nextNoticeKey() }
        assertEquals(32, keys.toSet().size)
    }

    @Test
    fun `key始终落在0到31槽位内`() {
        repeat(64) { assertTrue(ScheduleHelper.nextNoticeKey() in 0..31) }
    }

    @Test
    fun `切表后窗口外的残留会话应收尾`() {
        // 旧课表（id=2）的会话残留，当前活动课表 id=1 且未来仍有事件 → 必须收尾
        assertTrue(shouldEndOrphanSession(true, 2L, 1L, nextEventExists = true))
    }

    @Test
    fun `同课表的手动会话在窗口外保留`() {
        assertFalse(shouldEndOrphanSession(true, 1L, 1L, nextEventExists = true))
    }

    @Test
    fun `无后续事件的残留会话仍收尾`() {
        assertTrue(shouldEndOrphanSession(true, 1L, 1L, nextEventExists = false))
    }

    @Test
    fun `无会话不收尾`() {
        assertFalse(shouldEndOrphanSession(false, null, 1L, nextEventExists = false))
        // 旧版本快照（无课表 id）按同课表保守处理
        assertFalse(shouldEndOrphanSession(true, null, 1L, nextEventExists = true))
    }
}
