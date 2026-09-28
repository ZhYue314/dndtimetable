package com.dndtimetable.importx

import com.dndtimetable.data.db.Course
import java.text.Normalizer

/**
 * 文字课表解析：每行一门课，分隔 7 列（与「课表模板.xlsx」列序一致）：
 *   课程名称|星期|开始节数|结束节数|老师|地点|周数
 * delimiter 传 null（默认）时自动识别「| / Tab / 逗号 / 分号」，显式传入则按传入字符切（CSV 传 ','）。
 * 容错：全角转半角（NFKC）、忽略代码围栏/表头/空行、起止节次写反自动交换、字段外带引号、星期写成「周一」、
 * 节次写成「第3-4节」；周数含多段（如 1-5、7-11单、12-16双）时拆成多门课。
 * 7 列对不上时再尝试一次「语义扫描」（名称 + 星期 + 节次 + 带「周」的周次 + 老师/地点），仍失败才进 badLines。
 *
 * 分享头（App「生成分享文字」导出，可选）：`#名称:` / `#开学:`(ISO 日期) / `#周数:` 行解析进 [ShareMeta]，
 * 不计入 badLines；无头文字（AI 生成）照常解析，meta=null。
 */
object TextImporter {
    /** 分享头元数据（来自 `#` 行；各字段均可缺省）。 */
    data class ShareMeta(val name: String? = null, val startEpochDay: Long? = null, val totalWeeks: Int? = null)

    data class Result(val courses: List<Course>, val badLines: List<String>, val warnings: List<String> = emptyList(), val meta: ShareMeta? = null)

    /** 带「周」标记的周次片段（容错模式靠这个标记认出哪一列是周次）。 */
    private val weekMarkRe = WeekParser.tokenRe
    private val candidates = charArrayOf('|', '\t', ',', '，', ';', '；')
    private const val FENCE = "```"
    private const val HEADER_PREFIX = "课程名称"
    private const val META_PREFIX = "#"

    fun parse(text: String, maxPeriod: Int = 12, delimiter: Char? = null): Result {
        val courses = mutableListOf<Course>()
        val bad = mutableListOf<String>()
        var meta: ShareMeta? = null
        val d = delimiter ?: detectDelimiter(text)
        text.lineSequence().forEach { rawLine ->
            val line = Normalizer.normalize(rawLine, Normalizer.Form.NFKC).trim()
            if (line.isEmpty() || line.startsWith(FENCE)) return@forEach
            if (line.startsWith(META_PREFIX)) {
                meta = parseMeta(line, meta)
                return@forEach
            }
            val f = splitFields(line, d).map { it.trim().trim('"', '\'', '“', '”') }
            // 表头行按「首列是不是课程名称」判断：带引号的 CSV 表头（"课程名称",...）也能跳过
            if (f.firstOrNull()?.startsWith(HEADER_PREFIX) == true) return@forEach
            val parsed = parseStrict(f, maxPeriod) ?: parseLenient(f, maxPeriod)
            if (parsed == null) bad.add(rawLine.trim()) else courses.addAll(parsed)
        }
        val expanded = expandBigPeriods(courses, maxPeriod)
        val warnings =
            if (expanded !== courses) listOf("课程全是单节号，已按「大节」换算（第 N 节 → 2N-1..2N 节）")
            else emptyList()
        return Result(sortCourses(mergeAdjacentPeriods(expanded)), bad, warnings, meta)
    }

    /** `#key: value` 行（NFKC 已把全角：转半角）；未知 key / 无效值忽略。 */
    private fun parseMeta(line: String, cur: ShareMeta?): ShareMeta? {
        val body = line.substring(META_PREFIX.length)
        val key = body.substringBefore(':').trim()
        val value = body.substringAfter(':', "").trim()
        if (value.isEmpty() || key == body.trim()) return cur   // 无冒号（如标题行 #dndtimetable 课表）跳过
        val base = cur ?: ShareMeta()
        return when (key) {
            "名称" -> base.copy(name = value)
            "开学" -> runCatching { java.time.LocalDate.parse(value) }.getOrNull()
                ?.let { base.copy(startEpochDay = it.toEpochDay()) } ?: cur
            "周数" -> value.toIntOrNull()?.takeIf { it in 1..60 }?.let { base.copy(totalWeeks = it) } ?: cur
            else -> cur
        }
    }

