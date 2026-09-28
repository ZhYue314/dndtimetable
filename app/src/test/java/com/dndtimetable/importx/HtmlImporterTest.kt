package com.dndtimetable.importx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlImporterTest {

    @Test
    fun parsesHtmlGridTable() {
        val html = """
            <html><body>
            <table>
              <tr><td>某某大学课表</td></tr>
              <tr><td></td><td>星期一</td><td>星期二</td><td>星期三</td></tr>
              <tr><td>第一大节</td><td></td><td></td>
                  <td>数字电路与逻辑设计<br>杨洁<br>1-16([周])[01-02节]<br>J7201</td></tr>
              <tr><td>第二大节</td>
                  <td>马克思主义基本原理<br>袁吉萍<br>1-16([周])[03-04节]<br>J7107<br><br>
                      数字电路综合设计实践<br>冉冰心<br>17-18([周])[03-04节]<br>S50602 计算思维与人工智能创新工场</td>
                  <td></td><td></td></tr>
            </table>
            </body></html>
        """.trimIndent()
        val r = HtmlImporter.parse(html, maxPeriod = 10)
        assertEquals(3, r.courses.size)
        val dz = r.courses.first { it.name == "数字电路与逻辑设计" }
        assertEquals(3, dz.weekday)
        assertEquals(1, dz.startPeriod)
        assertEquals(2, dz.endPeriod)
        assertEquals("杨洁", dz.teacher)
        assertEquals("J7201", dz.location)
        val mz = r.courses.first { it.name == "马克思主义基本原理" }
        assertEquals(1, mz.weekday)
        assertEquals(3, mz.startPeriod)
        val sj = r.courses.first { it.name == "数字电路综合设计实践" }
        assertEquals("冉冰心", sj.teacher)
        assertEquals(17, sj.startWeek)
        assertEquals(18, sj.endWeek)
    }

    @Test
    fun parsesHtmlColumnTable() {
        val html = """
            <table>
              <tr><th>星期</th><th>课程名称</th><th>开始节数</th><th>结束节数</th><th>周数</th><th>老师</th><th>地点</th></tr>
              <tr><td>1</td><td>高等数学</td><td>1</td><td>2</td><td>1-16</td><td>张三</td><td>A101</td></tr>
            </table>
        """.trimIndent()
        val r = HtmlImporter.parse(html, maxPeriod = 10)
        assertEquals(1, r.courses.size)
        assertEquals("高等数学", r.courses[0].name)
        assertEquals(1, r.courses[0].weekday)
        assertEquals(1, r.courses[0].startPeriod)
        assertEquals(2, r.courses[0].endPeriod)
    }

    @Test
    fun outOfRangeDiagnosticIsKeptInError() {
        val html = """
            <table>
              <tr><td></td><td>星期一</td><td>星期二</td><td>星期三</td></tr>
              <tr><td>第一大节</td>
                  <td>晚课<br>张三<br>1-16周<br>[11-12节]<br>A101</td>
                  <td></td><td></td></tr>
            </table>
        """.trimIndent()
        val e = try {
            HtmlImporter.parse(html, maxPeriod = 10)
            null
        } catch (ex: Exception) {
            ex
        }
        assertTrue(e != null && e.message!!.contains("节次超出"))
    }

    @Test
    fun picksTableWithMostCourses() {
        // 页面里常先有说明/布局表格：不能取第一个解析出课的，要取课程最多的那张
        val html = """
            <table>
              <tr><th>星期</th><th>课程名称</th></tr>
              <tr><td>周一</td><td>说明行</td></tr>
            </table>
            <table>
              <tr><th>星期一</th><th>星期二</th><th>星期三</th></tr>
              <tr><td>高等数学<br>张三<br>1-16([周])[01-02节]<br>A101</td><td></td><td></td></tr>
              <tr><td></td><td>大学物理<br>李四<br>1-16([周])[03-04节]<br>B202</td><td></td></tr>
            </table>
        """.trimIndent()
        val r = HtmlImporter.parse(html, maxPeriod = 10)
        assertEquals(2, r.courses.size)
    }

    @Test
    fun rowspanIsFilledDown() {
        val html = """
            <table>
              <tr><td>第一大节</td><td>星期一</td><td>星期二</td><td>星期三</td></tr>
              <tr><td rowspan="2">上午</td><td>高等数学<br>张三<br>1-16([周])[01-02节]<br>A101</td><td></td><td></td></tr>
              <tr><td>大学英语<br>李四<br>1-16([周])[03-04节]<br>B202</td><td></td><td></td></tr>
            </table>
        """.trimIndent()
        val r = HtmlImporter.parse(html, maxPeriod = 10)
        assertEquals(2, r.courses.size)
    }
}
