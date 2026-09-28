package com.dndtimetable.importx

import com.dndtimetable.data.db.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextImporterTest {

    @Test
    fun parseSimpleLine() {
        val r = TextImporter.parse("高等数学|1|1|2|小明|逸夫楼201|1-16")
        assertEquals(0, r.badLines.size)
        assertEquals(1, r.courses.size)
        val c = r.courses[0]
        assertEquals("高等数学", c.name)
        assertEquals(1, c.weekday)
        assertEquals(1, c.startPeriod)
        assertEquals(2, c.endPeriod)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(WeekType.ALL, c.weekType)
        assertEquals("小明", c.teacher)
        assertEquals("逸夫楼201", c.location)
    }

    @Test
    fun parseMultiSegmentWeeksExpandsToCourses() {
        val r = TextImporter.parse("高等数学|1|1|2|小明|逸夫楼201|1-5、7-11单、12-16双")
        assertEquals(3, r.courses.size)
        assertEquals(Triple(1, 5, WeekType.ALL), r.courses[0].let { Triple(it.startWeek, it.endWeek, it.weekType) })
        assertEquals(Triple(7, 11, WeekType.ODD), r.courses[1].let { Triple(it.startWeek, it.endWeek, it.weekType) })
        assertEquals(Triple(12, 16, WeekType.EVEN), r.courses[2].let { Triple(it.startWeek, it.endWeek, it.weekType) })
        assertTrue(r.courses.all { it.name == "高等数学" && it.weekday == 1 })
    }

    @Test
    fun parseDiscreteWeeks() {
        val r = TextImporter.parse("大学英语|2|3|4|小红|文成楼125|2、5、8")
        assertEquals(3, r.courses.size)
        assertEquals(listOf(2, 5, 8), r.courses.map { it.startWeek })
        assertTrue(r.courses.all { it.startWeek == it.endWeek && it.weekType == WeekType.ALL })
    }

    @Test
    fun parseFullWidthAndHeaderAndFence() {
        val text = """
            ```text
            课程名称|星期|开始节数|结束节数|老师|地点|周数
            线性代数|２|３|４|小红|理工楼110|１-１６
            ```
        """.trimIndent()
        val r = TextImporter.parse(text)
        assertEquals(0, r.badLines.size)
        assertEquals(1, r.courses.size)
        assertEquals(2, r.courses[0].weekday)
        assertEquals(3, r.courses[0].startPeriod)
        assertEquals(16, r.courses[0].endWeek)
    }

    @Test
    fun parseSwappedPeriodsAndEmptyOptionalFields() {
        val r = TextImporter.parse("体育|3|4|3|||1-16")
        assertEquals(1, r.courses.size)
        assertEquals(3, r.courses[0].startPeriod)
        assertEquals(4, r.courses[0].endPeriod)
        assertEquals(null, r.courses[0].teacher)
        assertEquals(null, r.courses[0].location)
    }

    @Test
    fun badLinesAreCollectedAndNotImported() {
        val r = TextImporter.parse(
            """
            高等数学|1|1|2|小明|逸夫楼201|1-16
            缺列课程|1|1
            星期越界|8|1|2|小明|A101|1-16
            节次越界|1|1|99|小明|A101|1-16
            周数缺失|1|1|2|小明|A101|
            解释性文字一行
            """.trimIndent()
        )
        assertEquals(1, r.courses.size)
        assertEquals(5, r.badLines.size)
    }

    @Test
    fun maxPeriodIsRespected() {
        assertTrue(TextImporter.parse("课|1|1|12|a|b|1-16", maxPeriod = 12).courses.isNotEmpty())
        assertTrue(TextImporter.parse("课|1|1|13|a|b|1-16", maxPeriod = 12).courses.isEmpty())
    }

    @Test
    fun bigPeriodNumberingIsExpanded() {
        val r = TextImporter.parse(
            """
            马克思主义基本原理|1|2|2|袁吉萍|J7107|1-16
            JAVA程序设计|1|3|3|刘云玉|S50607|1-16
            计算机组成原理|4|1|1|郭笋延|J8304|1-16
            """.trimIndent(), maxPeriod = 10
        )
        assertEquals(3, r.courses.size)
        val ma = r.courses.first { it.name == "马克思主义基本原理" }
        assertEquals(3, ma.startPeriod)
        assertEquals(4, ma.endPeriod)
        val java = r.courses.first { it.name == "JAVA程序设计" }
        assertEquals(5, java.startPeriod)
        assertEquals(6, java.endPeriod)
        val co = r.courses.first { it.name == "计算机组成原理" }
        assertEquals(1, co.startPeriod)
        assertEquals(2, co.endPeriod)
    }

    @Test
    fun smallPeriodRangesAreNotExpanded() {
        val r = TextImporter.parse("马克思主义基本原理|1|3|4|袁吉萍|J7107|1-16", maxPeriod = 10)
        assertEquals(3, r.courses[0].startPeriod)
        assertEquals(4, r.courses[0].endPeriod)
    }

    @Test
    fun singlePeriodBeyondHalfIsNotExpanded() {
        val r = TextImporter.parse("晚自习|1|7|7|t|l|1-16", maxPeriod = 10)
        assertEquals(7, r.courses[0].startPeriod)
        assertEquals(7, r.courses[0].endPeriod)
    }

    @Test
    fun mixedSingleAndRangeIsNotExpanded() {
        val r = TextImporter.parse(
            """
            课A|1|1|1|t|l|1-16
            课B|1|3|4|t|l|1-16
            """.trimIndent(), maxPeriod = 10
        )
        val a = r.courses.first { it.name == "课A" }
        assertEquals(1, a.startPeriod)
        assertEquals(1, a.endPeriod)
        val b = r.courses.first { it.name == "课B" }
        assertEquals(3, b.startPeriod)
        assertEquals(4, b.endPeriod)
    }

    @Test
    fun alternatingSingleWeeksAreCompacted() {
        val odd = TextImporter.parse("JAVA程序设计|1|3|4|刘云玉|S50607|1、3、5、7、9、11、13、15")
        assertEquals(1, odd.courses.size)
        assertEquals(1, odd.courses[0].startWeek)
        assertEquals(15, odd.courses[0].endWeek)
        assertEquals(WeekType.ODD, odd.courses[0].weekType)

        val even = TextImporter.parse("马克思主义基本原理|2|7|8|袁吉萍|J7107|2、4、6、8、10、12、14、16")
        assertEquals(1, even.courses.size)
        assertEquals(2, even.courses[0].startWeek)
        assertEquals(16, even.courses[0].endWeek)
        assertEquals(WeekType.EVEN, even.courses[0].weekType)
    }

    @Test
    fun consecutiveSingleWeeksAreCompacted() {
        val r = TextImporter.parse("课|1|1|2|t|l|1、2、3")
        assertEquals(1, r.courses.size)
        assertEquals(1, r.courses[0].startWeek)
        assertEquals(3, r.courses[0].endWeek)
        assertEquals(WeekType.ALL, r.courses[0].weekType)
    }

    @Test
    fun bracketAnnotationsInWeekColumnAreIgnored() {
        // 大模型常把整段单元格文字塞进周数列，如 1、3、5…15([周])[07-08节]
        val r = TextImporter.parse("JAVA程序设计|2|9|10|刘云玉|S50607|1、3、5、7、9、11、13、15([周])[07-08节]")
        assertEquals(1, r.courses.size)
        assertEquals(1, r.courses[0].startWeek)
        assertEquals(15, r.courses[0].endWeek)
        assertEquals(WeekType.ODD, r.courses[0].weekType)
    }

    @Test
    fun trailingAnnotationKeepsEvenType() {
        val r = TextImporter.parse("大学物理（下）|2|7|8|鲁军旺|J8208|1-16([双周])[05-06节]双")
        assertEquals(WeekType.EVEN, r.courses[0].weekType)
        assertEquals(1, r.courses[0].startWeek)
        assertEquals(16, r.courses[0].endWeek)
    }

    @Test
    fun csvDelimiterWorks() {
        val csv = """
            课程名称,星期,开始节数,结束节数,老师,地点,周数
            马克思主义基本原理,1,3,4,袁吉萍,J7107,1-16
            新中国史（四史）MOOC,2,9,10,网络教师1,,1-8
        """.trimIndent()
        val r = TextImporter.parse(csv, maxPeriod = 10, delimiter = ',')
        assertEquals(0, r.badLines.size)
        assertEquals(2, r.courses.size)
        val mooc = r.courses.first { it.name.startsWith("新中国史") }
        assertEquals(9, mooc.startPeriod)
        assertEquals(10, mooc.endPeriod)
        assertEquals(1, mooc.startWeek)
        assertEquals(8, mooc.endWeek)
    }

    @Test
    fun outputIsSortedByWeekdayThenPeriod() {
        val r = TextImporter.parse(
            """
            高等数学|2|1|2|王五|101|1-16
            计算机原理|1|3|4|李四|309|1-16
            大学物理|1|1|2|张三|302|1-16
            """.trimIndent()
        )
        assertEquals(listOf("大学物理", "计算机原理", "高等数学"), r.courses.map { it.name })
    }

    @Test
    fun adjacentRowsOfSameCourseAreMerged() {
        // AI 把跨 5-7 节的课拆成 5-6 与 7-7 两行 → 合并为 5-7
        val r = TextImporter.parse(
            """
            微生物学实验|2|5|6|李向阳||1-16
            微生物学实验|2|7|7|李向阳||1-16
            """.trimIndent()
        )
        assertEquals(1, r.courses.size)
        assertEquals(5, r.courses[0].startPeriod)
        assertEquals(7, r.courses[0].endPeriod)
    }

    @Test
    fun bigPeriodRowsOfSameCourseAreMergedAfterExpansion() {
        // 同一门课占两个大节（1、2）→ 展开为 1-2、3-4 后合并为 1-4
        val r = TextImporter.parse(
            """
            高等数学|1|1|1|张三|101|1-16
            高等数学|1|2|2|张三|101|1-16
            """.trimIndent(), maxPeriod = 10
        )
        assertEquals(1, r.courses.size)
        assertEquals(1, r.courses[0].startPeriod)
        assertEquals(4, r.courses[0].endPeriod)
    }

    // ---- 优化：分隔符自动识别、引号字段 ----

    @Test
    fun delimiterIsAutoDetected() {
        val csv = """
            高等数学,1,1,2,小明,逸夫楼201,1-16
            大学物理,2,3,4,小红,B203,1-8
        """.trimIndent()
        val r = TextImporter.parse(csv)   // 不传 delimiter → 自动识别逗号
        assertEquals(0, r.badLines.size)
        assertEquals(2, r.courses.size)
        assertEquals("小明", r.courses.first { it.name == "高等数学" }.teacher)

        val tsv = "线性代数\t3\t5\t6\t小王\tJ7201\t1-16"
        val t = TextImporter.parse(tsv)
        assertEquals(0, t.badLines.size)
        assertEquals(1, t.courses.size)

        val semi = "计算机组成原理;4;1;2;小李;J8304;1-16"
        assertEquals(1, TextImporter.parse(semi).courses.size)
    }

    @Test
    fun csvWithExtraColumnIsStillDetected() {
        val csv = """
            高等数学,1,1,2,小明,逸夫楼201,1-16,备注
            大学物理,2,3,4,小红,B203,1-8,备注
        """.trimIndent()
        val r = TextImporter.parse(csv)   // 自动识别：多出的备注列不影响
        assertEquals(2, r.courses.size)
        assertEquals("小明", r.courses.first { it.name == "高等数学" }.teacher)
    }

    @Test
    fun quotedCsvFieldsAreSupported() {
        val csv = """
            "课程名称","星期","开始节数","结束节数","老师","地点","周数"
            "高等数学","1","1","2","张三,李四","A101","1-16"
        """.trimIndent()
        val r = TextImporter.parse(csv, delimiter = ',')
        assertEquals(0, r.badLines.size)   // 带引号的表头也要被跳过
        assertEquals(1, r.courses.size)
        assertEquals("高等数学", r.courses[0].name)
        assertEquals("张三,李四", r.courses[0].teacher)   // 引号内的逗号不切列
        assertEquals("A101", r.courses[0].location)
    }

    // ---- 优化：字段写成「周一」「第3-4节」也能读 ----

    @Test
    fun weekdayWordsAndPeriodRangesAreAccepted() {
        val r = TextImporter.parse("高等数学|周三|5|6|张三|A101|1-16")
        assertEquals(0, r.badLines.size)
        assertEquals(3, r.courses[0].weekday)

        // 节次列写成「第5-6节」（起止列留空）也能读
        val r2 = TextImporter.parse("大学物理|2|第5-6节|鲁军旺|J8208||1-16")
        assertEquals(0, r2.badLines.size)
        assertEquals(2, r2.courses[0].weekday)
        assertEquals(5, r2.courses[0].startPeriod)
        assertEquals(6, r2.courses[0].endPeriod)
    }

    @Test
    fun fullWidthDigitsInWeeksAreAccepted() {
        val r = TextImporter.parse("高等数学|1|1|2|小明|A101|１-１６")
        assertEquals(0, r.badLines.size)
        assertEquals(1, r.courses[0].startWeek)
        assertEquals(16, r.courses[0].endWeek)
    }

    // ---- 优化：列序/列数不标准时的语义扫描 ----

    @Test
    fun lenientModeHandlesOddColumnOrder() {
        val r = TextImporter.parse("高等数学|第3-4节|周一|张三|1-16周")
        assertEquals(0, r.badLines.size)
        assertEquals(1, r.courses.size)
        val c = r.courses[0]
        assertEquals(3, c.startPeriod)
        assertEquals(4, c.endPeriod)
        assertEquals(1, c.weekday)
        assertEquals("张三", c.teacher)
    }

    @Test
    fun lenientModeHandlesSixColumns() {
        val r = TextImporter.parse("大学物理|周二|5-6节|李四|B203|1-8周")
        assertEquals(0, r.badLines.size)
        assertEquals(1, r.courses.size)
        val c = r.courses[0]
        assertEquals(2, c.weekday)
        assertEquals(5, c.startPeriod)
        assertEquals(6, c.endPeriod)
        assertEquals("李四", c.teacher)
        assertEquals("B203", c.location)
    }

    @Test
    fun bracketedEvenOddAnnotationIsKept() {
        // 教务系统常见写法：单双周标注在括号里（1-16([双周])），不能被括号剔除逻辑丢掉
        val even = TextImporter.parse("大学物理(下)|2|5|6|鲁军旺|J8208|1-16([双周])")
        assertEquals(WeekType.EVEN, even.courses[0].weekType)
        assertEquals(1, even.courses[0].startWeek)
        assertEquals(16, even.courses[0].endWeek)

        val odd = TextImporter.parse("计算机网络概论|3|5|6|顾怀广|S50603|1-15([单周])")
        assertEquals(WeekType.ODD, odd.courses[0].weekType)
    }

    @Test
    fun lenientModeStillRejectsUnexplainableLines() {
        // 没有带「周」的周次标记时不能靠猜，仍判为坏行
        val r = TextImporter.parse("高等数学|1|1|2|小明|A101|1-16")
        assertEquals(0, r.badLines.size)
        val r2 = TextImporter.parse("一些说明文字|1|2|3")
        assertEquals(1, r2.badLines.size)
        assertTrue(r2.courses.isEmpty())
    }
}
