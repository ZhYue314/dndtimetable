package com.dndtimetable.domain

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.CourseDndRule
import com.dndtimetable.data.db.DndPolicy
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.SpecialDateType
import com.dndtimetable.data.db.WeekType
import com.dndtimetable.data.prefs.DndConfig
import com.dndtimetable.data.prefs.DndConfigCode
import com.dndtimetable.data.prefs.PeriodTable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单门课免打扰（本学期/本周/本节课）、网络课默认、日期锚点与调休补课来源日。
 */
class CourseDndTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)   // 周一
    private val semester = Semester(1, "test", startEpochDay = monday.toEpochDay(), totalWeeks = 16)
    private val weeks = PeriodTable.DEFAULT
    private fun at(hour: Int, min: Int, day: LocalDate = monday): Instant =
        day.atTime(hour, min).atZone(zone).toInstant()

    private fun course(
        id: Long = 1, weekday: Int = 1, start: Int = 1, end: Int = 2,
        sw: Int = 1, ew: Int = 16, wt: WeekType = WeekType.ALL,
        policy: DndPolicy = DndPolicy.INHERIT, code: String? = null
    ) = Course(
        id = id, name = "高数", weekday = weekday, startPeriod = start, endPeriod = end,
        startWeek = sw, endWeek = ew, weekType = wt, dndPolicy = policy, dndCode = code
    )

    // ---------- 日期锚点 / 星期一致性（需求 7） ----------

    @Test
    fun `开学日期归一到周一`() {
        // 2026-09-22 是周二：归一后应是该周周一 9/21
        val tue = LocalDate.of(2026, 9, 22)
        assertEquals(java.time.DayOfWeek.TUESDAY, tue.dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 21), ScheduleEngine.anchorStart(tue))
        assertEquals(java.time.DayOfWeek.MONDAY, ScheduleEngine.anchorStart(tue).dayOfWeek)
    }

    @Test
    fun `周次按周一锚点推进且星期与日期一致`() {
        val start = ScheduleEngine.anchorStart(LocalDate.of(2026, 9, 22))
        val sem = Semester(1, "t", start.toEpochDay(), 16)
        // 第 1 周周一..周日都是第 1 周
        (0..6L).forEach { assertEquals(1, ScheduleEngine.weekNumber(sem, start.plusDays(it))) }
        (7..13L).forEach { assertEquals(2, ScheduleEngine.weekNumber(sem, start.plusDays(it))) }
        // 2026-09-22（周二）落在第 1 周周二那一列：周次=1、星期=2
        assertEquals(1, ScheduleEngine.weekNumber(sem, LocalDate.of(2026, 9, 22)))
        assertEquals(2, ScheduleEngine.weekdayOf(LocalDate.of(2026, 9, 22)))
        assertEquals("二", ScheduleEngine.weekdayName(ScheduleEngine.weekdayOf(LocalDate.of(2026, 9, 22))))
        // 历史数据：开学日期是周二（9/22）时，周次仍按该周周一（9/21）算，
        // 周二当天 = 第 1 周周二（修掉「9/22 周二却排在周一列」的错乱）；学期起始日之前仍算 0
        val legacy = Semester(1, "t", LocalDate.of(2026, 9, 22).toEpochDay(), 16)
        assertEquals(LocalDate.of(2026, 9, 21), ScheduleEngine.weekAnchor(legacy))
        assertEquals(1, ScheduleEngine.weekNumber(legacy, LocalDate.of(2026, 9, 22)))
        assertEquals(0, ScheduleEngine.weekNumber(legacy, LocalDate.of(2026, 9, 21)))   // 开学前的周一 = 学期外
        assertEquals(1, ScheduleEngine.weekNumber(legacy, LocalDate.of(2026, 9, 27)))   // 同周周日
        assertEquals(2, ScheduleEngine.weekNumber(legacy, LocalDate.of(2026, 9, 28)))   // 下周一
    }

    @Test
    fun `周次范围与学期范围`() {
        val r = ScheduleEngine.weekRange(semester, 1)!!
        assertEquals(monday.toEpochDay(), r.start)
        assertEquals(monday.plusDays(6).toEpochDay(), r.endInclusive)
        val all = ScheduleEngine.semesterRange(semester)!!
        assertEquals(monday.toEpochDay(), all.start)
        assertEquals(monday.plusWeeks(16).minusDays(1).toEpochDay(), all.endInclusive)
        assertNull(ScheduleEngine.weekRange(null, 1))
    }

    // ---------- 网络课默认不静音（需求 4） ----------

    @Test
    fun `网络课识别与默认关闭免打扰`() {
        assertTrue(ScheduleEngine.isOnlineCourse("新中国史（四史）MOOC", "网络教师1", null))
        assertTrue(ScheduleEngine.isOnlineCourse("大学英语（网课）", null, null))
        assertTrue(ScheduleEngine.isOnlineCourse(null, null, "网络教学平台"))
        assertFalse(ScheduleEngine.isOnlineCourse("高等数学", "张三", "S50607"))
        assertEquals(DndPolicy.OFF, ScheduleEngine.defaultPolicyFor("人力资源管理（创新创业类）MOOC", "网络教师2", null))
        assertEquals(DndPolicy.INHERIT, ScheduleEngine.defaultPolicyFor("高等数学", "张三", "S50607"))
    }

    @Test
    fun `整门课关闭后不产生静音窗口`() {
        val off = course(policy = DndPolicy.OFF)
        val r = ScheduleEngine.compute(semester, listOf(off), emptyList(), weeks, zone, at(8, 30), 0, 0)
        assertFalse(r.silentNow)
        assertNull(ScheduleEngine.compute(semester, listOf(off), emptyList(), weeks, zone, at(7, 0), 0, 0).next)
        // 课表照常显示（不静音 ≠ 没课）
        assertEquals(1, ScheduleEngine.activeDayCourses(semester, listOf(off), emptyList(), monday).size)
        // 通知链也应为空
        val rule = com.dndtimetable.data.prefs.PeriodRule(45, 10, 20)
        assertTrue(ScheduleEngine.nextNoticeEvents(semester, listOf(off), emptyList(), weeks, rule, zone, at(6, 0), 20).isEmpty())
    }

    @Test
    fun `单个课程可单独指定静音方式`() {
        val c = course(policy = DndPolicy.CUSTOM, code = DndConfigCode.encode(DndConfig(silent = true, media = false, filter = false)))
        val cfg = ScheduleEngine.customConfig(c)
        assertEquals(DndConfig(silent = true, media = false, filter = false), cfg)
        assertEquals("静音", DndConfigCode.label(cfg!!))
        assertNull(ScheduleEngine.customConfig(course()))   // 继承全局时不覆盖
        assertEquals(DndConfig(true, true, false), DndConfigCode.decode("1,1,0")!!)
        assertNull(DndConfigCode.decode(null))
    }

    // ---------- 三级「是否开启免打扰」（需求 5） ----------

    @Test
    fun `本学期规则关闭该课所有静音窗口`() {
        val c = course()
        val rules = listOf(
            CourseDndRule(
                courseId = c.id,
                dateStart = ScheduleEngine.semesterRange(semester)!!.start,
                dateEnd = ScheduleEngine.semesterRange(semester)!!.endInclusive
            )
        )
        assertTrue(ScheduleEngine.dndSuppressed(rules, c.id, monday.toEpochDay(), 1))
        val r = ScheduleEngine.compute(semester, listOf(c), emptyList(), weeks, zone, at(8, 30), 0, 0, rules)
        assertFalse(r.silentNow)
    }

    @Test
    fun `本周规则只影响该周`() {
        val c = course()
        val week3 = ScheduleEngine.weekRange(semester, 3)!!
        val rules = listOf(CourseDndRule(courseId = c.id, dateStart = week3.start, dateEnd = week3.endInclusive))
        val w3mon = monday.plusWeeks(2)
        assertFalse(
            ScheduleEngine.compute(semester, listOf(c), emptyList(), weeks, zone, at(8, 30, w3mon), 0, 0, rules).silentNow
        )
        assertTrue(
            ScheduleEngine.compute(semester, listOf(c), emptyList(), weeks, zone, at(8, 30, monday), 0, 0, rules).silentNow
        )
    }

    @Test
    fun `单节课规则只影响指定那节课`() {
        val c = course(start = 1, end = 2)   // 第 1、2 节
        val rules = listOf(
            CourseDndRule(courseId = c.id, dateStart = monday.toEpochDay(), dateEnd = monday.toEpochDay(), periodStart = 1, periodEnd = 1)
        )
        assertTrue(ScheduleEngine.dndSuppressed(rules, c.id, monday.toEpochDay(), 1))    // 第 1 节被关掉
        assertFalse(ScheduleEngine.dndSuppressed(rules, c.id, monday.toEpochDay(), 2))   // 第 2 节仍自动静音
        assertTrue(ScheduleEngine.effectivePeriods(c, weeks, rules, monday.toEpochDay()).map { it.first } == listOf(2))
        // 第 1 节关掉后，窗口从第 2 节（8:55）开始：8:30 不静音、9:00 静音
        assertFalse(ScheduleEngine.compute(semester, listOf(c), emptyList(), weeks, zone, at(8, 30), 0, 0, rules).silentNow)
        assertTrue(ScheduleEngine.compute(semester, listOf(c), emptyList(), weeks, zone, at(9, 0), 0, 0, rules).silentNow)
    }

    @Test
    fun `规则可用 false 覆盖更宽的抑制`() {
        val c = course()
        val all = CourseDndRule(courseId = c.id, dateStart = 0, dateEnd = 99999, enabled = true)
        val back = CourseDndRule(courseId = c.id, dateStart = monday.toEpochDay(), dateEnd = monday.toEpochDay(), enabled = false)
        assertTrue(ScheduleEngine.dndSuppressed(listOf(all), c.id, monday.toEpochDay(), 1))
        assertFalse(ScheduleEngine.dndSuppressed(listOf(all, back), c.id, monday.toEpochDay(), 1))
    }

    // ---------- 调休补课：补哪一天的课（需求 6） ----------

    @Test
    fun `调休日按来源日期的课程上课`() {
        // 2026-09-20 是周日（调休上课），补 2026-10-06（周二）的课
        val makeupDay = LocalDate.of(2026, 9, 20)
        val source = LocalDate.of(2026, 10, 6)
        assertEquals(java.time.DayOfWeek.SUNDAY, makeupDay.dayOfWeek)
        assertEquals(java.time.DayOfWeek.TUESDAY, source.dayOfWeek)
        val tue = Course(name = "周二课", weekday = 2, startPeriod = 9, endPeriod = 10, startWeek = 1, endWeek = 16)
        val sun = Course(id = 2, name = "周日课", weekday = 7, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 16)
        val m = SpecialDate(
            epochDay = makeupDay.toEpochDay(), endEpochDay = makeupDay.toEpochDay(),
            type = SpecialDateType.MAKEUP, sourceEpochDay = source.toEpochDay()
        )
        // 来源日 10/6 本身在国庆放假区间里：仍要按它名义上的课表补课（这是调休的常态）
        val nationalDay = SpecialDate(
            epochDay = LocalDate.of(2026, 10, 1).toEpochDay(),
            endEpochDay = LocalDate.of(2026, 10, 7).toEpochDay(),
            type = SpecialDateType.HOLIDAY
        )
        val day = ScheduleEngine.activeDayCourses(semester, listOf(tue, sun), listOf(nationalDay, m), makeupDay)
        // 只上「来源那一天」的课（周二课），原本周日的课被顶掉
        assertEquals(listOf("周二课"), day.map { it.name })
        // 来源日(放假)自己没有课——课被搬到了补课日
        assertTrue(ScheduleEngine.activeDayCourses(semester, listOf(tue, sun), listOf(nationalDay, m), source).isEmpty())
    }

    @Test
    fun `课表网格：放假日仍显示名义课程、调休日显示来源日课程`() {
        // 10/6（周二）在国庆假期里，但课表网格是模板视图，要照常显示它名义上的课；
        // 9/20 调休补课时同样显示 10/6 这批课（画在当天列）。
        val makeupDay = LocalDate.of(2026, 9, 20)
        val source = LocalDate.of(2026, 10, 6)
        val tue = Course(name = "周二课", weekday = 2, startPeriod = 9, endPeriod = 10, startWeek = 1, endWeek = 16)
        val sun = Course(id = 2, name = "周日课", weekday = 7, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 16)
        val nationalDay = SpecialDate(
            epochDay = LocalDate.of(2026, 10, 1).toEpochDay(),
            endEpochDay = LocalDate.of(2026, 10, 7).toEpochDay(),
            type = SpecialDateType.HOLIDAY
        )
        val m = SpecialDate(
            epochDay = makeupDay.toEpochDay(), endEpochDay = makeupDay.toEpochDay(),
            type = SpecialDateType.MAKEUP, sourceEpochDay = source.toEpochDay()
        )
        val specials = listOf(nationalDay, m)
        assertEquals(listOf("周二课"), ScheduleEngine.gridDayCourses(semester, listOf(tue, sun), specials, source).map { it.name })
        assertEquals(listOf("周二课"), ScheduleEngine.gridDayCourses(semester, listOf(tue, sun), specials, makeupDay).map { it.name })
        // 实际口径不变：放假日不上课（首页/免打扰/提醒）
        assertTrue(ScheduleEngine.activeDayCourses(semester, listOf(tue, sun), specials, source).isEmpty())
    }

    @Test
    fun `调休日的静音窗口取来源日期那天的课`() {
        val makeupDay = LocalDate.of(2026, 9, 20)
        val source = LocalDate.of(2026, 10, 6)
        val tue = Course(name = "周二课", weekday = 2, startPeriod = 9, endPeriod = 10, startWeek = 1, endWeek = 16)
        val m = SpecialDate(
            epochDay = makeupDay.toEpochDay(), endEpochDay = makeupDay.toEpochDay(),
            type = SpecialDateType.MAKEUP, sourceEpochDay = source.toEpochDay()
        )
        // 9/20 当天原本无课（周日），调休后按 10/6（周二）的课表上课 → 第 9 节（19:00）应进入静音窗口
        val r = ScheduleEngine.compute(semester, listOf(tue), listOf(m), weeks, zone, at(19, 10, makeupDay), 0, 0)
        assertTrue(r.silentNow)
        // 没有这条调休时，同一时刻不该静音
        val noMakeup = ScheduleEngine.compute(semester, listOf(tue), emptyList(), weeks, zone, at(19, 10, makeupDay), 0, 0)
        assertFalse(noMakeup.silentNow)
    }

    @Test
    fun `未指定来源日的旧式补课仍按关联课程生效`() {
        val makeupDay = monday
        val wed = Course(id = 7, name = "周三课", weekday = 3, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 16)
        val m = SpecialDate(
            epochDay = makeupDay.toEpochDay(), type = SpecialDateType.MAKEUP, courseId = 7
        )
        val day = ScheduleEngine.activeDayCourses(semester, listOf(wed), listOf(m), makeupDay)
        assertEquals(listOf("周三课"), day.map { it.name })
    }

    @Test
    fun `放假仍然优先于调休`() {
        val holiday = SpecialDate(epochDay = monday.toEpochDay(), type = SpecialDateType.HOLIDAY)
        val m = SpecialDate(
            epochDay = monday.toEpochDay(), type = SpecialDateType.MAKEUP, sourceEpochDay = monday.plusDays(2).toEpochDay()
        )
        val c = Course(name = "课", weekday = 3, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 16)
        assertTrue(ScheduleEngine.activeDayCourses(semester, listOf(c), listOf(holiday, m), monday).isEmpty())
    }
}
