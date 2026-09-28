package com.dndtimetable.importx

import com.dndtimetable.data.db.Course
import org.json.JSONArray

/**
 * 教务网页导入的解析：WebView 里的 JS 按 DOM 展开合并单元格/嵌套表格后，
 * 返回三维数组 `[table][row][col]`（文本或 null），这里转成 cells map 再复用 [GridParser]。
 * 走 DOM 网格而不是 HTML 正则——教务系统课表常见「表格套表格」，
 * 正则按第一个 `</table>` 截断必然读错，而浏览器已经帮我们算好了 rowspan/colspan。
 */
object WebTableParser {

    /** JS 抓取的 JSON → 每张表的 cells map（跳过空表）；JSON 非法返回 null（页面抓取失败）。 */
    fun parse(json: String): List<Map<Pair<Int, Int>, String>>? {
        val root = runCatching { JSONArray(json) }.getOrNull() ?: return null
        val tables = ArrayList<Map<Pair<Int, Int>, String>>(root.length())
        for (ti in 0 until root.length()) {
            val table = root.optJSONArray(ti) ?: continue
            val cells = HashMap<Pair<Int, Int>, String>()
            for (r in 0 until table.length()) {
                val row = table.optJSONArray(r) ?: continue
                for (c in 0 until row.length()) {
                    if (row.isNull(c)) continue
                    val text = row.optString(c, "").trim()
                    if (text.isNotEmpty()) cells[r to c] = text
                }
            }
            if (cells.isNotEmpty()) tables.add(cells)
        }
        return tables
    }

    /**
     * 解析**所有**表格并合并去重：一页课表常被拆成多张表（上午/下午分开、每天一张、单双周两张……），
     * 只取「课程最多的一张」会缺胳膊少腿。诊断取解析出课程最多那张；全空时取证据最多的一份。
     * 不做模板固定列兜底：网页里的布局表太多，按列位硬读容易产出垃圾课。
     */
    fun parseAll(
        tables: List<Map<Pair<Int, Int>, String>>,
        maxPeriod: Int,
        defaultWeeks: Int = 0
    ): XlsxImporter.Result {
        var bestCount = 0
        var bestWarnings: List<String> = emptyList()
        var bestSuggest: Int? = null
        val merged = LinkedHashMap<String, Course>()
        for (cells in tables) {
            val out = GridParser.parseCascadeOutcome(cells, maxPeriod, defaultWeeks, allowFixedColumns = false)
            if (out.courses.isNotEmpty()) {
                out.courses.forEach { merged.putIfAbsent(courseKey(it), it) }
                if (out.courses.size > bestCount) {
                    bestCount = out.courses.size
                    bestWarnings = out.warnings
                    bestSuggest = out.suggestedPeriods
                }
            } else if (bestCount == 0 && out.warnings.size > bestWarnings.size) {
                // 一张课都没解析出来时，保留证据最多的诊断（「节次超出当前节次表」等）
                bestWarnings = out.warnings
                bestSuggest = out.suggestedPeriods
            }
        }
        return XlsxImporter.Result(
            courses = sortCourses(merged.values.toList()),
            notes = notesFrom(tables),
            warnings = bestWarnings,
            suggestPeriods = bestSuggest
        )
    }

    /**
     * 「无课表课程」备注：表头含「课程名称」但没有星期列的表（如强智课表页下方的无课表课程表），
     * 按课程名称那一列的下方文本收集备注（去重；与 xlsx 的备注表同语义，导入后显示为不可用课）。
     */
    private fun notesFrom(tables: List<Map<Pair<Int, Int>, String>>): List<String> {
        val notes = LinkedHashSet<String>()
        for (cells in tables) {
            val rows = cells.keys.map { it.first }.distinct().sorted()
            val headerRow = rows.firstOrNull { r ->
                cells.filterKeys { it.first == r }.values.any { it.contains("课程名称") }
            } ?: continue
            // 带星期的「课程名称」表是课表本身，不是备注
            if (cells.filterKeys { it.first == headerRow }.values.any { it.contains("星期") }) continue
            val nameCol = cells.entries
                .first { it.key.first == headerRow && it.value.contains("课程名称") }.key.second
            rows.forEach { r ->
                if (r <= headerRow) return@forEach
                cells[r to nameCol]?.trim()?.takeIf { it.isNotEmpty() }?.let { notes.add(it) }
            }
        }
        return notes.toList()
    }

    /** 同一门课在拆分表/打印视图里可能重复出现：按内容去重（含周次与单双周）。 */
    private fun courseKey(c: Course): String = listOf(
        c.name, c.weekday, c.startPeriod, c.endPeriod,
        c.startWeek, c.endWeek, c.weekType.name,
        c.teacher.orEmpty(), c.location.orEmpty()
    ).joinToString("|")
}
