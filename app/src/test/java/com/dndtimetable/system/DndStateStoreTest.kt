package com.dndtimetable.system

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 会话快照陈旧判断（纯逻辑）测试。
 *
 * 背景：快照无日期标记时，强停 App / 关机错过下课事件后，次日上课仍按上一会话的
 * 课前状态（铃声/免打扰）还原。修复：快照带真实墙钟时间戳，跨天即视为新会话重记。
 */
class DndStateStoreTest {

    private val zone = ZoneId.systemDefault()
    private fun ms(epochDay: Long, hour: Int): Long =
        LocalDate.ofEpochDay(epochDay).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `无快照视为陈旧`() {
        assertTrue(isSessionStale(0L, ms(LocalDate.now().toEpochDay(), 8)))
    }

    @Test
    fun `跨天遗留的上一会话快照视为陈旧`() {
        // 典型场景：昨晚 23:00 上课链未正常 end（关机/强停），今早 8:00 新会话启动
        val today = LocalDate.now().toEpochDay()
        assertTrue(isSessionStale(ms(today - 1, 23), ms(today, 8)))
    }

    @Test
    fun `同天超长课不视为陈旧`() {
        // 上课链不会跨午夜：同天任意时长的课（含 12 小时连上）快照均有效
        val today = LocalDate.now().toEpochDay()
        assertFalse(isSessionStale(ms(today, 8), ms(today, 21)))
    }
}
