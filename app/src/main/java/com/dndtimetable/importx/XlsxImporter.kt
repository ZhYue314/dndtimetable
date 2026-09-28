package com.dndtimetable.importx

import android.content.Context
import android.net.Uri
import com.dndtimetable.data.db.Course
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * 轻量 .xlsx 读取器：直接解析 ZIP + XML（sharedStrings / workbook / sheet），
 * 不引入 POI（体积小）。按「课表模板.xlsx」列解析：
 *   课程名称/星期/开始节数/结束节数/老师/地点/周数
 * 兼容大模型生成的文件：表头前 10 行内扫描、inlineStr 单元格、富文本 sharedStrings；
 * 周数多段（1-5、7-11单、12-16双）拆成多门课；整表按大节编号（1-5）时自动展开为小节。
 * 第二个 sheet「无课表课程」作为备注导入。
 * 入口按文件头嗅探：OLE2（.xls）→ XlsImporter；ZIP（.xlsx）→ 本解析器；其余 → CSV 文本（UTF-8/GBK）。
 *
 * XML 用 `javax.xml`（DOM）而不是 `android.util.Xml`：Android 与 JVM 都有，解析逻辑可 JVM 单测
 * （见 `XlsxImporterTest`，内存里现造 xlsx 直接跑 parse，不再依赖真机导入验证）。
 */
object XlsxImporter {

    data class Result(
        val courses: List<Course>,
        val notes: List<String>,
        val warnings: List<String> = emptyList(),
        /** 节次超出当前节次表时建议调到的节数（无建议 null），供 UI 一键调整后重新解析。 */
        val suggestPeriods: Int? = null
    )

