package com.dndtimetable.importx

import com.dndtimetable.data.db.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XlsImporterTest {

    private fun fixture(): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("grid_schedule.xls")!!.readBytes()

    @Test
    fun parsesGridXls() {
        val r = XlsImporter.parse(fixture(), maxPeriod = 10)
        assertEquals(8, r.courses.size)

        val mzMon = r.courses.first { it.name == "马克思主义基本原理" && it.weekday == 1 }
        assertEquals(3, mzMon.startPeriod)
        assertEquals(4, mzMon.endPeriod)
        assertEquals(1, mzMon.startWeek)
        assertEquals(16, mzMon.endWeek)
        assertEquals("沈丽萍", mzMon.teacher)
        assertEquals("J7107", mzMon.location)

        val practice = r.courses.first { it.name == "数字电路综合设计实践" }
        assertEquals(1, practice.weekday)
        assertEquals(3, practice.startPeriod)
        assertEquals(17, practice.startWeek)
        assertEquals(18, practice.endWeek)
        assertEquals("罗雪梅", practice.teacher)
        assertEquals("S50602 计算思维与人工智能创新工场", practice.location)

        val javaTue = r.courses.first { it.name == "JAVA程序设计" && it.weekday == 2 }
        assertEquals(WeekType.ODD, javaTue.weekType)
        assertEquals(1, javaTue.startWeek)
        assertEquals(15, javaTue.endWeek)
        assertEquals(7, javaTue.startPeriod)
        assertEquals(8, javaTue.endPeriod)

        val mzTue = r.courses.first { it.name == "马克思主义基本原理" && it.weekday == 2 }
        assertEquals(WeekType.EVEN, mzTue.weekType)
        assertEquals(2, mzTue.startWeek)
        assertEquals(16, mzTue.endWeek)

        val mooc = r.courses.first { it.name.contains("西游记") }
        assertEquals(2, mooc.weekday)
        assertEquals(9, mooc.startPeriod)
        assertEquals(10, mooc.endPeriod)
        assertEquals("网络教师2", mooc.teacher)
        assertNull(mooc.location)
    }
}
