package com.dndtimetable.domain

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.prefs.PeriodRule
import com.dndtimetable.data.prefs.PeriodTable
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)
        .minusDays(LocalDate.of(2026, 9, 7).dayOfWeek.value.toLong() - 1)
    private val semester = Semester(1, "test", startEpochDay = monday.toEpochDay(), totalWeeks = 16)
    private val weeks: List<com.dndtimetable.data.prefs.Period> = PeriodTable.DEFAULT
    private val rule = PeriodRule(45, 10, 20)
    private val c1 = Course(name = "高数", weekday = 1, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 16)
    private val c2 = Course(name = "英语", weekday = 1, startPeriod = 3, endPeriod = 4, startWeek = 1, endWeek = 16)
    private val c3 = Course(name = "物理", weekday = 1, startPeriod = 5, endPeriod = 6, startWeek = 1, endWeek = 16)
    private fun at(hour: Int, min: Int): java.time.Instant = monday.atTime(hour, min).atZone(zone).toInstant()
    private fun st(hour: Int, min: Int, lead: Int = 20, courses: List<Course> = listOf(c1, c2)) =
        computeStatus(semester, courses, emptyList(), weeks, zone, at(hour, min), 0, 0, lead, rule)

    @Test
    fun `上课中显示当前课程`() {
        val s = st(8, 20)
        assertTrue(s.silentNow)
        assertEquals("高数", s.currentCourse)
        assertFalse(s.inBreak)
        assertEquals(NoticePhase.IN_CLASS, s.phase)
    }

    @Test
    fun `大节内两节课之间为小课间`() {
        // 8:45-8:55 小课间：不该再显示"上课中"，也不该是课前提醒
        val s = st(8, 50)
        assertNull(s.currentCourse)
        assertTrue(s.inBreak)
        assertTrue(s.smallBreak)
        assertEquals(NoticePhase.SMALL_BREAK, s.phase)
        assertEquals("08:55", s.nextStartClock)   // 下一节是第2节，不是整门课的开始 08:00
    }

    @Test
    fun `课程结束后下一节开始前为大课间`() {
        // 1-2节 8:00-9:40 / 3-4节 10:00-11:40；9:50 处于大课间
        val s = st(9, 50)
        assertTrue(s.inBreak)
        assertFalse(s.smallBreak)  // 20min 间隔 > 小课间 10min = 大课间
        assertEquals("英语", s.nextCourseName)
        assertEquals(NoticePhase.BIG_BREAK, s.phase)
    }

    @Test
    fun `下午首节无课前提醒`() {
        // 只有下午 5-6节（14:00-15:40）：13:40 起为课前阶段
        val s = st(13, 40, courses = listOf(c3))
        assertEquals(NoticePhase.PRE, s.phase)
        assertEquals("物理", s.nextCourseName)
        assertEquals("14:00", s.nextStartClock)
    }

    @Test
    fun `大课间阶段不发课前提醒`() {
        // 10:00 不是下午首节（同属上午会话）：9:50 应为大课间而非课前提醒
        val s = st(9, 50)
        assertEquals(NoticePhase.BIG_BREAK, s.phase)
    }

    @Test
    fun `课前 lead 分钟内显示通知`() {
        val s = st(7, 50)
        assertTrue(s.showNotice)
        assertEquals(NoticePhase.PRE, s.phase)
    }

    @Test
    fun `课间显示通知`() {
        val s = st(9, 50)
        assertTrue(s.showNotice)
    }

    @Test
    fun `早于 lead 不显示`() {
        val s = st(7, 30)
        assertFalse(s.showNotice)
        assertEquals(NoticePhase.HIDDEN, s.phase)
    }

    @Test
    fun `无课当天状态为空`() {
        val day3 = Course(name = "另类", weekday = 3, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 16)
        val s = computeStatus(semester, listOf(day3), emptyList(), weeks, zone, at(8, 0), 0, 0, 20, rule)
        assertFalse(s.silentNow)
        assertEquals(0, s.todayCount)
        assertTrue(s.nextText != null)  // 当天无课，但未来还有课
        assertEquals(NoticePhase.HIDDEN, s.phase)
    }

    @Test
    fun `缓冲提前_上课通知随提前量`() {
        // 课前缓冲 +300s（免打扰 7:55 开启）：7:58 已是「上课中」
        val s = computeStatus(semester, listOf(c1, c2), emptyList(), weeks, zone, at(7, 58), 300, 0, 20, rule)
        assertEquals(NoticePhase.IN_CLASS, s.phase)
    }

    @Test
    fun `缓冲提前_小课间类型仍按真实节次判定`() {
        // 课后缓冲 +300s（8:40 恢复）：8:41 进入有效课间；类型仍是小课间（真实节次间隔 10 分钟）
        val s = computeStatus(semester, listOf(c1, c2), emptyList(), weeks, zone, at(8, 41), 0, 300, 20, rule)
        assertEquals(NoticePhase.SMALL_BREAK, s.phase)
        assertEquals("08:55", s.nextStartClock)   // 展示仍用节次表真实时间
    }
}
