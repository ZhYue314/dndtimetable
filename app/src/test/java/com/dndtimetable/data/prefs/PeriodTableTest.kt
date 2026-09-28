package com.dndtimetable.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodTableTest {

    @Test
    fun `编码解码往返一致`() {
        val list = PeriodTable.DEFAULT
        assertEquals(list, PeriodTable.decode(PeriodTable.encode(list)))
    }

    @Test
    fun `解码脏数据容错`() {
        // 一行为空/缺字段 → 丢弃；有效字段保留
        val s = "480,525,1;bad;535,580,0;"
        val r = PeriodTable.decode(s)
        assertEquals(2, r.size)
        assertEquals(Period(480, 525, true), r[0])
        assertEquals(Period(535, 580, false), r[1])
    }

    @Test
    fun `解码不足10节不补默认`() {
        assertEquals(1, PeriodTable.decode("600,645,1").size)
    }

    @Test
    fun `分钟文本互转`() {
        assertEquals("08:00", PeriodTable.minuteToText(480))
        assertEquals(480, PeriodTable.textToMinute("08:00"))
        assertEquals(0, PeriodTable.textToMinute("24:00"))  // 越界归零
    }

    @Test
    fun `按大节起始生成规则`() {
        val r = PeriodTable.fromBigStarts(listOf(480, 840))
        assertEquals(
            listOf(Period(480, 525, false), Period(535, 580, false), Period(840, 885, false), Period(895, 940, false)),
            r
        )
    }

    @Test
    fun `默认10节递增有序`() {
        val d = PeriodTable.DEFAULT
        assertEquals(10, d.size)
        assertTrue(d.all { it.endMin > it.startMin })
    }

    @Test
    fun `扩节按规则顺延到指定节数`() {
        val r = PeriodTable.extend(PeriodTable.DEFAULT, 12, PeriodRule(45, 10, 20))
        assertEquals(12, r.size)
        // 第 11 节 = 新大节第一节：上一节结束 20:40 + 大课间 20 → 21:00-21:45
        assertEquals(Period(1260, 1305, true), r[10])
        // 第 12 节 = 同大节第二节：上一节开始 21:00 + 45 + 小课间 10 → 21:55-22:40
        assertEquals(Period(1315, 1360, true), r[11])
        assertTrue(r.zipWithNext().all { (a, b) -> b.startMin >= a.endMin })
    }

    @Test
    fun `缩节直接截断`() {
        val r = PeriodTable.extend(PeriodTable.DEFAULT, 8, PeriodRule(45, 10, 20))
        assertEquals(8, r.size)
        assertEquals(PeriodTable.DEFAULT.take(8), r)
    }

    @Test
    fun `节数被限制在上下限内`() {
        assertEquals(PeriodTable.MAX_PERIODS, PeriodTable.extend(PeriodTable.DEFAULT, 99, PeriodRule(45, 10, 20)).size)
        assertEquals(PeriodTable.MIN_PERIODS, PeriodTable.extend(PeriodTable.DEFAULT, 1, PeriodRule(45, 10, 20)).size)
    }

    @Test
    fun `解码保留超过10节的节次表`() {
        // 以前 decode 会把 11 节以上截断回 10 节，「节数」根本存不下来
        val twelve = PeriodTable.extend(PeriodTable.DEFAULT, 12, PeriodRule(45, 10, 20))
        assertEquals(12, PeriodTable.decode(PeriodTable.encode(twelve)).size)
    }

    @Test
    fun `解码超过上限才截断`() {
        val tooMany = PeriodTable.extend(PeriodTable.DEFAULT, PeriodTable.MAX_PERIODS, PeriodRule(45, 10, 20)) +
            listOf(Period(1400, 1440, true))
        assertTrue(tooMany.size > PeriodTable.MAX_PERIODS)
        assertEquals(PeriodTable.MAX_PERIODS, PeriodTable.decode(PeriodTable.encode(tooMany)).size)
    }
}