    suspend fun import(context: Context, uri: Uri, maxPeriod: Int, defaultWeeks: Int = 0): Result = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw Exception("无法读取文件")
        when {
            XlsImporter.isXls(bytes) -> {   // 教务系统 .xls（BIFF8）
                val r = XlsImporter.parse(bytes, maxPeriod, defaultWeeks)
                Result(r.courses, r.notes, r.warnings, r.suggestPeriods)
            }
            isZip(bytes) -> parse(bytes.inputStream(), maxPeriod, defaultWeeks)   // .xlsx（ZIP+XML）
            else -> {
                val text = decodeText(bytes)
                if (HtmlImporter.isHtml(text)) {
                    // 教务系统「导出 Excel」实为 HTML 表格（.xls/.html）
                    val r = HtmlImporter.parse(text, maxPeriod, defaultWeeks)
                    Result(r.courses, r.notes, r.warnings, r.suggestPeriods)
                } else {
                    // 其余按文本 CSV 解析（教务系统「标准课表」导出，UTF-8 或 GBK；分隔符自动识别）
                    val res = TextImporter.parse(text, maxPeriod)
                    if (res.courses.isEmpty()) throw Exception("未识别到课程：请用 .xls/.xlsx 或课表 .csv 文件")
                    Result(res.courses, emptyList(), res.warnings)
                }
            }
        }
    }

    private fun isZip(b: ByteArray): Boolean =
        b.size >= 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() &&
            b[2] == 0x03.toByte() && b[3] == 0x04.toByte()

    /** UTF-8（含 BOM）严格解码，失败回退 GBK（教务系统常见编码）。 */
    private fun decodeText(b: ByteArray): String {
        val off = if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) 3 else 0
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b, off, b.size - off)).toString()
        } catch (_: Exception) {
            String(b, off, b.size - off, Charset.forName("GBK"))
        }
    }

    fun parse(input: InputStream, maxPeriod: Int, defaultWeeks: Int = 0): Result {
        val zip = ZipInputStream(input.buffered())
        val shared = mutableListOf<String>()
        val sheetXmls = mutableMapOf<String, String>()
        var e = zip.nextEntry
        while (e != null) {
            val name = e.name
            val content = zip.readBytes().toString(Charsets.UTF_8)
            when {
                name.endsWith("sharedStrings.xml") -> shared.addAll(parseSharedStrings(content))
                name.startsWith("xl/worksheets/") && name.endsWith(".xml") ->
                    sheetXmls[name.substringAfterLast('/')] = content
            }
            zip.closeEntry()
            e = zip.nextEntry
        }

        val courses = mutableListOf<Course>()
        val notes = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var fallback: List<String> = emptyList()   // 所有 sheet 都没解析出课程时的诊断
        var suggest: Int? = null
        var fallbackSuggest: Int? = null
        // 主课表：按「星期」列识别；无课表：按「课程名称」列识别（但无星期）
        sheetXmls.values.forEach { xml ->
            val rows = parseSheetRows(xml, shared)
            // 表头不一定在第 1 行（大模型生成的文件可能带标题行）：前 10 行内扫描
            val headerIdx = rows.indexOfFirst { r ->
                r.values.any { it.contains("星期") } || r.values.any { it.contains("课程名称") }
            }
            if (headerIdx < 0) return@forEach
            val header = rows[headerIdx]
            if (header.values.any { it.contains("星期") }) {
                // 表头列名映射 → 星期×大节网格 → 模板固定列；固定列只在表头确实像模板时启用，否则会把矩阵读成垃圾课
                val cellMap = HashMap<Pair<Int, Int>, String>()
                rows.forEachIndexed { r, m -> m.forEach { (c, v) -> cellMap[r to (c - 1)] = v } }
                val out = GridParser.parseCascadeOutcome(
                    cellMap, maxPeriod, defaultWeeks, looksLikeTemplateHeader(header)
                )
                if (out.courses.isNotEmpty()) {
                    courses.addAll(out.courses)
                    warnings.addAll(out.warnings)
                    out.suggestedPeriods?.let { suggest = maxOf(suggest ?: 0, it) }
                } else if (out.warnings.size > fallback.size) {
                    fallback = out.warnings
                    fallbackSuggest = out.suggestedPeriods
                }
            } else if (header.values.any { it.contains("课程名称") }) {
                // 无课表课程：D=课程名称
                for (r in rows.drop(headerIdx + 1)) {
                    val name = r[4]?.trim().orEmpty()
                    if (name.isNotEmpty()) notes.add(name)
                }
            }
        }
        return Result(
            sortCourses(mergeAdjacentPeriods(expandBigPeriods(courses, maxPeriod))), notes,
            if (courses.isEmpty()) fallback else warnings,
            if (courses.isEmpty()) fallbackSuggest else suggest
        )
    }

    /** 表头是否像「课表模板」（有节次/起止列 + 周次列）——只有像模板时才能按 A–G 列位兜底读取。 */
    private fun looksLikeTemplateHeader(header: Map<Int, String>): Boolean {
        val vals = header.values.map { it.trim() }
        val hasPeriods = vals.any { it.contains("节次") || it.contains("节数") || it.contains("开始") || it.contains("结束") }
        val hasWeeks = vals.any { it.contains("周") }
        return hasPeriods && hasWeeks
    }

    private fun parseSharedStrings(xml: String): List<String> =
        Regex("<si>.*?</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { si ->
                // 富文本会拆成多个 <r><t>…</t></r>：全部拼接，避免只取第一段丢字
                Regex("<t[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL).findAll(si.value)
                    .joinToString("") { it.groupValues[1] }
            }
            .map { decodeEntities(it) }
            .toList()

    /** sheet XML → 行×列单元格（DOM；与旧 XmlPullParser 行为一致：shared string 下标、inlineStr、富文本合并）。 */
    private fun parseSheetRows(xml: String, shared: List<String>): List<Map<Int, String>> {
        val rows = ArrayList<Map<Int, String>>()
        val doc = try {
            val f = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { isExpandEntityReferences = false }
                // 解析的是用户文件：能关外部实体就关（部分实现不支持该 feature，忽略即可）
                runCatching { setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            f.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        } catch (_: Exception) {
            return rows
        }
        val rowNodes = doc.getElementsByTagName("row")
        for (i in 0 until rowNodes.length) {
            val row = rowNodes.item(i) as? Element ?: continue
            val cells = HashMap<Int, String>()
            val cellNodes = row.getElementsByTagName("c")
            for (j in 0 until cellNodes.length) {
                val c = cellNodes.item(j) as? Element ?: continue
                val col = colIndex(c.getAttribute("r").takeWhile { it.isLetter() })
                if (col <= 0) continue
                val v = c.getElementsByTagName("v").item(0)?.textContent
                val raw = v ?: run {
                    // inlineStr：<is> 里可能有多个 <r><t> 片段（富文本），全部拼接
                    val ts = c.getElementsByTagName("t")
                    if (ts.length == 0) null else (0 until ts.length).joinToString("") { ts.item(it).textContent }
                } ?: continue
                val text = if (c.getAttribute("t") == "s") {
                    shared.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: ""
                } else raw
                cells[col] = decodeEntities(text)
            }
            if (cells.isNotEmpty()) rows.add(cells)
        }
        return rows
    }

    private fun colIndex(letters: String): Int {
        var n = 0
        for (ch in letters.uppercase()) n = n * 26 + (ch - 'A' + 1)
        return n
    }

    /**
     * XML 实体解码。数字实体（`&#10;` `&#x0A;`）先解——部分生成器把换行写成实体，
     * 而 sharedStrings 走正则提取、拿不到 DOM 已解码的文本。先解数字实体还能避免把 `&amp;#10;` 误解成换行。
     */
    private fun decodeEntities(s: String): String {
        val numeric = Regex("&#(x[0-9A-Fa-f]+|[0-9]+);").replace(s) { m ->
            val v = m.groupValues[1]
            val code = if (v.startsWith("x")) v.substring(1).toIntOrNull(16) else v.toIntOrNull()
            if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else m.value
        }
        return numeric.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&apos;", "'")
    }
}
