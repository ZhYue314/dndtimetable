package com.dndtimetable.importx

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.WeekType
import com.dndtimetable.data.prefs.PeriodTable
import java.text.Normalizer

/**
 * 课表「网格/表格」通用解析（.xls 单元格、HTML 表格、xlsx 矩阵共用）：
 * 1) parseTable：按表头关键词识别列（课程/星期/节次/周次/老师/地点），适配列序与列名不同的表格；
 * 2) parseGrid：「星期」表头 + 行内课程单元格（大节×星期网格），单元格支持多行
 *    （课程名/老师/周次([周])[xx-xx节]/地点，空行分隔多门课）与保守的单行格式；
 *    行首「大节/第 x-y 节」标签可直接推出节次，供单元格未写节次的文件使用；
 * 3) parseFixedColumns：模板固定列兜底（A=名称 B=星期 C=开始 D=结束 E=老师 F=地点 G=周数）。
 * 解析结果统一做周数压缩与大节换算，并把「哪些行/格被丢弃及原因」记到 [Report]。
 */
internal object GridParser {

    /** 星期表头/星期列的扫描行数：标题行较多的教务系统导出也能找到表头。 */
    private const val HEADER_SCAN_ROWS = 15

    /**
     * 导入诊断：以前解析失败是静默丢课，用户只看到「导入成功：N 门课」却不知道少了什么。
     * 这里只统计原因与数量，由调用方拼进导入结果提示。
     */
    class Report {
        /** 单元格既没有节次标记、所在行也没有「大节/节次」标签。 */
        var noPeriod = 0
        /** 单元格/行里找不到周次。 */
        var noWeek = 0
        /** 课程名有值、星期列也非空但无法识别（空格子多为表尾/说明行，不计）。 */
        var noWeekday = 0
        /** 节次超出当前节次表（会按 maxPeriod 丢弃）。 */
        var outOfRange = 0
        /** 超出范围时见过的最大节次，用来提示用户「该加到多少节」。 */
        var maxPeriodSeen = 0
        /** 文件没写周次，按整学期兜底导入。 */
        var weeksDefaulted = 0
        /** 全表都是单节号且不超过总节数一半，已按「大节编号」整表换算为 2N-1..2N。 */
        var bigPeriodsExpanded = false

        fun clear() {
            noPeriod = 0; noWeek = 0; noWeekday = 0
            outOfRange = 0; maxPeriodSeen = 0; weeksDefaulted = 0
            bigPeriodsExpanded = false
        }

        /** 节次表上调到多少节才能容纳全部课程（节次按大节成对增加，故往上取偶数）；超出 App 上限时 null。 */
        fun suggestedPeriodCount(): Int? {
            if (outOfRange == 0 || maxPeriodSeen <= 0) return null
            val need = if (maxPeriodSeen % 2 == 0) maxPeriodSeen else maxPeriodSeen + 1
            return need.takeIf { it <= PeriodTable.MAX_PERIODS }
        }

        fun warnings(): List<String> = buildList {
            if (outOfRange > 0) {
                val need = suggestedPeriodCount()
                add(
                    if (need != null)
                        "$outOfRange 门课节次超出当前节次表（最高第 $maxPeriodSeen 节），未导入；把节数调到 $need 节后重新导入"
                    else
                        "$outOfRange 门课节次超出 ${PeriodTable.MAX_PERIODS} 节上限（最高第 $maxPeriodSeen 节），未导入"
                )
            }
            if (noWeek > 0) add("$noWeek 门课没读到周次未导入")
            if (noWeekday > 0) add("$noWeekday 门课没读到星期未导入")
            if (noPeriod > 0) add("$noPeriod 门课没读到节次未导入")
            if (weeksDefaulted > 0) add("$weeksDefaulted 门课未写明周次，已按整学期导入")
            if (bigPeriodsExpanded) add("课程全是单节号，已按「大节」换算（第 N 节 → 2N-1..2N 节）")
        }
    }

    // ---------------- 正则 ----------------

    /** 星期表头/星期列：允许「周一(Mon)」「星期一 Mon」这类尾随注释，注释里不能出现星期字。 */
    private val dayHeaderRe = Regex(
        "^(?:星期|周|礼拜|weekday)?\\s*([一二三四五六日天1-7])\\s*[（(\\[【]?\\s*[A-Za-z]{0,12}\\s*[）)\\]】]?$",
        RegexOption.IGNORE_CASE
    )

