package com.dndtimetable.importx

import com.dndtimetable.data.db.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridParserTest {

    @Test
    fun singleLineCellParsedConservatively() {
        val cells = mapOf(
            (0 to 0) to "星期一", (0 to 1) to "星期二", (0 to 2) to "星期三",
            (1 to 0) to "第一大节",
            (1 to 1) to "高等数学(张三) 1-16周 3-4节 教一101"
        )
        val courses = GridParser.parseGrid(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals("高等数学", c.name)
        assertEquals("张三", c.teacher)
        assertEquals("教一101", c.location)
        assertEquals(3, c.startPeriod)
        assertEquals(4, c.endPeriod)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
    }

    @Test
    fun tableColumnsAreMappedByHeader() {
        val cells = mapOf(
            (0 to 0) to "教师", (0 to 1) to "课程", (0 to 2) to "上课周次",
            (0 to 3) to "星期", (0 to 4) to "节次", (0 to 5) to "上课地点",
            (1 to 0) to "李四", (1 to 1) to "大学物理", (1 to 2) to "1-16双",
            (1 to 3) to "周二", (1 to 4) to "第5-6节", (1 to 5) to "B203"
        )
        val courses = GridParser.parseTable(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        val c = courses[0]
        assertEquals("大学物理", c.name)
        assertEquals(2, c.weekday)
        assertEquals(5, c.startPeriod)
        assertEquals(6, c.endPeriod)
        assertEquals(WeekType.EVEN, c.weekType)
        assertEquals("李四", c.teacher)
        assertEquals("B203", c.location)
    }

    @Test
    fun gridWithoutBigPeriodLabelsStillParses() {
        val cells = mapOf(
            (0 to 0) to "周一", (0 to 1) to "周二", (0 to 2) to "周三",
            (1 to 1) to "高等数学\n张三\n1-16([周])[03-04节]\nA101"
        )
        val courses = GridParser.parseGrid(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals("高等数学", courses[0].name)
        assertEquals(2, courses[0].weekday)
        assertEquals(3, courses[0].startPeriod)
        assertEquals(4, courses[0].endPeriod)
    }

    // ---- 优化：节次只在行标签里（单元格没写节次）----

    @Test
    fun rowLabelSuppliesPeriodsWhenCellHasNone() {
        // 真实教务系统布局：第 0 列是「大节」行标签，星期从第 1 列开始
        val cells = mapOf(
            (0 to 1) to "星期一", (0 to 2) to "星期二", (0 to 3) to "星期三",
            (1 to 0) to "第一大节",
            (1 to 1) to "高等数学\n张三\n1-16周\n教一101",
            (2 to 0) to "第二大节",
            (2 to 3) to "大学物理\n李四\n1-16周\nB203"
        )
        val courses = GridParser.parseGrid(cells, maxPeriod = 10)
        assertEquals(2, courses.size)
        val math = courses.first { it.name == "高等数学" }
        assertEquals(1, math.weekday)
        assertEquals(1, math.startPeriod)
        assertEquals(2, math.endPeriod)
        assertEquals("张三", math.teacher)
        assertEquals("教一101", math.location)
        val physics = courses.first { it.name == "大学物理" }
        assertEquals(3, physics.weekday)
        assertEquals(3, physics.startPeriod)
        assertEquals(4, physics.endPeriod)
        assertEquals("李四", physics.teacher)
        assertEquals("B203", physics.location)
    }

    @Test
    fun explicitPeriodRangeLabelIsUsedAsFallback() {
        val cells = mapOf(
            (0 to 1) to "周一", (0 to 2) to "周二", (0 to 3) to "周三",
            (1 to 0) to "第3-4节",
            (1 to 2) to "线性代数\n王五\n2-16([双周])\nA101"
        )
        val courses = GridParser.parseGrid(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals(2, courses[0].weekday)
        assertEquals(3, courses[0].startPeriod)
        assertEquals(4, courses[0].endPeriod)
        assertEquals(2, courses[0].startWeek)
        assertEquals(WeekType.EVEN, courses[0].weekType)
    }

    @Test
    fun weekdayHeaderToleratesAnnotation() {
        val cells = mapOf(
            (0 to 1) to "星期一(Mon)", (0 to 2) to "星期二 Tue", (0 to 3) to "星期三",
            (1 to 0) to "第一大节",
            (1 to 3) to "高等数学\n张三\n1-16周\nJ7201"
        )
        val courses = GridParser.parseGrid(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals(3, courses[0].weekday)
        assertEquals("J7201", courses[0].location)
    }

    @Test
    fun dayNumberAcceptsTolerantFormsButRejectsOutOfRange() {
        assertEquals(1, GridParser.dayNumber("周一"))
        assertEquals(1, GridParser.dayNumber("星期一"))
        assertEquals(1, GridParser.dayNumber("礼拜一"))
        assertEquals(3, GridParser.dayNumber("星期三(Wed)"))
        assertEquals(5, GridParser.dayNumber(" 周五 "))
        assertEquals(7, GridParser.dayNumber("周日"))
        assertEquals(7, GridParser.dayNumber("天"))
        assertNull(GridParser.dayNumber("8"))
        assertNull(GridParser.dayNumber("星期"))
        assertNull(GridParser.dayNumber(""))
        assertNull(GridParser.dayNumber("数字电路"))
    }

    @Test
    fun periodsFromLabelHandlesBigAndExplicitLabels() {
        assertEquals(1 to 2, GridParser.periodsFromLabel("第一大节"))
        assertEquals(1 to 2, GridParser.periodsFromLabel("大一大节"))   // 教务系统常见笔误
        assertEquals(3 to 4, GridParser.periodsFromLabel("第二大节"))
        assertEquals(9 to 10, GridParser.periodsFromLabel("第五大节"))
        assertEquals(7 to 8, GridParser.periodsFromLabel("大节4"))
        assertEquals(3 to 4, GridParser.periodsFromLabel("第3-4节"))
        assertEquals(3 to 4, GridParser.periodsFromLabel("[03-04节]"))
        assertEquals(3 to 4, GridParser.periodsFromLabel("3-4"))
        assertEquals(5 to 5, GridParser.periodsFromLabel("第5节"))
        assertNull(GridParser.periodsFromLabel("1-16周"))   // 周次不能被当成节次
        assertNull(GridParser.periodsFromLabel("上午"))
        assertNull(GridParser.periodsFromLabel("备注"))
    }

    @Test
    fun clockTimeInRowLabelIsNotMistakenForPeriods() {
        // 大节标签常带上上课时间；分钟数不能被当节次（9:50 的 50、13:30 的 30）
        assertEquals(1 to 2, GridParser.periodsFromLabel("第一大节 9:50-11:25"))
        assertEquals(5 to 6, GridParser.periodsFromLabel("第三大节 13:30-15:05"))
        assertNull(GridParser.periodsFromLabel("9:50-11:25"))
    }

    @Test
    fun rowLabelWithClockTimeStillSuppliesPeriods() {
        val cells = mapOf(
            (0 to 1) to "星期一", (0 to 2) to "星期二", (0 to 3) to "星期三",
            (1 to 0) to "第一大节 9:50-11:25",
            (1 to 1) to "高等数学\n张三\n1-16周\n教一101"
        )
        val courses = GridParser.parseGrid(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].startPeriod)
        assertEquals(2, courses[0].endPeriod)
        assertEquals("教一101", courses[0].location)
    }

    @Test
    fun cascadeKeepsOutOfRangeWarningWhenAllStrategiesFail() {
        val cells = mapOf(
            (0 to 1) to "星期一", (0 to 2) to "星期二", (0 to 3) to "星期三",
            (1 to 0) to "第一大节",
            (1 to 1) to "晚课\n张三\n1-16周\n[11-12节]\nA101"
        )
        val (courses, warnings) = GridParser.parseCascade(cells, maxPeriod = 10)
        assertTrue(courses.isEmpty())
        // 真因（节次超出）不能被后一次兜底策略的空诊断洗掉
        assertTrue(warnings.any { it.contains("节次超出") })
    }

    @Test
    fun secondLabelColumnSuppliesPeriods() {
        // 星期列左边有两列（上午 + 第3-4节）时，要取能解析出节次的那格
        val cells = mapOf(
            (0 to 2) to "星期一", (0 to 3) to "星期二", (0 to 4) to "星期三",
            (1 to 0) to "上午", (1 to 1) to "第3-4节",
            (1 to 2) to "线性代数\n王五\n2-16周\nA101"
        )
        val courses = GridParser.parseGrid(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals(3, courses[0].startPeriod)
        assertEquals(4, courses[0].endPeriod)
        assertEquals("王五", courses[0].teacher)
    }

    @Test
    fun unreadableWeekdayIsReportedButBlankRowsAreNot() {
        val cells = mapOf(
            (0 to 0) to "课程", (0 to 1) to "星期", (0 to 2) to "节次", (0 to 3) to "周数",
            (1 to 0) to "高等数学", (1 to 1) to "周一", (1 to 2) to "第1-2节", (1 to 3) to "1-16",
            (2 to 0) to "大学物理", (2 to 1) to "星期八", (2 to 2) to "第3-4节", (2 to 3) to "1-16",
            (3 to 0) to "说明：本表仅供预览"   // 表尾说明行，星期列空 → 不计诊断
        )
        val report = GridParser.Report()
        val courses = GridParser.parseTable(cells, maxPeriod = 10, report = report)
        assertEquals(1, courses.size)
        assertEquals(1, report.noWeekday)
        assertTrue(report.warnings().any { it.contains("没读到星期") })
    }

    // ---- 优化：教务系统常见单元格格式 ----

    @Test
    fun jieRangeAndAnnotatedWeeksInCellAreRead() {
        // 「第3节-第4节」不能被截成第3节；「4-18(双)周」的单双标注在「周」前
        val cells = mapOf(
            (0 to 1) to "星期一", (0 to 2) to "星期二", (0 to 3) to "星期三",
            (1 to 1) to "大学英语\n吉胜芬\n4-18(双)周\n开物楼1415\n第3节-第4节"
        )
        val courses = GridParser.parseGrid(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals(3, courses[0].startPeriod)
        assertEquals(4, courses[0].endPeriod)
        assertEquals(WeekType.EVEN, courses[0].weekType)
        assertEquals(4, courses[0].startWeek)
        assertEquals(18, courses[0].endWeek)
    }

    @Test
    fun multiRangeWeeksInOneCellAreAllKept() {
        // 「2-5周,7-19周」两段都要读出来，而不是只读第一段
        val cells = mapOf(
            (0 to 1) to "星期一", (0 to 2) to "星期二", (0 to 3) to "星期三",
            (1 to 1) to "Linux服务器配置与应用\n曹雅茜\n2-5周,7-19周\n第1节-第4节\nS419"
        )
        val courses = GridParser.parseGrid(cells, maxPeriod = 12)
        assertEquals(2, courses.size)
        assertTrue(courses.all { it.startPeriod == 1 && it.endPeriod == 4 })
        assertEquals(listOf(2, 7), courses.map { it.startWeek })
    }

    @Test
    fun weekAnnotationsBeforeOrAfterZhouAreKept() {
        assertEquals(WeekType.EVEN, WeekParser.parse("2-16周(双)")[0].type)
        assertEquals(WeekType.EVEN, WeekParser.parse("4-18(双)周")[0].type)
        val multi = WeekParser.parse("1-3周(单),4周,9周")
        assertEquals(3, multi.size)
        assertEquals(WeekType.ODD, multi[0].type)
        assertEquals(Triple(1, 3, WeekType.ODD), Triple(multi[0].start, multi[0].end, multi[0].type))
        assertEquals(4, multi[1].start)
        assertEquals(9, multi[2].start)
    }

    @Test
    fun ellipsisWeeksAreFilled() {
        val segs = WeekParser.parse("1、3、5…15")
        assertEquals(1, segs.size)
        assertEquals(1, segs[0].start)
        assertEquals(15, segs[0].end)
        assertEquals(WeekType.ODD, segs[0].type)
    }

    @Test
    fun rowLabelWithOnlyPeriodNumberSuppliesPeriods() {
        // 节次列只写「3」+上课时间（前面还有「上午」分组列）时，也要能推出节次
        val cells = mapOf(
            (0 to 2) to "星期一", (0 to 3) to "星期二", (0 to 4) to "星期三",
            (1 to 0) to "上午", (1 to 1) to "3\n10:30\n11:15",
            (1 to 2) to "大学生职业生涯规划（理论）\n孙秋韵 8,13周\n开物楼1201",
            (2 to 1) to "5-6\n14:00\n15:40",
            (2 to 2) to "线性代数\n王五 1-16周\nJ201"
        )
        val courses = GridParser.parseGrid(cells, maxPeriod = 10)
        assertEquals(3, courses.size)   // 8 周与 13 周各一条 + 线性代数
        assertEquals(2, courses.count { it.startPeriod == 3 && it.endPeriod == 3 })
        val linear = courses.first { it.name == "线性代数" }
        assertEquals(5, linear.startPeriod)
        assertEquals(6, linear.endPeriod)
        assertEquals(1, courses[0].weekday)
        assertEquals("开物楼1201", courses[0].location)
    }

    @Test
    fun bigPeriodConversionIsReported() {
        val cells = mapOf(
            (0 to 0) to "课程", (0 to 1) to "星期", (0 to 2) to "节次", (0 to 3) to "周数",
            (1 to 0) to "体育", (1 to 1) to "1", (1 to 2) to "3", (1 to 3) to "1-16"
        )
        val report = GridParser.Report()
        val courses = GridParser.parseTable(cells, maxPeriod = 10, report = report)
        assertEquals(1, courses.size)
        assertEquals(5, courses[0].startPeriod)
        assertEquals(6, courses[0].endPeriod)
        assertTrue(report.warnings().any { it.contains("大节") })
    }

    // ---- 优化：全角、缺周次列、周次藏在节次格里 ----

    @Test
    fun fullWidthDigitsAreNormalized() {
        val cells = mapOf(
            (0 to 0) to "课程名称", (0 to 1) to "星期", (0 to 2) to "开始节数",
            (0 to 3) to "结束节数", (0 to 4) to "老师", (0 to 5) to "地点", (0 to 6) to "周数",
            (1 to 0) to "高等数学", (1 to 1) to "１", (1 to 2) to "３", (1 to 3) to "４",
            (1 to 4) to "张三", (1 to 5) to "A101", (1 to 6) to "１-１６"
        )
        val courses = GridParser.parseTable(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].weekday)
        assertEquals(3, courses[0].startPeriod)
        assertEquals(16, courses[0].endWeek)
    }

    @Test
    fun tableWithoutWeekColumnUsesDefaultWeeks() {
        val cells = mapOf(
            (0 to 0) to "课程", (0 to 1) to "星期", (0 to 2) to "节次", (0 to 3) to "老师",
            (1 to 0) to "高等数学", (1 to 1) to "周一", (1 to 2) to "第1-2节", (1 to 3) to "张三"
        )
        assertEquals(0, GridParser.parseTable(cells, maxPeriod = 10).size)   // 没有周次且无兜底 → 不猜
        val courses = GridParser.parseTable(cells, maxPeriod = 10, defaultWeeks = 18)
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].startWeek)
        assertEquals(18, courses[0].endWeek)
    }

    @Test
    fun weeksInsidePeriodCellAreUsed() {
        val cells = mapOf(
            (0 to 0) to "课程名称", (0 to 1) to "星期", (0 to 2) to "节次",
            (1 to 0) to "高等数学", (1 to 1) to "1", (1 to 2) to "3-4节(1-16周)"
        )
        val courses = GridParser.parseTable(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals(3, courses[0].startPeriod)
        assertEquals(4, courses[0].endPeriod)
        assertEquals(1, courses[0].startWeek)
        assertEquals(16, courses[0].endWeek)
    }

    @Test
    fun bracketedEvenWeeksInWeekColumnAreKept() {
        // 周数列写成「1-16([双周])」：单双周标注在括号里，不能被括号剔除逻辑丢掉
        val cells = mapOf(
            (0 to 0) to "课程名称", (0 to 1) to "星期", (0 to 2) to "开始节数",
            (0 to 3) to "结束节数", (0 to 6) to "周数",
            (1 to 0) to "大学物理", (1 to 1) to "2", (1 to 2) to "5", (1 to 3) to "6",
            (1 to 6) to "1-16([双周])"
        )
        val courses = GridParser.parseTable(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals(WeekType.EVEN, courses[0].weekType)
        assertEquals(1, courses[0].startWeek)
        assertEquals(16, courses[0].endWeek)
    }

    @Test
    fun outOfRangeCoursesAreReported() {
        val cells = mapOf(
            (0 to 0) to "课程", (0 to 1) to "星期", (0 to 2) to "节次", (0 to 3) to "周数",
            (1 to 0) to "晚课", (1 to 1) to "1", (1 to 2) to "11-12节", (1 to 3) to "1-16"
        )
        val report = GridParser.Report()
        assertEquals(0, GridParser.parseTable(cells, maxPeriod = 10, report = report).size)
        assertEquals(1, report.outOfRange)
        assertEquals(12, report.maxPeriodSeen)
        val warnings = report.warnings()
        assertTrue(warnings.any { it.contains("第 12 节") })
        // 提示必须指向真实存在的入口（「节次设置」这个页面并不存在）
        assertTrue(warnings.any { it.contains("节数") })
        assertTrue(warnings.any { it.contains("12 节") })
    }

    @Test
    fun outOfRangeBeyondSupportedMaxIsReported() {
        val cells = mapOf(
            (0 to 0) to "课程", (0 to 1) to "星期", (0 to 2) to "节次", (0 to 3) to "周数",
            (1 to 0) to "晚课", (1 to 1) to "1", (1 to 2) to "19-20节", (1 to 3) to "1-16"
        )
        val report = GridParser.Report()
        assertEquals(0, GridParser.parseTable(cells, maxPeriod = 10, report = report).size)
        assertEquals(20, report.maxPeriodSeen)
        // 需求超过 App 支持的节数上限时，不能再让用户去调「节数」
        assertTrue(report.warnings().any { it.contains("上限") })
    }

    // ---- 优化：模板固定列兜底 ----

    @Test
    fun fixedColumnsFallbackReadsTemplateLayout() {
        val cells = mapOf(
            (0 to 0) to "课程名称", (0 to 1) to "星期", (0 to 2) to "开始节数", (0 to 3) to "结束节数",
            (0 to 4) to "老师", (0 to 5) to "地点", (0 to 6) to "周数",
            (1 to 0) to "高等数学", (1 to 1) to "2", (1 to 2) to "3", (1 to 3) to "4",
            (1 to 4) to "张三", (1 to 5) to "A101", (1 to 6) to "1-16"
        )
        val courses = GridParser.parseFixedColumns(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals(2, courses[0].weekday)
        assertEquals(3, courses[0].startPeriod)
        assertEquals("张三", courses[0].teacher)
        assertEquals("A101", courses[0].location)
    }

    @Test
    fun fixedColumnsAcceptsSinglePeriodColumn() {
        val cells = mapOf(
            (0 to 0) to "名称", (0 to 1) to "星期", (0 to 2) to "节次",
            (0 to 4) to "老师", (0 to 6) to "周次",
            (1 to 0) to "大学物理", (1 to 1) to "周三", (1 to 2) to "5-6节",
            (1 to 4) to "李四", (1 to 6) to "1-8"
        )
        val courses = GridParser.parseFixedColumns(cells, maxPeriod = 10)
        assertEquals(1, courses.size)
        assertEquals(3, courses[0].weekday)
        assertEquals(5, courses[0].startPeriod)
        assertEquals(6, courses[0].endPeriod)
        assertEquals("李四", courses[0].teacher)
    }

    // ---- 超节次表：结构化建议（UI 一键调整用） ----

    @Test
    fun outOfRangeCourseSuggestsEvenPeriodCount() {
        val cells = mapOf(
            (0 to 0) to "课程", (0 to 1) to "星期", (0 to 2) to "节次", (0 to 3) to "周数",
            (1 to 0) to "晚课", (1 to 1) to "1", (1 to 2) to "11-12节", (1 to 3) to "1-16"
        )
        val out = GridParser.parseCascadeOutcome(cells, maxPeriod = 10)
        assertEquals(0, out.courses.size)
        assertEquals(12, out.suggestedPeriods)
        assertTrue(out.warnings.any { it.contains("节后重新导入") })
    }

    @Test
    fun beyondSupportedMaxHasNoSuggestion() {
        val cells = mapOf(
            (0 to 0) to "课程", (0 to 1) to "星期", (0 to 2) to "节次", (0 to 3) to "周数",
            (1 to 0) to "晚课", (1 to 1) to "1", (1 to 2) to "19-20节", (1 to 3) to "1-16"
        )
        val out = GridParser.parseCascadeOutcome(cells, maxPeriod = 10)
        assertNull(out.suggestedPeriods)   // 超过 App 上限：调节数也救不了，不给按钮
    }

    @Test
    fun inRangeImportHasNoSuggestion() {
        val cells = mapOf(
            (0 to 0) to "课程", (0 to 1) to "星期", (0 to 2) to "节次", (0 to 3) to "周数",
            (1 to 0) to "高数", (1 to 1) to "1", (1 to 2) to "1-2节", (1 to 3) to "1-16"
        )
        val out = GridParser.parseCascadeOutcome(cells, maxPeriod = 10)
        assertEquals(1, out.courses.size)
        assertNull(out.suggestedPeriods)
    }

    // ---- 强智页面细节：视图汇总行丢弃 / 班级名括号并入课名 ----

    @Test
    fun weekViewSummaryLineIsDropped() {
        // 「学期理论课表（第 N 周）」的汇总碎句：名字截断 + 教师/学分粘连，不能按单行课解析
        val cells = mapOf(
            (0 to 1) to "星期一", (0 to 2) to "星期二", (0 to 3) to "星期三",
            (1 to 0) to "第三大节",
            (1 to 2) to "大学物理（下）教师：鲁军旺05~06小节 第4周大学物理（下）学分：405~06节J8208第4周 星期二"
        )
        val list = GridParser.parseGrid(cells, maxPeriod = 10)
        assertTrue(list.isEmpty())
    }

    @Test
    fun classNameParenthesesMergeIntoCourseName() {
        // 明细里单独一行的班级名（如「(25B6B16)」）并入课名，别当老师
        val cells = mapOf(
            (0 to 1) to "星期一", (0 to 2) to "星期二", (0 to 3) to "星期三",
            (1 to 0) to "第二大节",
            (1 to 1) to "大学外语III\n(25B6B16)\n韦琳\n1-16周[03-04节]\nJ8208"
        )
        val list = GridParser.parseGrid(cells, maxPeriod = 10)
        assertEquals(1, list.size)
        assertEquals("大学外语III(25B6B16)", list[0].name)
        assertEquals("韦琳", list[0].teacher)
        assertEquals("J8208", list[0].location)
    }
}
