package com.dndtimetable.widget

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 小组件条目标签（courseTagLabel）纯逻辑测试。
 *
 * 回归背景（已修 bug）：第二区取"从明天起第一个有课日"（最多 7 天），周五时周六无课会取到周日，
 * 而标签此前写死「明日」→ 出现"周日的课标着明日"。
 */
class WidgetLabelsTest {

    private val sunday = LocalDate.of(2026, 9, 13)

    @Test
    fun `明天与后天`() {
        assertEquals("明日", courseTagLabel(dayOffset = 1, isPast = false, date = sunday))
        assertEquals("后天", courseTagLabel(dayOffset = 2, isPast = false, date = sunday))
    }

    @Test
    fun `三天及以上显示具体日期`() {
        assertEquals("9.13", courseTagLabel(dayOffset = 3, isPast = false, date = sunday))
        assertEquals("9.13", courseTagLabel(dayOffset = 7, isPast = false, date = sunday))
    }

    @Test
    fun `三日以上缺日期时不误标明日`() {
        assertEquals("未排课", courseTagLabel(dayOffset = 3, isPast = false, date = null))
    }

    @Test
    fun `今天已上完标已上未上不给标签`() {
        assertEquals("已上", courseTagLabel(dayOffset = 0, isPast = true, date = null))
        assertNull(courseTagLabel(dayOffset = 0, isPast = false, date = null))
    }

    @Test
    fun `第二区即使是明天也不受isPast影响`() {
        // 第二区条目恒为 isPast=false；即便传入 true，天差标签优先（不会串成「已上」）
        assertEquals("明日", courseTagLabel(dayOffset = 1, isPast = true, date = sunday))
    }
}
