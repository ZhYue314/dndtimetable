package com.dndtimetable.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阳历节日与周末顺延可单测；农历（春节/端午/中秋）依赖 android.icu，
 * 本地 JVM 单测不 mock ICU（isReturnDefaultValues），农历项跳过、以真机验证为准。
 * 已公布官方安排的年份（2026）走官方表，不受推算规则影响。
 */
class LegalHolidaysTest {

    @Test
    fun `阳历节日固定日期`() {
        val rs = LegalHolidays.forYears(2026, 2026)
        assertTrue(rs.any { it.name == "元旦" && it.start == LocalDate.of(2026, 1, 1) })
        assertTrue(
            rs.any {
                it.name == "劳动节" &&
                    it.start == LocalDate.of(2026, 5, 1) && it.end == LocalDate.of(2026, 5, 5)
            }
        )
        assertTrue(rs.any { it.name == "国庆节" && it.start == LocalDate.of(2026, 10, 1) })
    }

    @Test
    fun `清明节气日在四月初`() {
        // 2027 起为推算年份（2026 走官方表），公式同样应落在 4/4-4/6
        val rs = LegalHolidays.forYears(2024, 2027)
        listOf(2024, 2025, 2027).forEach { y ->
            val qm = rs.first { it.name == "清明" && it.start.year == y }.start
            assertTrue("$y 清明日在 4/4-4/6", qm.monthValue == 4 && qm.dayOfMonth in 4..6)
        }
    }

    @Test
    fun `周末顺延规则`() {
        // 2016-10-01 周六：国庆结束 10-07 周五 → 连放到周日 10-09（推算年份）
        val rs = LegalHolidays.forYears(2016, 2016)
        val gq = rs.first { it.name == "国庆节" }
        assertEquals(LocalDate.of(2016, 10, 9), gq.end)
    }

    /** 2026 走国务院办公厅官方安排：放假 7 天、不因周末再顺延到 10/9。 */
    @Test
    fun `2026 官方放假安排`() {
        val rs = LegalHolidays.forYears(2026, 2026)
        assertEquals(LocalDate.of(2026, 10, 7), rs.first { it.name == "国庆节" }.end)
        assertEquals(LocalDate.of(2026, 1, 3), rs.first { it.name == "元旦" }.end)
        assertEquals(LocalDate.of(2026, 2, 15), rs.first { it.name == "春节" }.start)
        assertEquals(LocalDate.of(2026, 2, 23), rs.first { it.name == "春节" }.end)
        assertEquals(LocalDate.of(2026, 4, 4), rs.first { it.name == "清明" }.start)
        assertEquals(LocalDate.of(2026, 4, 6), rs.first { it.name == "清明" }.end)
        assertEquals(LocalDate.of(2026, 9, 25), rs.first { it.name == "中秋节" }.start)
        assertEquals(LocalDate.of(2026, 6, 19), rs.first { it.name == "端午节" }.start)
    }

    /** 内置表不再带调休补课日（来自联网表/用户自添加），未注入远端时应为空。 */
    @Test
    fun `内置不含调休补课日`() {
        assertTrue(LegalHolidays.makeupsForYears(2026, 2027).isEmpty())
    }
}
