package com.dndtimetable.domain

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.SpecialDateType
import com.dndtimetable.data.db.WeekType
import com.dndtimetable.data.prefs.PeriodTable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEngineTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)
        .minusDays(LocalDate.of(2026, 9, 7).dayOfWeek.value.toLong() - 1).let { it }
    private val semester = Semester(1, "test", startEpochDay = monday.toEpochDay(), totalWeeks = 16)
    private val weeks: List<com.dndtimetable.data.prefs.Period> = PeriodTable.DEFAULT
    private fun at(hour: Int, min: Int, day: LocalDate = monday): Instant =
        day.atTime(hour, min).atZone(zone).toInstant()

    private fun course(
        weekday: Int = 1, start: Int = 1, end: Int = 2,
        sw: Int = 1, ew: Int = 16, wt: WeekType = WeekType.ALL
    ) = Course(name = "高数", weekday = weekday, startPeriod = start, endPeriod = end,
        startWeek = sw, endWeek = ew, weekType = wt)

    @Test
    fun `周次推算`() {
        assertEquals(1, ScheduleEngine.weekNumber(semester, monday))
        assertEquals(2, ScheduleEngine.weekNumber(semester, monday.plusDays(7)))
        assertEquals(0, ScheduleEngine.weekNumber(semester, monday.minusDays(1)))
    }

    @Test
    fun `由当前周反推开学日期`() {
        // 2026-09-09 是周三，monday=2026-09-07 为该周周一
        val wednesday = monday.plusDays(2)
        assertEquals(monday, ScheduleEngine.semesterStartFromWeek(wednesday, 1))
        assertEquals(monday.minusWeeks(2), ScheduleEngine.semesterStartFromWeek(wednesday, 3))
        // 反推结果与 weekNumber 自洽
        val start = ScheduleEngine.semesterStartFromWeek(wednesday, 3)
        assertEquals(3, ScheduleEngine.weekNumber(Semester(1, "t", start.toEpochDay(), 16), wednesday))
    }

    @Test
    fun `单双周匹配`() {
        assertTrue(ScheduleEngine.weekMatches(WeekType.ALL, 2))
        assertTrue(ScheduleEngine.weekMatches(WeekType.ODD, 1))
        assertFalse(ScheduleEngine.weekMatches(WeekType.ODD, 2))
        assertTrue(ScheduleEngine.weekMatches(WeekType.EVEN, 2))
        assertFalse(ScheduleEngine.weekMatches(WeekType.EVEN, 1))
    }

    @Test
    fun `周末默认不排静音`() {
        val sat = monday.plusDays(5)
        val c = Course(name = "物理", weekday = 6, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 16)
        val off = ScheduleEngine.compute(semester, listOf(c), emptyList(), weeks, zone, at(8, 30, sat), 0, 0, weekendDnd = false)
        assertFalse(off.silentNow)
        assertEquals(null, off.next)
        val on = ScheduleEngine.compute(semester, listOf(c), emptyList(), weeks, zone, at(8, 30, sat), 0, 0, weekendDnd = true)
        assertTrue(on.silentNow)
    }

    @Test
    fun `周末补课日照常静音`() {
        val sat = monday.plusDays(5)
        // 周六补周一的课：即使「周末不静音」开着，补课日照常排静音
        val makeup = SpecialDate(epochDay = sat.toEpochDay(), type = SpecialDateType.MAKEUP, sourceEpochDay = monday.toEpochDay())
        val c = Course(name = "高数", weekday = 1, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 16)
        val r = ScheduleEngine.compute(semester, listOf(c), listOf(makeup), weeks, zone, at(8, 30, sat), 0, 0, weekendDnd = false)
        assertTrue(r.silentNow)
    }

    @Test
    fun `周末免打扰判定与课表角标共用`() {
        val sat = monday.plusDays(5)
        assertFalse(ScheduleEngine.dndAppliesOn(emptyList(), sat, weekendDnd = false))
        assertTrue(ScheduleEngine.dndAppliesOn(emptyList(), monday, weekendDnd = false))
        assertTrue(ScheduleEngine.dndAppliesOn(emptyList(), sat, weekendDnd = true))
        val makeup = SpecialDate(epochDay = sat.toEpochDay(), type = SpecialDateType.MAKEUP, sourceEpochDay = monday.toEpochDay())
        assertTrue(ScheduleEngine.dndAppliesOn(listOf(makeup), sat, weekendDnd = false))
    }

    @Test
    fun `上课时间窗内 silentNow`() {
        val r = ScheduleEngine.compute(semester, listOf(course()), emptyList(), weeks, zone, at(8, 30), 0, 0)
        assertTrue(r.silentNow)
    }

    @Test
    fun `课间不在窗口`() {
        val r = ScheduleEngine.compute(semester, listOf(course()), emptyList(), weeks, zone, at(9, 50), 0, 0)
        assertFalse(r.silentNow)
    }

    @Test
    fun `下一个事件为开启`() {
        val r = ScheduleEngine.compute(semester, listOf(course()), emptyList(), weeks, zone, at(7, 0), 0, 0)
        assertEquals(at(8, 0).toEpochMilli(), r.next?.timeMillis)
        assertTrue(r.next?.start!!)
    }

    @Test
    fun `课程中下一个事件为恢复`() {
        val r = ScheduleEngine.compute(semester, listOf(course()), emptyList(), weeks, zone, at(9, 0), 0, 0)
        assertFalse(r.next?.start!!)
        assertEquals(at(9, 40).toEpochMilli(), r.next?.timeMillis)
    }

    @Test
    fun `缓冲偏移关闭与打开时间`() {
        val r = ScheduleEngine.compute(semester, listOf(course()), emptyList(), weeks, zone, at(7, 0), 300, 300)
        // 提前5分钟开启：8:00-5min = 7:55
        assertEquals(at(7, 55).toEpochMilli(), r.next?.timeMillis)
        // 窗口内 7:58 应已静音（提前 300s）
        val r2 = ScheduleEngine.compute(semester, listOf(course()), emptyList(), weeks, zone, at(7, 58), 300, 0)
        assertTrue(r2.silentNow)
    }

    @Test
    fun `单双周课在不对应周不生效`() {
        val ode = course(wt = WeekType.ODD)
        val week2 = monday.plusDays(7)
        val r = ScheduleEngine.compute(semester, listOf(ode), emptyList(), weeks, zone, at(8, 30, week2), 0, 0)
        assertFalse(r.silentNow)
    }

    @Test
    fun `节假日拦截课程`() {
        val h = SpecialDate(epochDay = monday.toEpochDay(), type = SpecialDateType.HOLIDAY, enabled = true)
        val r = ScheduleEngine.compute(semester, listOf(course()), listOf(h), weeks, zone, at(8, 30), 0, 0)
        assertFalse(r.silentNow)
    }

    @Test
    fun `调休补课生效于节假日`() {
        val c = course(weekday = 3)  // 周三课，但周一调休补课
        val m = SpecialDate(epochDay = monday.toEpochDay(), type = SpecialDateType.MAKEUP, courseId = c.id)
        val r = ScheduleEngine.compute(semester, listOf(c), listOf(m), weeks, zone, at(8, 30), 0, 0)
        assertTrue(r.silentNow)
    }

    @Test
    fun `非自动静音节次被跳过`() {
        val periods = PeriodTable.DEFAULT.map { if (it == PeriodTable.DEFAULT[0]) it.copy(auto = false) else it }
        val r = ScheduleEngine.compute(semester, listOf(course()), emptyList(), periods, zone, at(8, 30), 0, 0)
        assertFalse(r.silentNow)
    }

    @Test
    fun `通知事件提前与关闭`() {
        val rule = com.dndtimetable.data.prefs.PeriodRule(45, 10, 20)
        val events = ScheduleEngine.nextNoticeEvents(semester, listOf(course()), emptyList(), weeks, rule, zone, at(6, 0), 20)
        assertEquals(
            listOf(
                at(7, 40).toEpochMilli() to ScheduleEngine.NoticeKind.PRE,     // 上课前提醒
                at(8, 0).toEpochMilli() to ScheduleEngine.NoticeKind.CONTENT,  // 第1节上课中
                at(8, 45).toEpochMilli() to ScheduleEngine.NoticeKind.CONTENT, // 小课间
                at(8, 55).toEpochMilli() to ScheduleEngine.NoticeKind.CONTENT, // 第2节上课中
                at(9, 40).toEpochMilli() to ScheduleEngine.NoticeKind.HIDE     // 当天结束
            ),
            events.map { it.timeMillis to it.kind }
        )
    }

    @Test
    fun `上午下午各一段会话_课前提醒只在会话首节`() {
        // 上午 1-2节 + 下午 5-6节（14:00-15:40）：PRE 应只有 7:40 与 13:40，10:00 大课间无 PRE
        val afternoon = course(start = 5, end = 6)
        val rule = com.dndtimetable.data.prefs.PeriodRule(45, 10, 20)
        val events = ScheduleEngine.nextNoticeEvents(semester, listOf(course(), afternoon), emptyList(), weeks, rule, zone, at(6, 0), 20)
        val pres = events.filter { it.kind == ScheduleEngine.NoticeKind.PRE }.map { it.timeMillis }
        assertEquals(
            listOf(at(7, 40).toEpochMilli(), at(13, 40).toEpochMilli()),
            pres
        )
    }

    @Test
    fun `下午第一大节无课则课前提醒在4点前`() {
        // 只有下午 7-8节（16:00-17:40）：会话首节=16:00 → PRE 15:40
        val eveningOnly = course(start = 7, end = 8)
        val rule = com.dndtimetable.data.prefs.PeriodRule(45, 10, 20)
        val events = ScheduleEngine.nextNoticeEvents(semester, listOf(eveningOnly), emptyList(), weeks, rule, zone, at(6, 0), 20)
        val pres = events.filter { it.kind == ScheduleEngine.NoticeKind.PRE }.map { it.timeMillis }
        assertEquals(listOf(at(15, 40).toEpochMilli()), pres)
    }

    @Test
    fun `通知事件随上下课缓冲偏移`() {
        // 提前/延后缓冲各 300s：事件整体随 DND 窗口平移（PRE 7:35 / 上课 7:55 / 小课间 8:40 / 上课 8:50 / 结束 9:35）
        val rule = com.dndtimetable.data.prefs.PeriodRule(45, 10, 20)
        val events = ScheduleEngine.nextNoticeEvents(semester, listOf(course()), emptyList(), weeks, rule, zone, at(6, 0), 20, 300, 300)
        assertEquals(
            listOf(
                at(7, 35).toEpochMilli() to ScheduleEngine.NoticeKind.PRE,
                at(7, 55).toEpochMilli() to ScheduleEngine.NoticeKind.CONTENT,
                at(8, 40).toEpochMilli() to ScheduleEngine.NoticeKind.CONTENT,
                at(8, 50).toEpochMilli() to ScheduleEngine.NoticeKind.CONTENT,
                at(9, 35).toEpochMilli() to ScheduleEngine.NoticeKind.HIDE
            ),
            events.map { it.timeMillis to it.kind }
        )
    }

    @Test
    fun `当日课程过滤`() {
        assertTrue(ScheduleEngine.activeDayCourses(semester, listOf(course()), emptyList(), monday).size == 1)
        val h = SpecialDate(epochDay = monday.toEpochDay(), type = SpecialDateType.HOLIDAY, enabled = true)
        assertTrue(ScheduleEngine.activeDayCourses(semester, listOf(course()), listOf(h), monday).isEmpty())
    }

    // --- 扫描期回归：旧实现只扫 today..+6，长假/长空窗会把未来课排空，漏掉节后首日课 ---

    /** 2026-09-07 开学（周一）；9/28–10/06 放 9 天假，覆盖 9/28 与 10/05 两个周一；下一课 10/12。 */
    private val longHoliday = SpecialDate(
        epochDay = LocalDate.of(2026, 9, 28).toEpochDay(),
        endEpochDay = LocalDate.of(2026, 10, 6).toEpochDay(),
        type = SpecialDateType.HOLIDAY, enabled = true
    )
    private val vacationEve: Instant =
        LocalDate.of(2026, 9, 27).atTime(20, 0).atZone(zone).toInstant()
    private val nextClassDay: LocalDate = LocalDate.of(2026, 10, 12)   // 周一（第 6 周）

    @Test
    fun `长假第1天仍排到节后首日课闹钟`() {
        val r = ScheduleEngine.compute(semester, listOf(course(ew = 16)), listOf(longHoliday), weeks, zone, vacationEve, 0, 0)
        assertFalse(r.silentNow)
        assertEquals(nextClassDay.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), r.next?.timeMillis)
        assertTrue(r.next?.start!!)
    }

    @Test
    fun `长假期间通知提醒排到节后首日课程链`() {
        val rule = com.dndtimetable.data.prefs.PeriodRule(45, 10, 20)
        val events = ScheduleEngine.nextNoticeEvents(semester, listOf(course(ew = 16)), listOf(longHoliday), weeks, rule, zone, vacationEve, 20)
        assertEquals(
            listOf(
                nextClassDay.atTime(7, 40).atZone(zone).toInstant().toEpochMilli() to ScheduleEngine.NoticeKind.PRE,
                nextClassDay.atTime(8, 0).atZone(zone).toInstant().toEpochMilli() to ScheduleEngine.NoticeKind.CONTENT,
                nextClassDay.atTime(8, 45).atZone(zone).toInstant().toEpochMilli() to ScheduleEngine.NoticeKind.CONTENT,
                nextClassDay.atTime(8, 55).atZone(zone).toInstant().toEpochMilli() to ScheduleEngine.NoticeKind.CONTENT,
                nextClassDay.atTime(9, 40).atZone(zone).toInstant().toEpochMilli() to ScheduleEngine.NoticeKind.HIDE
            ),
            events.map { it.timeMillis to it.kind }
        )
    }

    @Test
    fun `本周课程全部结束后仍排下周课程闹钟与提醒`() {
        // 周一 21:00：今天的链已结束、本周无其他课 → 旧实现 next 为 null 排空闹钟
        val now = monday.atTime(21, 0).atZone(zone).toInstant()
        val r = ScheduleEngine.compute(semester, listOf(course()), emptyList(), weeks, zone, now, 0, 0)
        val nextMonday = monday.plusDays(7)
        assertEquals(nextMonday.atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), r.next?.timeMillis)

        val rule = com.dndtimetable.data.prefs.PeriodRule(45, 10, 20)
        val events = ScheduleEngine.nextNoticeEvents(semester, listOf(course()), emptyList(), weeks, rule, zone, now, 20)
        assertEquals(
            nextMonday.atTime(7, 40).atZone(zone).toInstant().toEpochMilli(),
            events.first().timeMillis
        )
        assertEquals(ScheduleEngine.NoticeKind.PRE, events.first().kind)
    }

    @Test
    fun `单双周课跨周扫描仍正确`() {
        // 双周课：偶数周周一上课；第3周（奇数周）周一 21:00 → 下一课应为第4周周一
        val even = course(wt = WeekType.EVEN)
        val now = monday.plusDays(14).atTime(21, 0).atZone(zone).toInstant()   // 第3周周一
        val r = ScheduleEngine.compute(semester, listOf(even), emptyList(), weeks, zone, now, 0, 0)
        assertEquals(monday.plusDays(21).atTime(8, 0).atZone(zone).toInstant().toEpochMilli(), r.next?.timeMillis)
    }

    @Test
    fun `删除周范围把课拆成前后两段`() {
        val c = course(sw = 1, ew = 16).copy(id = 7)
        // 删中间一周 → 两段：前段保留原 id，后段是新行（id=0，由调用方 insert）
        val mid = ScheduleEngine.minusWeeks(c, 5, 5)
        assertEquals(2, mid.size)
        assertEquals(7L, mid[0].id)
        assertEquals(4, mid[0].endWeek)
        assertEquals(0L, mid[1].id)
        assertEquals(6, mid[1].startWeek)
        // 删开头 / 删结尾 → 只剩一段，原地更新（id 不变，免打扰规则跟着留下）
        assertEquals(listOf(c.copy(startWeek = 6)), ScheduleEngine.minusWeeks(c, 1, 5))
        assertEquals(listOf(c.copy(endWeek = 11)), ScheduleEngine.minusWeeks(c, 12, 16))
        // 删满整门（含范围比课更宽的「整个学期」）→ 空，调用方 deleteCourse
        assertEquals(emptyList<Course>(), ScheduleEngine.minusWeeks(c, 1, 16))
        assertEquals(emptyList<Course>(), ScheduleEngine.minusWeeks(c, 1, 99))    // 起点盖住整门课即可（整个学期）
        // 只删后半截（范围比课宽但没盖住开头）→ 还剩前半截
        assertEquals(listOf(c.copy(endWeek = 2)), ScheduleEngine.minusWeeks(c, 3, 99))
        // 没有交集 → 原样返回
        assertEquals(listOf(c), ScheduleEngine.minusWeeks(c, 17, 20))
        assertEquals(listOf(c), ScheduleEngine.minusWeeks(c, 0, 0))
    }

    @Test
    fun `删除周范围要匹配单双周`() {
        val odd = course(sw = 1, ew = 15, wt = WeekType.ODD).copy(id = 9)
        // 双周点「仅当周」/ 指定纯双周段：单周课这周本来就没课 → 原样返回，不许把整门课劈成两半
        assertEquals(listOf(odd), ScheduleEngine.minusWeeks(odd, 6, 6))
        assertEquals(listOf(odd), ScheduleEngine.minusWeeks(odd, 4, 4))
        // 范围里确实有单周（5、7…）才删：4..6 含第 5 周 → 拆成 1..3 与 7..15
        val split = ScheduleEngine.minusWeeks(odd, 4, 6)
        assertEquals(2, split.size)
        assertEquals(3, split[0].endWeek)
        assertEquals(7, split[1].startWeek)
        assertEquals(0L, split[1].id)
        // 整个学期（范围宽于课程）照删
        assertEquals(emptyList<Course>(), ScheduleEngine.minusWeeks(odd, 1, 16))
        // 双周课同理：单周范围删不到东西
        val even = course(sw = 2, ew = 16, wt = WeekType.EVEN)
        assertEquals(listOf(even), ScheduleEngine.minusWeeks(even, 3, 3))
    }
}
