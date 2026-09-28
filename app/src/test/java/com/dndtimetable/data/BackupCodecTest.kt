package com.dndtimetable.data

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.CourseDndRule
import com.dndtimetable.data.db.DndPolicy
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.SpecialDateType
import com.dndtimetable.data.db.WeekType
import com.dndtimetable.data.prefs.DndConfig
import com.dndtimetable.data.prefs.Period
import com.dndtimetable.data.prefs.PeriodRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun sample() = BackupCodec.Snapshot(
        activeScheduleId = 2L,
        schedules = listOf(
            Semester(id = 1, name = "我的课表", startEpochDay = 20000, totalWeeks = 20),
            Semester(id = 2, name = "下学期", startEpochDay = 20140, totalWeeks = 18)
        ),
        courses = listOf(
            Course(
                id = 5, name = "高等数学", weekday = 1, startPeriod = 1, endPeriod = 2,
                startWeek = 1, endWeek = 16, weekType = WeekType.ODD, teacher = "张三", location = "A101",
                dndPolicy = DndPolicy.CUSTOM, dndCode = "1,1,0", scheduleId = 1
            ),
            Course(
                id = 6, name = "网课", weekday = 3, startPeriod = 5, endPeriod = 6,
                startWeek = 3, endWeek = 10, enabled = false, dndPolicy = DndPolicy.OFF, scheduleId = 2
            )
        ),
        specials = listOf(
            SpecialDate(
                id = 1, epochDay = 20010, endEpochDay = 20012, type = SpecialDateType.HOLIDAY,
                builtin = true, name = "国庆节", scheduleId = 1
            ),
            SpecialDate(
                id = 2, epochDay = 20020, type = SpecialDateType.MAKEUP,
                sourceEpochDay = 20010, scheduleId = 1
            )
        ),
        rules = listOf(
            CourseDndRule(
                id = 1, scheduleId = 1, courseId = 5,
                dateStart = 20000, dateEnd = 20006, enabled = false
            )
        ),
        periods = listOf(Period(480, 525, true), Period(535, 580, false)),
        periodRule = PeriodRule(45, 10, 20),
        dndConfig = DndConfig(silent = true, media = false, filter = true),
        schedulePeriods = mapOf(
            1L to listOf(Period(480, 525, true), Period(535, 580, true)),
            2L to listOf(Period(600, 645, false))
        )
    )

    @Test
    fun roundTripKeepsEverything() {
        val back = BackupCodec.decode(BackupCodec.encode(sample()))
        assertEquals(sample(), back)
    }

    @Test
    fun rejectsForeignFile() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"app":"other"}""")
        }
        assertTrue(e.message!!.contains("不是本应用"))
    }

    @Test
    fun acceptsLegacyAppTag() {
        // 项目改名前导出的旧备份（app=autoDND）仍可恢复
        val old = BackupCodec.encode(sample()).replace("\"app\":\"${BackupCodec.APP_TAG}\"", "\"app\":\"${BackupCodec.LEGACY_APP_TAG}\"")
        assertEquals(sample(), BackupCodec.decode(old))
    }

    @Test
    fun rejectsEmptySchedules() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"app":"dndtimetable","version":1,"schedules":[]}""")
        }
        assertTrue(e.message!!.contains("没有课表"))
    }

    @Test
    fun rejectsNewerVersion() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(
                """{"app":"dndtimetable","version":99,"schedules":[{"id":1,"name":"x","startEpochDay":1,"totalWeeks":20}]}"""
            )
        }
        assertTrue(e.message!!.contains("更新版本"))
    }

    @Test
    fun rejectsBrokenCourseRow() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(
                """{"app":"dndtimetable","version":1,"schedules":[{"id":1,"name":"x","startEpochDay":1,"totalWeeks":20}],""" +
                    """"courses":[{"id":1,"name":"高数","weekday":1}]}"""
            )
        }
        assertTrue(e.message!!.contains("损坏"))
    }

    @Test
    fun decodedCountsMatchBackup() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample()))
        assertEquals(2, decoded.courses.size)
        assertEquals(2, decoded.specials.size)
        assertEquals(1, decoded.rules.size)
    }
}
