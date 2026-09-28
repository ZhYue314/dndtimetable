package com.dndtimetable.importx

import com.dndtimetable.data.db.WeekType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * XlsxImporter 解析测试：解析实现已从 android.util.Xml 换成 javax.xml（DOM），
 * 可以在这里内存里现造一个 xlsx（ZIP+XML）直接跑，不再依赖真机导入。
 */
class XlsxImporterTest {

    private fun xlsx(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(bos.toByteArray())
    }

    @Test
    fun parsesMatrixSheetWithSharedStrings() {
        val shared = """<?xml version="1.0" encoding="UTF-8"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="6" uniqueCount="6">
<si><t>节次</t></si><si><t>星期一</t></si><si><t>星期二</t></si><si><t>星期三</t></si>
<si><t>第一大节</t></si><si><t>高等数学&#10;张三&#10;1-16周 3-4节&#10;A101</t></si>
</sst>"""
        val sheet = """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c><c r="D1" t="s"><v>3</v></c></row>
<row r="2"><c r="A2" t="s"><v>4</v></c><c r="B2" t="s"><v>5</v></c></row>
</sheetData></worksheet>"""
        val res = XlsxImporter.parse(
            xlsx("xl/sharedStrings.xml" to shared, "xl/worksheets/sheet1.xml" to sheet),
            maxPeriod = 10
        )
        assertEquals(1, res.courses.size)
        val c = res.courses[0]
        assertEquals("高等数学", c.name)
        assertEquals(1, c.weekday)
        assertEquals(3, c.startPeriod)
        assertEquals(4, c.endPeriod)
        assertEquals("张三", c.teacher)
        assertEquals("A101", c.location)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
    }

    @Test
    fun parsesTemplateSheetWithInlineStringsAndNotesSheet() {
        val sheet = """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
<row r="1">
<c r="A1" t="inlineStr"><is><t>课程名称</t></is></c><c r="B1" t="inlineStr"><is><t>星期</t></is></c>
<c r="C1" t="inlineStr"><is><t>开始节数</t></is></c><c r="D1" t="inlineStr"><is><t>结束节数</t></is></c>
<c r="E1" t="inlineStr"><is><t>老师</t></is></c><c r="F1" t="inlineStr"><is><t>地点</t></is></c>
<c r="G1" t="inlineStr"><is><t>周数</t></is></c>
</row>
<row r="2">
<c r="A2" t="inlineStr"><is><t>大学物理</t></is></c><c r="B2" t="inlineStr"><is><t>2</t></is></c>
<c r="C2" t="inlineStr"><is><t>5</t></is></c><c r="D2" t="inlineStr"><is><t>6</t></is></c>
<c r="E2" t="inlineStr"><is><t>李四</t></is></c><c r="F2" t="inlineStr"><is><t>B203</t></is></c>
<c r="G2" t="inlineStr"><is><t>1-16双</t></is></c>
</row>
</sheetData></worksheet>"""
        // 第二个 sheet「无课表课程」：D 列是课程名，导入为备注
        val notesSheet = """<?xml version="1.0" encoding="UTF-8"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
<row r="1"><c r="D1" t="inlineStr"><is><t>课程名称</t></is></c></row>
<row r="2"><c r="A2" t="inlineStr"><is><t>1</t></is></c><c r="D2" t="inlineStr"><is><t>劳动教育</t></is></c></row>
</sheetData></worksheet>"""
        val res = XlsxImporter.parse(
            xlsx("xl/worksheets/sheet1.xml" to sheet, "xl/worksheets/sheet2.xml" to notesSheet),
            maxPeriod = 10,
            defaultWeeks = 20
        )
        assertEquals(1, res.courses.size)
        val c = res.courses[0]
        assertEquals("大学物理", c.name)
        assertEquals(2, c.weekday)
        assertEquals(5, c.startPeriod)
        assertEquals(6, c.endPeriod)
        assertEquals(WeekType.EVEN, c.weekType)
        assertEquals("李四", c.teacher)
        assertEquals("B203", c.location)
        assertTrue(res.notes.contains("劳动教育"))
    }

    @Test
    fun brokenSheetDoesNotThrow() {
        val res = XlsxImporter.parse(
            xlsx("xl/worksheets/sheet1.xml" to "<worksheet><sheetData><row r=\"1\"><c r=\"A1\""),
            maxPeriod = 10
        )
        assertEquals(0, res.courses.size)
    }
}