    /** 按列数打分挑分隔符：恰好 7 列权重更高，多于 7 列（如多了备注列）也算命中；全都不行时退回「|」。 */
    private fun detectDelimiter(text: String): Char {
        var best = '|'
        var bestScore = 0
        for (d in candidates) {
            var score = 0
            text.lineSequence().forEach { raw ->
                val line = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()
                if (line.isEmpty() || line.startsWith(FENCE) || line.startsWith(HEADER_PREFIX) || line.startsWith(META_PREFIX)) return@forEach
                val n = splitFields(line, d).size
                if (n == 7) score += 2 else if (n > 7) score += 1
            }
            if (score > bestScore) {
                bestScore = score
                best = d
            }
        }
        return best
    }

    /**
     * 单字符分隔符切分，识别「字段以引号开头」的 CSV 写法（"张三,李四" 不会被逗号切开）。
     * 只有出现在字段开头（前面全是空白）的引号才当引号，避免把课程名里的引号当成引号起始吞掉整行。
     */
    private fun splitFields(line: String, d: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"' && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"'); i += 2; continue
                }
                if (ch == '"') {
                    inQuotes = false; i++; continue
                }
                sb.append(ch); i++
            } else {
                when {
                    ch == '"' && sb.isBlank() -> { inQuotes = true; i++ }
                    ch == d -> { out.add(sb.toString()); sb.setLength(0); i++ }
                    else -> { sb.append(ch); i++ }
                }
            }
        }
        out.add(sb.toString())
        return out
    }

    /** 标准 7 列：失败返回 null（交给 parseLenient 再试一次）。 */
    private fun parseStrict(f: List<String>, maxPeriod: Int): List<Course>? {
        if (f.size < 7) return null
        val name = f[0]
        if (name.isEmpty()) return null
        val wd = GridParser.dayNumber(f[1]) ?: return null
        var sp = f[2].toIntOrNull() ?: -1
        var ep = f[3].toIntOrNull() ?: -1
        if (sp < 1 || ep < 1) {
            val p = GridParser.periodsFromLabel(f[2]) ?: return null
            sp = p.first; ep = p.second
        }
        val from = minOf(sp, ep)
        val to = maxOf(sp, ep)
        if (from < 1 || to > maxPeriod) return null
        val segs = WeekParser.parse(f[6])
        if (segs.isEmpty()) return null
        val teacher = f[4].takeIf { it.isNotEmpty() }
        val location = f[5].takeIf { it.isNotEmpty() }
        return segs.map { seg ->
            Course(
                name = name, weekday = wd, startPeriod = from, endPeriod = to,
                startWeek = seg.start, endWeek = seg.end, weekType = seg.type,
                teacher = teacher, location = location
            )
        }
    }

    /**
     * 容错模式：列序/列数不标准时，按语义在各字段里找「带周的周次」「星期」「节次」，剩下的当老师与地点。
     * 三个关键字段（周次/星期/节次）缺一就放弃，避免把说明性文字导成课程。
     */
    private fun parseLenient(f: List<String>, maxPeriod: Int): List<Course>? {
        val name = f.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        var weekIdx = -1
        var weekToken = ""
        for (i in 1 until f.size) {
            val m = weekMarkRe.find(f[i])
            if (m != null) {
                weekIdx = i; weekToken = m.value; break
            }
        }
        if (weekIdx < 0) return null
        val segs = WeekParser.parse(weekToken)
        if (segs.isEmpty()) return null
        var dayIdx = -1
        var day = 0
        for (i in 1 until f.size) {
            if (i == weekIdx) continue
            val d = GridParser.dayNumber(f[i])
            if (d != null) {
                dayIdx = i; day = d; break
            }
        }
        if (dayIdx < 0) return null
        var pIdx = -1
        var from = -1
        var to = -1
        for (i in 1 until f.size) {
            if (i == weekIdx || i == dayIdx) continue
            val p = GridParser.periodsFromLabel(f[i]) ?: continue
            pIdx = i; from = p.first; to = p.second; break
        }
        if (pIdx < 0) return null
        if (from < 1 || to > maxPeriod) return null
        val others = f.indices
            .filter { it >= 1 && it != weekIdx && it != dayIdx && it != pIdx }
            .map { f[it] }.filter { it.isNotEmpty() }
        val location = others.firstOrNull { GridParser.looksLikeRoom(it) }
        val teacher = others.firstOrNull { it != location && it.none { ch -> ch.isDigit() } }
        return segs.map { seg ->
            Course(
                name = name, weekday = day, startPeriod = from, endPeriod = to,
                startWeek = seg.start, endWeek = seg.end, weekType = seg.type,
                teacher = teacher, location = location
            )
        }
    }
}