    private val periodRe = Regex(
        "(?:\\[|【|（|\\()\\s*(?:第)?(\\d{1,2})\\s*[-~—至]\\s*(\\d{1,2})\\s*节?\\s*(?:\\]|】|）|\\))" +
            "|第\\s*(\\d{1,2})\\s*节\\s*[-~—至]\\s*第?\\s*(\\d{1,2})\\s*节" +
            "|(?:第)?(\\d{1,2})\\s*[-~—至]\\s*(\\d{1,2})\\s*节" +
            "|第\\s*(\\d{1,2})\\s*节"
    )
    private val weekTokenRe = WeekParser.tokenRe
    private val weekParenRe = Regex("([0-9][0-9,，、\\-~—至]*)\\s*[（(]\\s*[\\[【]?\\s*(?:周|单周|双周)")
    private val parenRe = Regex("[（(]([^）)]{1,20})[）)]")
    private val roomRe = Regex("[A-Za-z]{0,3}\\d{2,}[A-Za-z0-9]*|[\\u4e00-\\u9fa5A-Za-z0-9]*(?:楼|室|馆|区|栋|教)[\\u4e00-\\u9fa5A-Za-z0-9]*")

    /** 行标签里的显式节次区间：「第1-2节」「1~2节」「3-4」。 */
    private val labelRangeRe = Regex("(?:第)?(\\d{1,2})\\s*[-~—至]\\s*(\\d{1,2})\\s*节?")
    /** 行标签里的单节：「第5节」。 */
    private val labelSingleRe = Regex("第\\s*(\\d{1,2})\\s*节")
    /** 分隔行：强智（及类似系统）一格多课时用「--------------------」分隔两门课的明细。 */
    private val separatorLineRe = Regex("^[-—–_=*·.。]{3,}$")
    /** 只含一个括号片段的整行（NFKC 后是半角括号）：明细里的班级名行，并入课名。 */
    private val parenOnlyRe = Regex("^\\([^)]{1,20}\\)$")
    /** 「学期理论课表（第 N 周）」视图的汇总行特征：含「学分：/教师：」的碎句，不是可导入的课。 */
    private val viewSummaryRe = Regex("学分|教师[:：]")
    /** 行标签里的大节编号：「第一大节」「大一大节」（教务系统常见笔误）「大节3」。 */
    private val bigLabelRe = Regex("大\\s*[0-9一二三四五六七八九十]|大节")
    /** 行标签里的上课时间：「9:50-11:25」；匹配节次前先剔除，否则分钟数会被当成节次。 */
    private val clockRe = Regex("\\d{1,2}\\s*[:：]\\s*\\d{2}\\s*[-~—至]\\s*\\d{1,2}\\s*[:：]\\s*\\d{2}")
    /** 单个时间点：「8:30」；与整格只剩数字的行标签（节次列只写「3」+ 上课时间）一起剔除。 */
    private val timePointRe = Regex("\\d{1,2}\\s*[:：]\\s*\\d{2}")
    private val cnDigits = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10
    )

    // ---------------- 表头驱动（任意列序） ----------------

    fun parseTable(
        cells: Map<Pair<Int, Int>, String>,
        maxPeriod: Int,
        report: Report = Report(),
        defaultWeeks: Int = 0
    ): List<Course> {
        val c = normalize(cells)
        val rows = c.keys.map { it.first }.distinct().sorted()
        val headerRow = rows.firstOrNull { r ->
            val vals = c.filterKeys { it.first == r }.values
            vals.any { nameKeyword(it) } && vals.any { weekDayKeyword(it) }
        } ?: return emptyList()
        var colName = -1; var colDay = -1; var colStart = -1; var colEnd = -1
        var colPeriods = -1; var colTeacher = -1; var colLoc = -1; var colWeeks = -1
        c.filterKeys { it.first == headerRow }.forEach { (k, v) ->
            val t = v.trim()
            when {
                colName < 0 && nameKeyword(t) -> colName = k.second
                colDay < 0 && weekDayKeyword(t) -> colDay = k.second
                colStart < 0 && (t.contains("开始") || t.contains("起始")) -> colStart = k.second
                colEnd < 0 && (t.contains("结束") || t.contains("终止")) -> colEnd = k.second
                colPeriods < 0 && (t.contains("节次") || t.contains("节数")) -> colPeriods = k.second
                colWeeks < 0 && t.contains("周") -> colWeeks = k.second
                colTeacher < 0 && (t.contains("老师") || t.contains("教师") || t.contains("授课")) -> colTeacher = k.second
                colLoc < 0 && (t.contains("地点") || t.contains("教室") || t.contains("场地")) -> colLoc = k.second
            }
        }
        // 周次列不再必需：整行里带「周」的片段、或整学期兜底都能补上周次
        if (colName < 0 || colDay < 0) return emptyList()
        val out = ArrayList<Course>()
        for (r in rows) {
            if (r <= headerRow) continue
            val name = cell(c, r, colName)
            if (name.isEmpty()) continue
            val dayCell = cell(c, r, colDay)
            val day = dayNumber(dayCell)
            if (day == null) {
                // 空格子多为表尾/说明行；非空却读不出才是真丢课，计入诊断
                if (dayCell.isNotEmpty()) report.noWeekday++
                continue
            }
            var segs = if (colWeeks >= 0) WeekParser.parse(cell(c, r, colWeeks)) else emptyList()
            if (segs.isEmpty()) segs = weeksFromRow(c, r, colWeeks)
            if (segs.isEmpty() && defaultWeeks >= 1) {
                segs = listOf(WeekParser.Seg(1, defaultWeeks, WeekType.ALL))
                report.weeksDefaulted++
            }
            if (segs.isEmpty()) {
                report.noWeek++
                continue
            }
            var from = -1
            var to = -1
            if (colStart >= 0 && colEnd >= 0) {
                from = cell(c, r, colStart).toIntOrNull() ?: -1
                to = cell(c, r, colEnd).toIntOrNull() ?: -1
            }
            if (from < 1 || to < 1) {
                val src = cell(c, r, if (colPeriods >= 0) colPeriods else colStart)
                val p = periodsFromLabel(src)
                if (p == null) {
                    report.noPeriod++
                    continue
                }
                from = p.first; to = p.second
            }
            val f = minOf(from, to)
            val t = maxOf(from, to)
            if (f < 1 || t > maxPeriod) {
                report.outOfRange++
                report.maxPeriodSeen = maxOf(report.maxPeriodSeen, f, t)
                continue
            }
            val teacher = cell(c, r, colTeacher).ifEmpty { null }
            val loc = cell(c, r, colLoc).ifEmpty { null }
            segs.forEach { seg ->
                out.add(
                    Course(
                        name = name, weekday = day, startPeriod = f, endPeriod = t,
                        startWeek = seg.start, endWeek = seg.end, weekType = seg.type,
                        teacher = teacher, location = loc
                    )
                )
            }
        }
        val expanded = expandBigPeriods(out, maxPeriod)
        if (expanded !== out) report.bigPeriodsExpanded = true
        return sortCourses(mergeAdjacentPeriods(expanded))
    }

    private fun nameKeyword(s: String): Boolean {
        val t = s.trim()
        if (t.contains("编号") || t.contains("代码") || t.contains("性质") || t.contains("属性") || t.contains("序号")) return false
        return t.contains("课程名称") || t.contains("课程名") || t.contains("科目") || t == "课程"
    }

    private fun weekDayKeyword(s: String): Boolean {
        val t = s.trim()
        if (t == "周次" || t.contains("周数")) return false
        return t.contains("星期") || t == "周几" || t == "周" || t == "weekday" ||
            Regex("^周[一二三四五六日天1-7]$").matches(t)
    }

    // ---------------- 大节×星期网格 ----------------

    fun parseGrid(
        cells: Map<Pair<Int, Int>, String>,
        maxPeriod: Int,
        report: Report = Report()
    ): List<Course> {
        val c = normalize(cells)
        var headerRow = -1
        var best = 0
        val maxRow = c.keys.maxOfOrNull { it.first } ?: return emptyList()
        for (r in 0..minOf(maxRow, HEADER_SCAN_ROWS)) {
            val cnt = c.filterKeys { it.first == r }.values.count { dayHeaderRe.matches(it.trim()) }
            if (cnt > best) { best = cnt; headerRow = r }
        }
        if (headerRow < 0 || best < 3) return emptyList()
        val dayCols = HashMap<Int, Int>()
        c.forEach { (k, v) ->
            if (k.first == headerRow) {
                val m = dayHeaderRe.find(v.trim())
                if (m != null) dayToken(m.groupValues[1])?.let { d -> dayCols[d] = k.second }
            }
        }
        if (dayCols.size < 3) return emptyList()
        val minDayCol = dayCols.values.min()
        val rows = c.keys.filter { it.first > headerRow }.map { it.first }.distinct().sorted()
        // 行标签（「第一大节」「第 3-4 节」）直接推出节次：单元格里没写节次的文件也能导入
        val rowPeriods = HashMap<Int, Pair<Int, Int>>()
        rows.forEach { r ->
            rowLabelPeriods(c, r, minDayCol)?.let { rowPeriods[r] = it }
        }
        // 有节次行标签时只解析带标签的行，否则会把「小节行」和「大节行」重复计课
        val labeled = rowPeriods.isNotEmpty()
        val out = ArrayList<Course>()
        for (r in rows) {
            val fallback = rowPeriods[r]
            if (labeled && fallback == null) continue
            for ((day, col) in dayCols) {
                val raw = c[r to col] ?: continue
                parseCell(raw, day, maxPeriod, out, report, fallback)
            }
        }
        val expanded = expandBigPeriods(out, maxPeriod)
        if (expanded !== out) report.bigPeriodsExpanded = true
        return sortCourses(mergeAdjacentPeriods(expanded))
    }

    /**
     * 行标签的节次：星期列之前最靠左、能解析出节次的那格。
     * 不只看最左格——有些表前面还有「上午/下午」分组列，真正的「第 3-4 节」在它右边。
     */
    private fun rowLabelPeriods(cells: Map<Pair<Int, Int>, String>, row: Int, minDayCol: Int): Pair<Int, Int>? =
        cells.entries
            .filter { it.key.first == row && it.key.second < minDayCol }
            .sortedBy { it.key.second }
            .firstNotNullOfOrNull { periodsFromLabel(it.value) }

    private fun parseCell(
        raw: String,
        day: Int,
        maxPeriod: Int,
        out: MutableList<Course>,
        report: Report,
        fallback: Pair<Int, Int>?
    ) {
        val text = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        text.split(Regex("\\n\\s*\\n")).forEach { block ->
            val lines = block.split('\n').map { it.trim() }
                .filter { it.isNotEmpty() && !separatorLineRe.matches(it) }
            if (lines.isEmpty()) return@forEach
            // 周次可在任意一行（「1-16周」「1-16([周])」「1、3、5([周])」）
            val weekIdx = lines.indexOfFirst { weekTokenRe.containsMatchIn(it) || weekParenRe.containsMatchIn(it) }
            val periodIdx = lines.indexOfFirst { periodRe.containsMatchIn(it) }
            // 既没有节次行、行标签也推不出节次 → 放弃该格（记诊断）
            if (periodIdx < 0 && fallback == null) {
                report.noPeriod++
                return@forEach
            }
            if (weekIdx < 0) {
                report.noWeek++
                return@forEach
            }
            val weekToken = weekTokenRe.find(lines[weekIdx])?.value ?: weekParenRe.find(lines[weekIdx])?.value
            if (weekToken == null) {
                report.noWeek++
                return@forEach
            }
            val segs = WeekParser.parse(weekToken)
            if (segs.isEmpty()) {
                report.noWeek++
                return@forEach
            }

            var sp = -1
            var ep = -1
            var name = ""
            var teacher: String? = null
            var location: String? = null
            if (periodIdx >= 0) {
                val pm = periodRe.find(lines[periodIdx])
                val a = if (pm != null) periodStart(pm) else null
                val b = if (pm != null) periodEnd(pm) else null
                if (pm == null || a == null || b == null) {
                    report.noPeriod++
                    return@forEach
                }
                sp = a
                ep = b
                if (periodIdx > 0) {
                    // 明细里单独一行的括号片段是班级名（如「(25B6B16)」「(武术男1班)」）：并入课名，别当老师
                    var nameLines = 1
                    while (nameLines < periodIdx && parenOnlyRe.matches(lines[nameLines])) nameLines++
                    name = lines.take(nameLines).joinToString("")
                    teacher = lines.subList(nameLines, periodIdx).joinToString(",").takeIf { it.isNotBlank() }
                    location = lines.drop(periodIdx + 1).joinToString(" ").takeIf { it.isNotBlank() }
                } else {
                    val parsed = parseSingleLine(periodLine = lines[0], pm = pm, weekToken = weekToken)
                    if (parsed == null) {
                        report.noPeriod++
                        return@forEach
                    }
                    name = parsed.first
                    teacher = parsed.second
                    location = parsed.third
                }
            } else {
                // 单元格没写节次：用行标签的节次，名称取首行，其余行按「教室样式 / 姓名样式」分老师与地点
                val fb = fallback ?: return@forEach
                sp = fb.first
                ep = fb.second
                name = lines[0]
                // 强智「汇总行」（形如「数字电路与逻辑设计1-16(周)J7201」，明细在后续空行块里）会带周次，
                // 若当课名会产出乱名课；真正的单行课名不会包含「周次」片段
                if (weekTokenRe.containsMatchIn(name) || weekParenRe.containsMatchIn(name)) return@forEach
                val rest = lines.indices.drop(1)
                    .filter { it != weekIdx && !periodRe.containsMatchIn(lines[it]) }
                    .map { lines[it] }
                location = rest.firstOrNull { looksLikeRoom(it) }
                teacher = rest.firstOrNull { it != location && it.none { ch -> ch.isDigit() } }
            }
            if (name.startsWith("备注")) return@forEach
            val from = minOf(sp, ep)
            val to = maxOf(sp, ep)
            if (from < 1 || to > maxPeriod) {
                report.outOfRange++
                report.maxPeriodSeen = maxOf(report.maxPeriodSeen, from, to)
                return@forEach
            }
            segs.forEach { seg ->
                out.add(
                    Course(
                        name = name, weekday = day, startPeriod = from, endPeriod = to,
                        startWeek = seg.start, endWeek = seg.end, weekType = seg.type,
                        teacher = teacher, location = location
                    )
                )
            }
        }
    }

    /** 单行格式（如「高等数学(张三) 1-16周 3-4节 教一101」）的保守拆解；拿不准返回 null 跳过。 */
    private fun parseSingleLine(periodLine: String, pm: MatchResult, weekToken: String): Triple<String, String?, String?>? {
        // 强智「学期理论课表（第 N 周）」的汇总碎句（数字电路与...教师：杨洁01~02小节…学分：2.5…）：
        // 名字截断、字段粘连，按单行格式拼会产出乱课（课名/老师都是碎片），直接跳过
        if (viewSummaryRe.containsMatchIn(periodLine)) return null
        val cut = listOfNotNull(
            pm.range.first,
            periodLine.indexOf('(').takeIf { it >= 0 },
            weekTokenRe.find(periodLine)?.range?.first,
            weekParenRe.find(periodLine)?.range?.first
        ).min()
        val name = periodLine.substring(0, cut).trim().trim('-', '—', ':', '：', ',', '，')
        if (name.isEmpty() || name.any { it.isDigit() }) return null
        val teacher = parenRe.findAll(periodLine).map { it.groupValues[1].trim() }
            .firstOrNull {
                it.isNotEmpty() && it.none { c -> c.isDigit() } && !it.contains("周") && !it.contains("节") &&
                    it !in listOf("美育", "必修", "选修", "公选", "限选", "任选", "实践", "理论", "通识")
            }
        val rest = periodLine.replace(pm.value, " ").replace(weekToken, " ")
        val location = roomRe.find(rest)?.value
        return Triple(name, teacher, location)
    }

    // ---------------- 模板固定列兜底 ----------------

    /**
     * 固定列兜底：A=课程名称 B=星期 C=开始节数 D=结束节数 E=老师 F=地点 G=周数（列号 0-based）。
     * 兼容「节次写成 3-4节 放在 C 列」的写法；周次可从整行里带「周」的片段补，或用 defaultWeeks 兜底。
     */
    fun parseFixedColumns(
        cells: Map<Pair<Int, Int>, String>,
        maxPeriod: Int,
        report: Report = Report(),
        defaultWeeks: Int = 0
    ): List<Course> {
        val c = normalize(cells)
        val out = ArrayList<Course>()
        for (r in c.keys.map { it.first }.distinct().sorted()) {
            val name = cell(c, r, 0)
            if (name.isEmpty() || name.startsWith("课程名称")) continue
            val dayCell = cell(c, r, 1)
            val day = dayNumber(dayCell)
            if (day == null) {
                if (dayCell.isNotEmpty()) report.noWeekday++
                continue
            }
            var from = cell(c, r, 2).toIntOrNull() ?: -1
            var to = cell(c, r, 3).toIntOrNull() ?: -1
            if (from < 1 || to < 1) {
                val p = periodsFromLabel(cell(c, r, 2)) ?: periodsFromLabel(cell(c, r, 3))
                if (p == null) {
                    report.noPeriod++
                    continue
                }
                from = p.first; to = p.second
            }
            val f = minOf(from, to)
            val t = maxOf(from, to)
            if (f < 1 || t > maxPeriod) {
                report.outOfRange++
                report.maxPeriodSeen = maxOf(report.maxPeriodSeen, f, t)
                continue
            }
            var segs = WeekParser.parse(cell(c, r, 6))
            if (segs.isEmpty()) segs = weeksFromRow(c, r, 6)
            if (segs.isEmpty() && defaultWeeks >= 1) {
                segs = listOf(WeekParser.Seg(1, defaultWeeks, WeekType.ALL))
                report.weeksDefaulted++
            }
            if (segs.isEmpty()) {
                report.noWeek++
                continue
            }
            val teacher = cell(c, r, 4).ifEmpty { null }
            val loc = cell(c, r, 5).ifEmpty { null }
            segs.forEach { seg ->
                out.add(
                    Course(
                        name = name, weekday = day, startPeriod = f, endPeriod = t,
                        startWeek = seg.start, endWeek = seg.end, weekType = seg.type,
                        teacher = teacher, location = loc
                    )
                )
            }
        }
        val expanded = expandBigPeriods(out, maxPeriod)
        if (expanded !== out) report.bigPeriodsExpanded = true
        return sortCourses(mergeAdjacentPeriods(expanded))
    }

    // ---------------- 三段策略 ----------------

    /**
     * 三段策略的结果：课程 + 诊断 + 「节次表该加到多少节」建议（无建议时 null，供 UI 一键调整）。
     */
    class Outcome(val courses: List<Course>, val warnings: List<String>, val suggestedPeriods: Int?)

    /**
     * 依次尝试三种结构：表头驱动 → 星期×大节网格 → 模板固定列。
     * 成功时只回报该策略的诊断；全部失败时回报证据最多的一份——
     * 否则真因（如「节次超出当前节次表」）会被后一次尝试的空诊断洗掉，用户只能看到「未识别到课程」。
     */
    fun parseCascadeOutcome(
        cells: Map<Pair<Int, Int>, String>,
        maxPeriod: Int,
        defaultWeeks: Int = 0,
        allowFixedColumns: Boolean = true
    ): Outcome {
        var best: Report? = null
        fun keep(r: Report) {
            if (r.warnings().size > (best?.warnings()?.size ?: 0)) best = r
        }
        val r1 = Report()
        val c1 = parseTable(cells, maxPeriod, r1, defaultWeeks)
        if (c1.isNotEmpty()) return Outcome(c1, r1.warnings(), r1.suggestedPeriodCount())
        keep(r1)
        val r2 = Report()
        val c2 = parseGrid(cells, maxPeriod, r2)
        if (c2.isNotEmpty()) return Outcome(c2, r2.warnings(), r2.suggestedPeriodCount())
        keep(r2)
        if (allowFixedColumns) {
            val r3 = Report()
            val c3 = parseFixedColumns(cells, maxPeriod, r3, defaultWeeks)
            if (c3.isNotEmpty()) return Outcome(c3, r3.warnings(), r3.suggestedPeriodCount())
            keep(r3)
        }
        return Outcome(emptyList(), best?.warnings() ?: emptyList(), best?.suggestedPeriodCount())
    }

    fun parseCascade(
        cells: Map<Pair<Int, Int>, String>,
        maxPeriod: Int,
        defaultWeeks: Int = 0,
        allowFixedColumns: Boolean = true
    ): Pair<List<Course>, List<String>> =
        parseCascadeOutcome(cells, maxPeriod, defaultWeeks, allowFixedColumns).let { it.courses to it.warnings }

    // ---------------- 小工具 ----------------

    /** 全角转半角：让「１」「｜」这类大模型/网页复制来的字符也能读（幂等）。 */
    private fun normalize(cells: Map<Pair<Int, Int>, String>): Map<Pair<Int, Int>, String> =
        cells.mapValues { (_, v) -> Normalizer.normalize(v, Normalizer.Form.NFKC) }

    /** 整行里第一个带「周」的周次片段（周次列缺失/为空时兜底）。 */
    private fun weeksFromRow(cells: Map<Pair<Int, Int>, String>, row: Int, skipCol: Int): List<WeekParser.Seg> {
        val merged = cells.entries
            .filter { it.key.first == row && it.key.second != skipCol }
            .joinToString(" ") { it.value }
        val token = weekTokenRe.find(merged)?.value ?: weekParenRe.find(merged)?.value ?: return emptyList()
        return WeekParser.parse(token)
    }

    /**
     * 星期文本 → 1..7。容忍「周一」「星期一」「1」「一」「星期一(Mon)」；
     * 纯数字非 1..7（如「8」）一律判为无效，不猜。
     */
    fun dayNumber(s: String): Int? {
        val t = Normalizer.normalize(s, Normalizer.Form.NFKC).trim()
        if (t.isEmpty()) return null
        val stripped = t.removePrefix("星期").removePrefix("周").removePrefix("礼拜").trim()
        dayToken(stripped)?.let { return it }
        if (stripped.matches(Regex("\\d+"))) return null
        val m = dayHeaderRe.find(t) ?: return null
        return dayToken(m.groupValues[1])
    }

    private fun dayToken(s: String): Int? =
        when (val t = s.trim()) {
            "一" -> 1; "二" -> 2; "三" -> 3; "四" -> 4; "五" -> 5; "六" -> 6; "日", "天" -> 7
            else -> t.toIntOrNull()?.takeIf { it in 1..7 }
        }

    /**
     * 行标签/节次文本 → 节次区间：
     * 「第1-2节」「1~2节」「3-4」→ 原样；「第5节」「3」→ 5..5 / 3..3；「第三大节」「大一大节」→ 2N-1..2N。
     * 先剔除周次与上课时间片段，避免把「1-16周」「9:50-11:25」当成节次。
     */
    fun periodsFromLabel(label: String): Pair<Int, Int>? {
        val raw = Normalizer.normalize(label, Normalizer.Form.NFKC).trim()
        if (raw.isEmpty()) return null
        val t = timePointRe.replace(
            clockRe.replace(weekTokenRe.replace(weekParenRe.replace(raw, " "), " "), " "),
            " "
        )
        labelRangeRe.find(t)?.let { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a != null && b != null && a > 0 && b > 0) return minOf(a, b) to maxOf(a, b)
        }
        labelSingleRe.find(t)?.let { m ->
            m.groupValues[1].toIntOrNull()?.takeIf { it > 0 }?.let { return it to it }
        }
        if (bigLabelRe.containsMatchIn(t)) {
            val stripped = t.replace("节", "").replace("大", "").replace("第", "").trim()
            numeral(stripped)?.takeIf { it in 1..12 }?.let { return (it * 2 - 1) to (it * 2) }
        }
        // 剔掉时间后整格只剩一个数字（节次列只写「3」或「3 + 上课时间」）：当作单节
        t.trim().toIntOrNull()?.takeIf { it in 1..PeriodTable.MAX_PERIODS }?.let { return it to it }
        return null
    }

    private fun numeral(s: String): Int? =
        s.toIntOrNull() ?: s.firstOrNull()?.let { cnDigits[it] }

    /** 看起来像教室/地点（含「楼室馆区栋教」或「字母+数字」），用于无节次锚点时区分老师与地点。 */
    fun looksLikeRoom(s: String): Boolean = roomRe.find(s) != null

    private fun periodStart(m: MatchResult): Int? = when {
        m.groupValues[1].isNotEmpty() -> m.groupValues[1].toIntOrNull()
        m.groupValues[3].isNotEmpty() -> m.groupValues[3].toIntOrNull()
        m.groupValues[5].isNotEmpty() -> m.groupValues[5].toIntOrNull()
        m.groupValues[7].isNotEmpty() -> m.groupValues[7].toIntOrNull()
        else -> null
    }

    private fun periodEnd(m: MatchResult): Int? = when {
        m.groupValues[2].isNotEmpty() -> m.groupValues[2].toIntOrNull()
        m.groupValues[4].isNotEmpty() -> m.groupValues[4].toIntOrNull()
        m.groupValues[6].isNotEmpty() -> m.groupValues[6].toIntOrNull()
        m.groupValues[7].isNotEmpty() -> m.groupValues[7].toIntOrNull()
        else -> null
    }

    private fun cell(cells: Map<Pair<Int, Int>, String>, row: Int, col: Int): String =
        if (col >= 0) cells[row to col]?.trim().orEmpty() else ""
}
