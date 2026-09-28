package com.dndtimetable.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayRemoteTest {

    @Test
    fun `完整 schema 解析出放假区间与调休配对`() {
        val json = """
            {"years":{"2027":{
              "holidays":[{"name":"元旦","start":"2027-01-01","end":"2027-01-03"}],
              "makeups":[{"date":"2027-01-09","source":"2027-01-06","name":"元旦调休上课"}]
            }}}
        """.trimIndent()
        val table = HolidayRemote.parse(json)
        val entry = table[2027]!!
        assertEquals(1, entry.first.size)
        assertEquals("元旦", entry.first[0].name)
        assertEquals(LocalDate.of(2027, 1, 1), entry.first[0].start)
        assertEquals(LocalDate.of(2027, 1, 3), entry.first[0].end)
        val m = entry.second[0]
        assertEquals(LocalDate.of(2027, 1, 9), m.date)
        assertEquals(LocalDate.of(2027, 1, 6), m.source)
        assertEquals("元旦调休上课", m.name)
    }

    @Test
    fun `垃圾与空对象返回空表`() {
        assertTrue(HolidayRemote.parse("{oops").isEmpty())
        assertTrue(HolidayRemote.parse("").isEmpty())
        // 某年两者皆空 → 该年不装入（避免空年份顶掉内置条目）
        assertTrue(HolidayRemote.parse("""{"years":{"2028":{"holidays":[],"makeups":[]}}}""").isEmpty())
    }

    @Test
    fun `无 source 的补课日留空待用户设置`() {
        val json = """
            {"years":{"2027":{"makeups":[
              {"date":"2027-01-09","name":"缺配对"},
              {"date":"2027-02-07","source":"2027-02-05","name":"有效"}
            ]}}}
        """.trimIndent()
        val makeups = HolidayRemote.parse(json)[2027]!!.second
        assertEquals(2, makeups.size)
        // 缺 source → null（App 内提醒用户设置结束日期），不再丢弃
        assertEquals(null, makeups[0].source)
        assertEquals(LocalDate.of(2027, 2, 5), makeups[1].source)
    }

    @Test
    fun `非法日期条目被丢弃其余保留`() {
        val json = """
            {"years":{"2027":{
              "holidays":[
                {"name":"坏","start":"not-a-date","end":"2027-01-03"},
                {"name":"好","start":"2027-04-04","end":"2027-04-06"}
              ]
            }}}
        """.trimIndent()
        val holidays = HolidayRemote.parse(json)[2027]!!.first
        assertEquals(1, holidays.size)
        assertEquals("好", holidays[0].name)
    }

    @Test
    fun `联网表与内置表按字段互相兜底`() {
        // 注入只有调休没有放假的 2026 远端 → 放假仍取内置
        LegalHolidays.installRemote(
            mapOf(2026 to (emptyList<LegalHolidays.Range>() to listOf(LegalHolidays.Makeup(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 27), "测试"))))
        )
        try {
            assertTrue(LegalHolidays.forYears(2026, 2026).isNotEmpty())
            val makeups = LegalHolidays.makeupsForYears(2026, 2026)
            assertEquals(1, makeups.size)
        } finally {
            // 复位：装远端只影响进程内状态（installRemote 整表替换）
            LegalHolidays.installRemote(
                mapOf(2026 to (emptyList<LegalHolidays.Range>() to emptyList<LegalHolidays.Makeup>()))
            )
        }
    }

    @Test
    fun `内置不再带调休（联网表为空时补课日为空）`() {
        assertTrue(LegalHolidays.makeupsForYears(2026, 2027).isEmpty())
    }
}
