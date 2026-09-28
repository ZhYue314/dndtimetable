package com.dndtimetable.importx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分享头（`#名称/#开学/#周数`）解析：App「生成分享文字」导出的头部元数据。 */
class TextImporterMetaTest {

    @Test
    fun `分享头解析进 meta 且不进 badLines`() {
        val text = """
            #dndtimetable 课表
            #名称: 宿舍四人课表
            #开学: 2026-09-07
            #周数: 16
            课程名称|星期|开始节数|结束节数|老师|地点|周数
            高等数学|2|1|2|张三|302|1-16
        """.trimIndent()
        val r = TextImporter.parse(text, 12)
        assertEquals(1, r.courses.size)
        assertTrue(r.badLines.isEmpty())
        assertEquals("宿舍四人课表", r.meta?.name)
        assertEquals(java.time.LocalDate.of(2026, 9, 7).toEpochDay(), r.meta?.startEpochDay)
        assertEquals(16, r.meta?.totalWeeks)
    }

    @Test
    fun `无分享头 meta 为 null（AI 生成文字照常解析）`() {
        val r = TextImporter.parse("大学英语|3|3|4|李四|101|1-8", 12)
        assertEquals(1, r.courses.size)
        assertNull(r.meta)
    }

    @Test
    fun `全角冒号与非法值容错`() {
        val text = """
            #开学：2026-09-07
            #周数: abc
            #未知键: 忽略
            高等数学|2|1|2|张三|302|1-16
        """.trimIndent()
        val r = TextImporter.parse(text, 12)
        assertEquals(1, r.courses.size)
        assertTrue(r.badLines.isEmpty())
        assertEquals(java.time.LocalDate.of(2026, 9, 7).toEpochDay(), r.meta?.startEpochDay)
        assertNull(r.meta?.totalWeeks)   // 非法周数忽略
        assertNull(r.meta?.name)
    }
}
