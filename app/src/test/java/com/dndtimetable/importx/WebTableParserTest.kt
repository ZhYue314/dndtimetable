package com.dndtimetable.importx

import com.dndtimetable.data.db.WeekType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTableParserTest {

    /** 与 WebView JS 输出同构：[table][row][col]，空/合并位为 null。 */
    private fun table(vararg rows: Array<Any?>): JSONArray = JSONArray().apply {
        rows.forEach { row ->
            put(JSONArray().apply { row.forEach { put(it ?: JSONObject.NULL) } })
        }
    }

    @Test
    fun picksTheCourseTableOutOfLayoutTables() {
        val json = JSONArray().apply {
            put(table(arrayOf<Any?>("教务系统", "欢迎使用")))
            put(
                table(
                    arrayOf<Any?>("节次", "星期一", "星期二", "星期三"),
                    arrayOf<Any?>("第一大节", "高等数学\n张三\n1-16周 3-4节\nA101", null, null),
                    arrayOf<Any?>("第二大节", null, "大学物理\n李四\n1-16周 5-6节\nB203", null)
                )
            )
        }.toString()
        val tables = WebTableParser.parse(json)!!
        val res = WebTableParser.parseAll(tables, maxPeriod = 10, defaultWeeks = 20)
        assertEquals(2, res.courses.size)
        val math = res.courses.first { it.name == "高等数学" }
        assertEquals(1, math.weekday)
        assertEquals(3, math.startPeriod)
        assertEquals(4, math.endPeriod)
        assertEquals("张三", math.teacher)
        assertEquals("A101", math.location)
        assertEquals(1, math.startWeek)
        assertEquals(16, math.endWeek)
    }

    @Test
    fun blankLineSeparatesTwoCoursesInOneCell() {
        val cell = "高等数学\n张三\n1-16周 3-4节\nA101\n\n大学物理\n李四\n1-16周 5-6节\nB203"
        val json = JSONArray().apply {
            put(
                table(
                    arrayOf<Any?>("节次", "星期一", "星期二", "星期三"),
                    arrayOf<Any?>("第一大节", cell, null, null)
                )
            )
        }.toString()
        val tables = WebTableParser.parse(json)!!
        val res = WebTableParser.parseAll(tables, maxPeriod = 10)
        assertEquals(2, res.courses.size)
        assertTrue(res.courses.any { it.name == "高等数学" && it.location == "A101" })
        assertTrue(res.courses.any { it.name == "大学物理" && it.location == "B203" })
    }

    @Test
    fun outOfRangeCourseSuggestsPeriodCount() {
        val json = JSONArray().apply {
            put(
                table(
                    arrayOf<Any?>("节次", "星期一", "星期二", "星期三"),
                    arrayOf<Any?>("第五大节", "晚课\n1-16周 11-12节", null, null)
                )
            )
        }.toString()
        val res = WebTableParser.parseAll(WebTableParser.parse(json)!!, maxPeriod = 10)
        assertEquals(0, res.courses.size)
        assertEquals(12, res.suggestPeriods)
        assertTrue(res.warnings.any { it.contains("节后重新导入") })
    }

    @Test
    fun mergesCoursesSplitAcrossTablesAndDedupes() {
        // 上午一张表 + 下午一张表（同一页拆开显示）；第二张重复一次模拟打印视图
        val morning = table(
            arrayOf<Any?>("节次", "星期一", "星期二", "星期三"),
            arrayOf<Any?>("第一大节", "高等数学\n张三\n1-16周 1-2节\nA101", null, null)
        )
        val afternoon = table(
            arrayOf<Any?>("节次", "星期一", "星期二", "星期三"),
            arrayOf<Any?>("第三大节", null, "大学物理\n李四\n1-16周 5-6节\nB203", null)
        )
        val json = JSONArray().apply { put(morning); put(afternoon); put(afternoon) }.toString()
        val res = WebTableParser.parseAll(WebTableParser.parse(json)!!, maxPeriod = 10)
        assertEquals(2, res.courses.size)
        assertTrue(res.courses.any { it.name == "高等数学" })
        assertTrue(res.courses.any { it.name == "大学物理" })
    }

    @Test
    fun malformedJsonReturnsNull() {
        assertNull(WebTableParser.parse("not json"))
    }

    @Test
    fun collectsNotesFromCourseNameTableWithoutWeekdays() {
        // 强智课表页的「无课表课程」表：表头有课程名称、没有星期；同名多行要去重
        val notesTable = table(
            arrayOf<Any?>("序号", "上课班级", "课程编号", "课程名称", "授课教师"),
            arrayOf<Any?>("1", "B25计科1班", "TSB0", "形势与政策Ⅲ", "刘栩"),
            arrayOf<Any?>("2", "B25计科1班", "SZB1", "劳动教育", ""),
            arrayOf<Any?>("3", "B25计科1班", "SZB1", "劳动教育", "")
        )
        val json = JSONArray().apply { put(notesTable) }.toString()
        val res = WebTableParser.parseAll(WebTableParser.parse(json)!!, maxPeriod = 10)
        assertEquals(0, res.courses.size)
        assertEquals(listOf("形势与政策Ⅲ", "劳动教育"), res.notes)
    }

    @Test
    fun parsesRealQiangzhiCourseTable() {
        // 真实教务系统（强智科技）页面：每格「汇总行 + 空行 + 明细」，一格多课用一排横线分隔
        val main = table(
            arrayOf<Any?>("", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"),
            arrayOf<Any?>(
                "第一大节",
                "计算机组成原理5(周)J7206\n\n计算机组成原理\n郭勇,叶延婷\n5(周)[01-02节]\nJ7206",
                "",
                "数字电路与逻辑设计1-16(周)J7201\n\n数字电路与逻辑设计\n杨洁\n1-16(周)[01-02节]\nJ7201",
                "计算机组成原理1-2,4-16(周)J8304\n\n计算机组成原理\n郭勇,叶延婷\n1-2,4-16(周)[01-02节]\nJ8304",
                "计算机组成原理1,3,5,7,9,11,13,15(周)S50603 软件工程专业综合实践创新实验室\n\n计算机组成原理\n叶延婷,郭勇\n1,3,5,7,9,11,13,15(周)[01-02节]\nS50603 软件工程专业综合实践创新实验室",
                "", ""
            ),
            arrayOf<Any?>(
                "第二大节",
                "马克思主义基本原理1-16(周)J7107----------------------数字电路综合设计实践17-18(周)S50602 计算思维与人工智能创新工场\n\n马克思主义基本原理\n袁吉萍\n1-16(周)[03-04节]\nJ7107\n\n---------------------\n数字电路综合设计实践\n冉冰心\n17-18(周)[03-04节]\nS50602 计算思维与人工智能创新工场",
                "计算机网络概论1-16(周)J8303----------------------数字电路综合设计实践17-18(周)S50602\n\n计算机网络概论\n顾怀广\n1-16(周)[03-04节]\nJ8303\n\n---------------------\n数字电路综合设计实践\n冉冰心\n17-18(周)[03-04节]\nS50602",
                "大学外语III(25B6B16)1-16(周)J8208\n\n大学外语III\n(25B6B16)\n韦琳\n1-16(周)[03-04节]\nJ8208",
                "算法设计与分析1-16(周)J8403\n\n算法设计与分析\n郭顺超\n1-16(周)[03-04节]\nJ8403",
                "大学体育III(武术男1班)1-16(周)\n\n大学体育III\n(武术男1班)\n吴凡\n1-16(周)[03-04节]",
                "",
                "数字电路与逻辑设计1-16(周)S2502 电子技术基础实验室\n\n数字电路与逻辑设计\n罗华\n1-16(周)[03-04节]\nS2502 电子技术基础实验室"
            ),
            arrayOf<Any?>("备注", "劳动教育 1-20周;形势与政策Ⅲ 刘栩 17-18周;", null, null, null, null, null, null)
        )
        // 同一页还有「学期理论课表（第4周）」等视图表：不能污染导入
        val weekView = table(
            arrayOf<Any?>("周/节次", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"),
            arrayOf<Any?>("第一大节", "", "", "数字电路与...教师：杨洁01~02小节 第4周数字电路与逻辑设计学分：2.501~02节J7201第4周 星期三", "", "", "", ""),
            arrayOf<Any?>("第二大节", "马克思主义...教师：袁吉萍03~04小节 第4周马克思主义基本原理学分：303~04节J7107第4周 星期一", "", "", "", "", "", ""),
            // 带括号的截断课名汇总行：曾产出「大学物理 / 下 / 教师」这种乱课，必须丢弃
            arrayOf<Any?>("第三大节", "", "大学物理（下）教师：鲁军旺05~06小节 第4周大学物理（下）学分：405~06节J8208第4周 星期二", "", "", "", "", "")
        )
        val json = JSONArray().apply { put(weekView); put(main) }.toString()
        val res = WebTableParser.parseAll(WebTableParser.parse(json)!!, maxPeriod = 10)

        // 汇总行/横线行/第4周视图都不能变成课名
        assertTrue(res.courses.none { it.name.contains("----") || it.name.contains("(周)") || it.name.contains("教师：") })
        assertTrue(res.courses.none { it.name == "大学物理" })   // 视图汇总碎句不得产出「大学物理/下/教师」乱课
        assertTrue(res.courses.none { it.name.startsWith("劳动教育") })
        // 横线分隔的第二门课必须导入（周一、周二各一节）
        assertEquals(2, res.courses.count { it.name == "数字电路综合设计实践" && it.startWeek == 17 && it.endWeek == 18 })
        // 计算机组成原理：周一第5周、周四 1-2 与 4-16 两段、周五单周 1-15
        assertEquals(4, res.courses.count { it.name == "计算机组成原理" })
        assertTrue(res.courses.any { it.name == "计算机组成原理" && it.weekday == 5 && it.weekType == WeekType.ODD })
        // (周) 写法与多段周次
        assertTrue(res.courses.any { it.name == "数字电路与逻辑设计" && it.weekday == 3 && it.startWeek == 1 && it.endWeek == 16 })
        // 班级名括号并入课名，不再混进老师
        assertTrue(res.courses.any { it.name == "大学外语III(25B6B16)" && it.teacher == "韦琳" })
        assertTrue(res.courses.any { it.name == "大学体育III(武术男1班)" && it.teacher == "吴凡" })
        assertTrue(res.courses.any { it.name == "计算机网络概论" && it.location == "J8303" })
    }
}
