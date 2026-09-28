package com.dndtimetable.domain

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.SpecialDateType
import com.dndtimetable.data.prefs.Period
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsExportTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val periods = listOf(
        Period(480, 525, true), Period(535, 580, true),
        Period(600, 645, true), Period(655, 700, true)
    )
    private val semester = Semester(
        id = 1, startEpochDay = LocalDate.of(2026, 9, 7).toEpochDay(), totalWeeks = 3
    )

    @Test
    fun expandsWeeksSkipsHolidayAndRemapsMakeup() {
        val courses = listOf(
            Course(id = 1, name = "高数", weekday = 1, startPeriod = 1, endPeriod = 2, startWeek = 1, endWeek = 3),
            Course(id = 2, name = "物理", weekday = 2, startPeriod = 3, endPeriod = 4, startWeek = 1, endWeek = 3)
        )
        val specials = listOf(
            SpecialDate(id = 1, epochDay = LocalDate.of(2026, 9, 15).toEpochDay(), type = SpecialDateType.HOLIDAY),
            SpecialDate(
                id = 2, epochDay = LocalDate.of(2026, 9, 19).toEpochDay(), type = SpecialDateType.MAKEUP,
                sourceEpochDay = LocalDate.of(2026, 9, 15).toEpochDay()
            )
        )
        val ics = IcsExport.build(semester, courses, specials, periods, zone, Instant.parse("2026-09-01T00:00:00Z"))
        assertEquals(6, Regex("BEGIN:VEVENT").findAll(ics).count())
        assertTrue(ics.contains("DTSTART:20260907T000000Z"))   // 第 1 周周一 8:00 CST
        assertTrue(ics.contains("DTSTART:20260919T020000Z"))   // 调休补课日 10:00 CST（按周二课表）
        assertEquals(3, Regex("SUMMARY:高数").findAll(ics).count())
        assertEquals(3, Regex("SUMMARY:物理").findAll(ics).count())   // 9/8、9/22 + 9/19 补课
    }

    @Test
    fun escapesSpecialCharacters() {
        val c = Course(
            id = 3, name = "高数,上;下", weekday = 1, startPeriod = 1, endPeriod = 1,
            startWeek = 1, endWeek = 1, teacher = "张三;李四"
        )
        val ics = IcsExport.build(semester, listOf(c), emptyList(), periods, zone, Instant.EPOCH)
        assertTrue(ics.contains("SUMMARY:高数\\,上\\;下"))
        assertTrue(ics.contains("DESCRIPTION:教师：张三\\;李四"))
    }

    @Test
    fun courseBeyondPeriodTableIsSkipped() {
        val c = Course(id = 4, name = "晚课", weekday = 1, startPeriod = 19, endPeriod = 20, startWeek = 1, endWeek = 1)
        val ics = IcsExport.build(semester, listOf(c), emptyList(), periods, zone, Instant.EPOCH)
        assertEquals(0, Regex("BEGIN:VEVENT").findAll(ics).count())
    }
}
