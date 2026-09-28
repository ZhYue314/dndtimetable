package com.dndtimetable.importx

import com.dndtimetable.data.db.Course

/**
 * HTML 表格课表解析：不少教务系统「导出 Excel」实际是 HTML 表格（后缀 .xls/.html）。
 * 依次尝试每个 <table>：先按表头列名（GridParser.parseTable），再按「星期×大节」网格（GridParser.parseGrid）。
 * 支持 <br> 换行、colspan/rowspan（合并单元格）。
 */
object HtmlImporter {

    private val tableRe = Regex("<table[^>]*>(.*?)</table>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val rowRe = Regex("<tr[^>]*>(.*?)</tr>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val cellRe = Regex("<t[dh]([^>]*)>(.*?)</t[dh]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tagRe = Regex("<[^>]+>")
    private val brRe = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
    private val spanRe = Regex("(row|col)span\\s*=\\s*[\"']?(\\d+)", RegexOption.IGNORE_CASE)

    fun isHtml(text: String): Boolean {
        val head = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t').take(500).lowercase()
        return head.startsWith("<!doctype") || head.startsWith("<html") || head.startsWith("<table") || head.contains("<table")
    }

    fun parse(text: String, maxPeriod: Int, defaultWeeks: Int = 0): XlsImporter.Result {
        val tables = tableRe.findAll(text).map { it.groupValues[1] }.toList()
        if (tables.isEmpty()) throw Exception("HTML 里没有找到表格")
        var bestCourses: List<Course> = emptyList()
        var bestWarnings: List<String> = emptyList()
        var bestSuggest: Int? = null
        var fallback: List<String> = emptyList()   // 所有表格都没解析出课程时的诊断
        var fallbackSuggest: Int? = null
        for (t in tables) {
            val cells = extractCells(t)
            if (cells.isEmpty()) continue
            // 先按表头列名（扁平列表），再按「星期×大节」网格，最后退模板固定列
            val out = GridParser.parseCascadeOutcome(cells, maxPeriod, defaultWeeks)
            // 页面里常有说明/布局表格：取解析出课程最多的那张，而不是第一个成功的
            if (out.courses.size > bestCourses.size) {
                bestCourses = out.courses
                bestWarnings = out.warnings
                bestSuggest = out.suggestedPeriods
            }
            if (out.courses.isEmpty() && out.warnings.size > fallback.size) {
                fallback = out.warnings
                fallbackSuggest = out.suggestedPeriods
            }
        }
        if (bestCourses.isNotEmpty()) return XlsImporter.Result(bestCourses, emptyList(), bestWarnings, bestSuggest)
        throw Exception(
            "未识别到课程：HTML 表格里没有找到课程行" +
                if (fallback.isEmpty()) "" else "；" + fallback.joinToString("；")
        )
    }

    private fun extractCells(tableHtml: String): Map<Pair<Int, Int>, String> {
        val cells = HashMap<Pair<Int, Int>, String>()
        val pending = HashMap<Int, Pair<String, Int>>()   // col -> (文本, 剩余行数)：rowspan 向下填充
        var row = 0
        rowRe.findAll(tableHtml).forEach { tr ->
            val occupied = pending.keys.toSet()
            val parsed = ArrayList<Pair<Int, String>>()
            val newSpans = HashMap<Int, Pair<String, Int>>()
            var col = 0
            cellRe.findAll(tr.groupValues[1]).forEach { c ->
                while (col in occupied) col++   // 跳过被 rowspan 占用的列
                val attrs = c.groupValues[1]
                var colSpan = 1
                var rowSpan = 1
                spanRe.findAll(attrs).forEach { m ->
                    val n = m.groupValues[2].toIntOrNull() ?: 1
                    if (m.groupValues[1].equals("col", true)) colSpan = n else rowSpan = n
                }
                val txt = cleanCell(c.groupValues[2])
                parsed.add(col to txt)
                if (rowSpan > 1 && txt.isNotEmpty()) newSpans[col] = txt to (rowSpan - 1)
                col += colSpan
            }
            for (c in (occupied + parsed.map { it.first }).sorted()) {
                val v = parsed.firstOrNull { it.first == c }?.second ?: pending[c]?.first.orEmpty()
                if (v.isNotEmpty()) cells[row to c] = v
            }
            pending.replaceAll { _, v -> v.first to (v.second - 1) }
            pending.entries.removeAll { it.value.second <= 0 }
            pending.putAll(newSpans)
            row++
        }
        return cells
    }

    private fun cleanCell(html: String): String =
        brRe.replace(html, "\n")
            .replace(tagRe, "")
            .let { decodeEntities(it) }
            .lines().joinToString("\n") { it.trim().trim('\u00A0') }
            .trim('\n', ' ', '\u00A0')

    private fun decodeEntities(s: String): String {
        var t = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        t = Regex("&#x([0-9a-fA-F]+);").replace(t) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
        }
        t = Regex("&#(\\d+);").replace(t) { m ->
            m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
        }
        return t
    }
}
