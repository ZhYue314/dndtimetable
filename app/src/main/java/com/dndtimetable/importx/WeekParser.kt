package com.dndtimetable.importx

import com.dndtimetable.data.db.WeekType
import java.text.Normalizer

/**
 * 周数字段解析（xlsx 与文字导入共用）。支持：
 *   1-16 / 1-16单 / 1-16双 / 1-5、7-11单、12-16双 / 2、5、8 / 第1-16周 / 1~16
 * 返回每段 (start, end, type)；多段在导入时拆成多门课（数据模型只支持连续区间）。
 * 单周列表会压缩：1、3、5…15 → 1-15单；2、4、6 → 2-6双；1、2、3 → 1-3。
 * 空字符串或无法识别时返回空列表，由调用方决定兜底或判为坏行。
 */
object WeekParser {
    data class Seg(val start: Int, val end: Int, val type: WeekType)

    private val rangeRe = Regex("(\\d+)\\s*[-~—至]\\s*(\\d+)")
    private val singleRe = Regex("\\d+")
    private val separators = charArrayOf('、', ',', '，', ';', '；', '/')
    // [07-08节]、[周]、【双周】等标注先剔除，避免其中的数字被误当成周次
    private val bracketRe = Regex("[\\[【][^\\]】]*[\\]】]")
    private val ellipsisRe = Regex("(\\d+)\\s*(?:…|\\.{2,})\\s*(\\d+)")

    /**
     * 周次片段的完整写法（网格/文字导入截取整段用）：
     *   1-16周 / 2-5周,7-19周 / 4-18(双)周 / 2-16周(双) / 1-3周(单),4周,9周
     * 每段都要求带「周」，避免把节次 3-4 当周次；单双周标注可写在「周」前或后。
     */
    internal val tokenRe = Regex(
        "([0-9][0-9,，、\\-~—至]*" +
            "(?:\\s*[（(]\\s*[单双]\\s*[）)])?\\s*周次?" +
            "(?:\\s*[（(]\\s*[单双]\\s*[）)])?" +
            "(?:\\s*[、,，;；]\\s*[0-9][0-9,，、\\-~—至]*(?:\\s*[（(]\\s*[单双]\\s*[）)])?\\s*周次?(?:\\s*[（(]\\s*[单双]\\s*[）)])?)*)"
    )

    /** 省略号补齐：「1、3、5…15」→「1、3、5、7、9、11、13、15」（步长取前一个数到起点的差，取不到按 1）。 */
    private fun expandEllipsis(text: String): String =
        ellipsisRe.replace(text) { m ->
            val a = m.groupValues[1].toIntOrNull()
            val b = m.groupValues[2].toIntOrNull()
            if (a == null || b == null || a <= 0 || a >= b || b > 60) return@replace m.value
            val prev = Regex("(\\d+)\\s*[、,，;；/]?\\s*$").find(text.substring(0, m.range.first))
                ?.groupValues?.get(1)?.toIntOrNull()
            val step = if (prev != null && a - prev in 1..2) a - prev else 1
            val seq = (a..b step step).toList().let { if (it.last() == b) it else it + b }
            seq.joinToString("、")
        }

    fun parse(raw: String): List<Seg> {
        val out = mutableListOf<Seg>()
        val text = expandEllipsis(Normalizer.normalize(raw, Normalizer.Form.NFKC))   // 全角数字/连字符也能读
        // 括号内容替换成等长空格：既防止 [07-08节] 里的数字被当成周次，又保持下标与原文一致，
        // 从而能把「([双周])」这种写在括号里的单双周标注算回本段（否则 1-16([双周]) 会被当成全周）
        val cleaned = bracketRe.replace(text) { " ".repeat(it.value.length) }
        var i = 0
        while (i <= cleaned.length) {
            var j = i
            while (j < cleaned.length && cleaned[j] !in separators) j++
            val part = cleaned.substring(i, j).trim()
            if (part.isNotEmpty()) {
                val type = typeOf(text.substring(i, j))
                val m = rangeRe.find(part)
                if (m != null) {
                    val a = m.groupValues[1].toIntOrNull()
                    val b = m.groupValues[2].toIntOrNull()
                    if (a != null && b != null && a > 0 && b > 0) out.add(Seg(minOf(a, b), maxOf(a, b), type))
                } else {
                    val n = singleRe.find(part)?.value?.toIntOrNull()
                    if (n != null && n > 0) out.add(Seg(n, n, type))
                }
            }
            if (j >= cleaned.length) break
            i = j + 1
        }
        return compactSingles(out)
    }

    /** 纯单周列表且等步长（1 或 2）时压缩为一段：1、3、5 → 1-5单；1、2、3 → 1-3。 */
    private fun compactSingles(segs: List<Seg>): List<Seg> {
        if (segs.size < 2 || segs.any { it.start != it.end || it.type != WeekType.ALL }) return segs
        val weeks = segs.map { it.start }.sorted()
        if (weeks.distinct().size != weeks.size) return segs
        val step = weeks[1] - weeks[0]
        if (step != 1 && step != 2) return segs
        if (weeks.zipWithNext().any { (a, b) -> b - a != step }) return segs
        val type = when {
            step == 1 -> WeekType.ALL
            weeks[0] % 2 == 1 -> WeekType.ODD
            else -> WeekType.EVEN
        }
        return listOf(Seg(weeks.first(), weeks.last(), type))
    }

    private fun typeOf(s: String): WeekType = when {
        s.contains("单") -> WeekType.ODD
        s.contains("双") -> WeekType.EVEN
        else -> WeekType.ALL
    }
}
