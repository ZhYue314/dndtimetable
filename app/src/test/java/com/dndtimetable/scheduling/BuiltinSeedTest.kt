package com.dndtimetable.scheduling

import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.SpecialDateType
import com.dndtimetable.domain.LegalHolidays
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinSeedTest {

    private val semStart = LocalDate.of(2026, 9, 1).toEpochDay()
    private val semEnd = LocalDate.of(2027, 1, 15).toEpochDay()

    private val makeups2026 = listOf(
        LegalHolidays.Makeup(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 6), "国庆调休上课"),
        LegalHolidays.Makeup(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 7), "国庆调休上课")
    )

    private fun makeupRow(d: LocalDate, src: LocalDate, edited: Boolean = false) = SpecialDate(
        epochDay = d.toEpochDay(), endEpochDay = d.toEpochDay(), sourceEpochDay = src.toEpochDay(),
        type = SpecialDateType.MAKEUP, builtin = true, name = "国庆调休上课", scheduleId = 1, edited = edited
    )

    @Test
    fun `首次播种插入两条同名调休`() {
        val (ins, upd) = planBuiltinSeed(emptyList(), emptyList(), makeups2026, semStart, semEnd, 1)
        assertEquals(2, ins.size)
        assertTrue(upd.isEmpty())
        assertTrue(ins.any { it.epochDay == LocalDate.of(2026, 9, 20).toEpochDay() })
        assertTrue(ins.any { it.epochDay == LocalDate.of(2026, 10, 10).toEpochDay() })
    }

    @Test
    fun `二次播种不动已存在的同名调休——10月10日不会被改成9月20日`() {
        val existing = listOf(
            makeupRow(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 6)),
            makeupRow(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 7))
        )
        val (ins, upd) = planBuiltinSeed(existing, emptyList(), makeups2026, semStart, semEnd, 1)
        assertTrue(ins.isEmpty())
        assertTrue(upd.isEmpty())
    }

    @Test
    fun `用户改过的调休不会被官方日期覆盖`() {
        val existing = listOf(makeupRow(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 7), edited = true))
        val (ins, upd) = planBuiltinSeed(existing, emptyList(), listOf(makeups2026[1]), semStart, semEnd, 1)
        // 已编辑且日期不同 → 不更新；官方条目无处认领 → 新增（用户行保留）
        assertTrue(upd.isEmpty())
        assertEquals(1, ins.size)
        assertEquals(LocalDate.of(2026, 10, 10).toEpochDay(), ins[0].epochDay)
    }

    @Test
    fun `官方只改调休日时按同名未编辑行认领并同步`() {
        // 库里 10/10 补 10/7；官方把调休日改成 10/11（来源仍 10/7）
        val existing = listOf(makeupRow(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 7)))
        val official = listOf(LegalHolidays.Makeup(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 7), "国庆调休上课"))
        val (ins, upd) = planBuiltinSeed(existing, emptyList(), official, semStart, semEnd, 1)
        assertTrue(ins.isEmpty())
        assertEquals(1, upd.size)
        assertEquals(LocalDate.of(2026, 10, 11).toEpochDay(), upd[0].epochDay)
    }

    @Test
    fun `历史写坏的重复9月20行会被认领修成10月10日`() {
        // 旧 bug 现场：两条都是 9/20 补 10/6（10/10 那行被改写）
        val existing = listOf(
            makeupRow(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 6)),
            makeupRow(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 6))
        )
        val (ins, upd) = planBuiltinSeed(existing, emptyList(), makeups2026, semStart, semEnd, 1)
        assertTrue(ins.isEmpty())
        assertEquals(1, upd.size)
        assertEquals(LocalDate.of(2026, 10, 10).toEpochDay(), upd[0].epochDay)
        assertEquals(LocalDate.of(2026, 10, 7).toEpochDay(), upd[0].sourceEpochDay)
    }

    @Test
    fun `学期外的调休不播种`() {
        val outOfSem = listOf(LegalHolidays.Makeup(LocalDate.of(2026, 5, 9), LocalDate.of(2026, 5, 5), "劳动节调休上课"))
        val (ins, _) = planBuiltinSeed(emptyList(), emptyList(), outOfSem, semStart, semEnd, 1)
        assertTrue(ins.isEmpty())
    }

    @Test
    fun `上游改名调休时按未编辑机器行认领并改名`() {
        // 旧库「国庆调休上课」9/20 补 10/6；上游改名「国庆节调休上课」且只标当天上课（source=date）
        val existing = listOf(makeupRow(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 6)))
        val upstream = listOf(LegalHolidays.Makeup(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20), "国庆节调休上课"))
        val (ins, upd) = planBuiltinSeed(existing, emptyList(), upstream, semStart, semEnd, 1)
        assertTrue(ins.isEmpty())
        assertEquals(1, upd.size)
        assertEquals("国庆节调休上课", upd[0].name)
        assertEquals(LocalDate.of(2026, 9, 20).toEpochDay(), upd[0].sourceEpochDay)
    }

    @Test
    fun `上游改名放假区间时按同年未编辑机器行认领并改名`() {
        val gqStart = LocalDate.of(2026, 10, 1).toEpochDay()
        val gqEnd = LocalDate.of(2026, 10, 7).toEpochDay()
        val existing = listOf(
            SpecialDate(epochDay = gqStart, endEpochDay = gqEnd, type = SpecialDateType.HOLIDAY,
                builtin = true, name = "国庆", scheduleId = 1)
        )
        val upstream = listOf(LegalHolidays.Range("国庆节", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7)))
        val (ins, upd) = planBuiltinSeed(existing, upstream, emptyList(), semStart, semEnd, 1)
        assertTrue(ins.isEmpty())
        assertEquals(1, upd.size)
        assertEquals("国庆节", upd[0].name)
        assertEquals(gqStart, upd[0].epochDay)
        assertEquals(gqEnd, upd[0].endEpochDay)
    }

    @Test
    fun `用户改过的调休不被改名兜底认领`() {
        val existing = listOf(makeupRow(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 6), edited = true))
        val upstream = listOf(LegalHolidays.Makeup(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20), "国庆节调休上课"))
        val (ins, upd) = planBuiltinSeed(existing, emptyList(), upstream, semStart, semEnd, 1)
        assertTrue(upd.isEmpty())
        assertEquals(1, ins.size)   // 用户行保留，官方条目新增
    }

    @Test
    fun `联网表无来源日时未编辑行同步置空（重新走设置结束日期提醒）`() {
        val existing = listOf(makeupRow(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20)))
        val upstream = listOf(LegalHolidays.Makeup(LocalDate.of(2026, 9, 20), null, "国庆调休上课"))
        val (ins, upd) = planBuiltinSeed(existing, emptyList(), upstream, semStart, semEnd, 1)
        assertTrue(ins.isEmpty())
        assertEquals(1, upd.size)
        assertNull(upd[0].sourceEpochDay)
    }

    @Test
    fun `用户已设置来源的行不被联网表置空`() {
        val existing = listOf(makeupRow(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 10, 6), edited = true))
        val upstream = listOf(LegalHolidays.Makeup(LocalDate.of(2026, 9, 20), null, "国庆调休上课"))
        val (ins, upd) = planBuiltinSeed(existing, emptyList(), upstream, semStart, semEnd, 1)
        assertTrue(ins.isEmpty())
        assertTrue(upd.isEmpty())
        assertEquals(LocalDate.of(2026, 10, 6).toEpochDay(), existing[0].sourceEpochDay)
    }
}
