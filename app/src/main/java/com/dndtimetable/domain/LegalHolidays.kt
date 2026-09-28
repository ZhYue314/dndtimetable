package com.dndtimetable.domain

import android.icu.util.ChineseCalendar
import java.time.LocalDate

/**
 * 内置法定节假日，外加联网表注入（[installRemote]，见 [HolidayRemote]）：
 *
 * - **联网表优先**（自托管 JSON，App 启动静默拉取并缓存；解析失败不覆盖）；
 * - **官方内置表**（[officialYears]）：已公布年份的**放假**日期，逐条照抄国务院办公厅通知，
 *   也是联网不可用时的兜底；**调休补课日来自联网表**（只给哪天补班，不带被补日），
 *   播种后显示在「自添加日期」区，由用户补全结束日期（补哪天的课）；
 * - **推算兜底**（[estimated]）：未公布年份按规律推算（阳历固定 + 农历 ICU + 清明寿星公式 + 周末顺延），
 *   只给放假不给调休——各校补课安排差异大，让用户手动补。
 *
 * 内置数据来源：国务院办公厅关于 2026 年部分节假日安排的通知（国办发明电〔2025〕7 号）。
 * 用户可在「假期设置」里改日期/开关，改过的（edited）不会被再次播种覆盖。
 */
object LegalHolidays {

    data class Range(val name: String, val start: LocalDate, val end: LocalDate, val fixed: Boolean = false)

    /**
     * 调休补课（上班）日。[source] = 被补课日期（结束日期）：[date] 那天按它那天的课表上课；
     * **null = 尚未设置结束日期**（联网表只给「哪天补班」）：当天按星期几正常上课、显示「班」角标，
     * 并在头天提醒用户补全；设置后引擎同步该来源日的课表。
     */
    data class Makeup(val date: LocalDate, val source: LocalDate?, val name: String)

    /** 联网表（年份 → 放假区间 + 调休补课）；空 = 仅内置表。 */
    @Volatile
    private var remoteYears: Map<Int, Pair<List<Range>, List<Makeup>>> = emptyMap()

    /** 注入联网拉取到的官方表（空表不覆盖——保持缓存/内置兜底语义）。 */
    fun installRemote(table: Map<Int, Pair<List<Range>, List<Makeup>>>) {
        if (table.isNotEmpty()) remoteYears = table
    }

    /** 某年的官方条目：联网表与内置表按字段互相兜底（远端该年缺调休则用内置的，反之亦然）。 */
    private fun officialEntry(year: Int): Pair<List<Range>, List<Makeup>>? {
        val r = remoteYears[year]
        val b = officialYears[year]
        return when {
            r == null -> b
            b == null -> r
            else -> (if (r.first.isEmpty()) b.first else r.first) to (if (r.second.isEmpty()) b.second else r.second)
        }
    }

    // ---------- 官方公布年份 ----------

    /** 2026 年放假安排（国办发明电〔2025〕7 号）。 */
    private val table2026: List<Range> = listOf(
        Range("元旦", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 3), fixed = true),
        Range("春节", LocalDate.of(2026, 2, 15), LocalDate.of(2026, 2, 23), fixed = true),
        Range("清明", LocalDate.of(2026, 4, 4), LocalDate.of(2026, 4, 6), fixed = true),
        Range("劳动节", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5), fixed = true),
        Range("端午节", LocalDate.of(2026, 6, 19), LocalDate.of(2026, 6, 21), fixed = true),
        Range("中秋节", LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 27), fixed = true),
        Range("国庆节", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7), fixed = true)
    )

    /** 已公布官方安排的年份（仅放假；调休补课走联网表/用户自添加）。 */
    private val officialYears: Map<Int, Pair<List<Range>, List<Makeup>>> = mapOf(
        2026 to (table2026 to emptyList())
    )

    /** 生成 [fromYear, toYear] 每年的全部法定节假日：联网表 > 官方内置表 > 按规律推算。 */
    fun forYears(fromYear: Int, toYear: Int): List<Range> {
        val out = ArrayList<Range>()
        for (y in fromYear..toYear) {
            val official = officialEntry(y)
            if (official != null) out.addAll(official.first) else out.addAll(estimated(y))
        }
        return out
    }

    /** 生成 [fromYear, toYear] 每年的调休补课日（联网表或官方内置表有才给）。 */
    fun makeupsForYears(fromYear: Int, toYear: Int): List<Makeup> {
        val out = ArrayList<Makeup>()
        for (y in fromYear..toYear) officialEntry(y)?.second?.let { out.addAll(it) }
        return out
    }

    // ---------- 未公布年份的推算 ----------

    /** 推算某年放假（不含调休）：阳历固定 + 农历（春节/端午/中秋）+ 清明公式 + 周末顺延。 */
    private fun estimated(year: Int): List<Range> {
        val out = ArrayList<Range>()
        out.add(Range("元旦", LocalDate.of(year, 1, 1), LocalDate.of(year, 1, 1)))
        lunar(year, 1, 1)?.let { out.add(Range("春节", it.minusDays(1), it.plusDays(2))) }  // 除夕~初三
        val qm = qingmingDay(year)
        out.add(Range("清明", LocalDate.of(year, 4, qm), LocalDate.of(year, 4, qm)))
        out.add(Range("劳动节", LocalDate.of(year, 5, 1), LocalDate.of(year, 5, 5)))
        lunar(year, 5, 5)?.let { out.add(Range("端午节", it, it)) }
        lunar(year, 8, 15)?.let { out.add(Range("中秋节", it, it)) }
        out.add(Range("国庆节", LocalDate.of(year, 10, 1), LocalDate.of(year, 10, 7)))
        // 周末顺延：假期结束日为周五 → 连放到周日；为周六 → 连放到周日（覆盖周日有课的情况）
        return out.map { r ->
            when (r.end.dayOfWeek) {
                java.time.DayOfWeek.FRIDAY -> r.copy(end = r.end.plusDays(2))
                java.time.DayOfWeek.SATURDAY -> r.copy(end = r.end.plusDays(1))
                else -> r
            }
        }.map { it.copy(fixed = true) }
    }

    /** 农历（非闰月）month/day 在公历 year 年中的日期。 */
    private fun lunar(year: Int, month: Int, day: Int): LocalDate? {
        var d = LocalDate.of(year, 1, 1)
        while (d.year == year) {
            val cc = ChineseCalendar()
            cc.timeInMillis = d.toEpochDay() * 86400000L + 12 * 3600_000L   // 当日正午，避开时区边界
            val isLeap = cc.get(ChineseCalendar.IS_LEAP_MONTH) == 1
            if (!isLeap && cc.get(ChineseCalendar.MONTH) + 1 == month && cc.get(ChineseCalendar.DAY_OF_MONTH) == day) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** 清明节气日：day = int(y*0.2422 + 4.81) - int(y/4)，y = 年份后两位。 */
    private fun qingmingDay(year: Int): Int {
        val y = year % 100
        return (y * 0.2422 + 4.81).toInt() - y / 4
    }
}
