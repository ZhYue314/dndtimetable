package com.dndtimetable.domain

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.SpecialDateType
import com.dndtimetable.data.db.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 调休补课日提醒（头天 20:00）。
 * 固定场景：学期 2026-09-07（周一）起 20 周；每周二第 1-2 节有课；
 * now = 2026-09-23 10:00（周三）。
 */
class MakeupNoticeTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val semester = Semester(id = 1, startEpochDay = LocalDate.of(2026, 9, 7).toEpochDay(), totalWeeks = 20)
    private val tueCourse = Course(
        id = 1, name = "高数", weekday = 2, startPeriod = 1, endPeriod = 2,
        startWeek = 1, endWeek = 20, weekType = WeekType.ALL
    )
    private val now = LocalDate.of(2026, 9, 23).atTime(10, 0).atZone(zone).toInstant()

    private fun makeup(day: Int, sourceDay: Int, month: Int = 9, sourceMonth: Int = 9, enabled: Boolean = true) = SpecialDate(
        id = 99, epochDay = LocalDate.of(2026, month, day).toEpochDay(),
        endEpochDay = LocalDate.of(2026, month, day).toEpochDay(),
        type = SpecialDateType.MAKEUP,
        sourceEpochDay = LocalDate.of(2026, sourceMonth, sourceDay).toEpochDay(),
        builtin = true, name = "测试调休", enabled = enabled
    )

    @Test
    fun `找到下一个补课日且提醒时刻为前一天20点`() {
        // 9/27（周日）补 9/22（周二）的课；提醒 = 9/26 20:00
        val n = ScheduleEngine.nextMakeupNotice(semester, listOf(tueCourse), listOf(makeup(27, 22)), zone, now)!!
        assertEquals(LocalDate.of(2026, 9, 27).toEpochDay(), n.dateEpoch)
        assertEquals(
            LocalDate.of(2026, 9, 26).atTime(20, 0).atZone(zone).toInstant().toEpochMilli(),
            n.remindMs
        )
        assertTrue(n.remindMs > now.toEpochMilli())
    }

    @Test
    fun `提醒时刻已过的补课日跳过`() {
        // 今天（9/23）补课 → 提醒在昨天 20:00，已过 → 无
        val n = ScheduleEngine.nextMakeupNotice(semester, listOf(tueCourse), listOf(makeup(23, 22)), zone, now)
        assertNull(n)
    }

    @Test
    fun `disabled 的补课日跳过`() {
        val n = ScheduleEngine.nextMakeupNotice(semester, listOf(tueCourse), listOf(makeup(27, 22, enabled = false)), zone, now)
        assertNull(n)
    }

    @Test
    fun `补课日按来源日没课则不提醒`() {
        // 来源 9/28 是周一（weekday=1），本测试只有周二有课 → 那天没课 → 不提醒
        val n = ScheduleEngine.nextMakeupNotice(semester, listOf(tueCourse), listOf(makeup(27, 28)), zone, now)
        assertNull(n)
    }

    @Test
    fun `多条取最近的一条`() {
        val later = makeup(10, 6, month = 10, sourceMonth = 10)   // 10/10（周六）补 10/6（周二）的课，提醒 10/9
        val sooner = makeup(27, 22)                                // 9/27 补 9/22，提醒 9/26
        val n = ScheduleEngine.nextMakeupNotice(semester, listOf(tueCourse), listOf(later, sooner), zone, now)!!
        assertEquals(LocalDate.of(2026, 9, 27).toEpochDay(), n.dateEpoch)
    }

    @Test
    fun `文案带补课日与来源日`() {
        val n = ScheduleEngine.nextMakeupNotice(semester, listOf(tueCourse), listOf(makeup(27, 22)), zone, now)!!
        val text = ScheduleEngine.makeupNoticeText(n)
        assertTrue(text.contains("调休补课日"))
        assertTrue(text.contains("9/27"))
        assertTrue(text.contains("9/22"))
    }

    @Test
    fun `未设置结束日期的补课日必提醒（即使那天没课）`() {
        // 9/27 周日无课，但未设置结束日期 → 仍提醒补全
        val sp = SpecialDate(
            epochDay = LocalDate.of(2026, 9, 27).toEpochDay(),
            endEpochDay = LocalDate.of(2026, 9, 27).toEpochDay(),
            type = SpecialDateType.MAKEUP, sourceEpochDay = null, enabled = true
        )
        val n = ScheduleEngine.nextMakeupNotice(semester, listOf(tueCourse), listOf(sp), zone, now)!!
        assertEquals(LocalDate.of(2026, 9, 27).toEpochDay(), n.dateEpoch)
        assertTrue(ScheduleEngine.makeupNoticeText(n).contains("未设置结束日期"))
    }

    @Test
    fun `无来源日的旧式补课文案不含课表指引`() {
        val sp = SpecialDate(
            epochDay = LocalDate.of(2026, 9, 27).toEpochDay(), type = SpecialDateType.MAKEUP,
            courseId = 1, enabled = true
        )
        val n = ScheduleEngine.nextMakeupNotice(semester, listOf(tueCourse), listOf(sp), zone, now)!!
        val text = ScheduleEngine.makeupNoticeText(n)
        assertTrue(text.contains("调休补课日"))
        assertTrue(!text.contains("课表上课"))
    }
}
