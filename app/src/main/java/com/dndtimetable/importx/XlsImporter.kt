package com.dndtimetable.importx

import com.dndtimetable.data.db.Course
import java.io.ByteArrayOutputStream

/**
 * 教务系统导出的 .xls（BIFF8/OLE2）个人课表解析：直接读「大节 × 星期」网格。
 * 单元格文本形如：课程名 / 老师 / 周次([周|双周])[xx-xx节] / 地点（同一格可含多门课，空行分隔）。
 * 不引入 POI：自实现最小 OLE2 复合文档 + BIFF8 记录读取（SST/LABELSST/NUMBER/RK/MULRK）。
 */
object XlsImporter {

    data class Result(
        val courses: List<Course>,
        val notes: List<String>,
        val warnings: List<String> = emptyList(),
        /** 节次超出当前节次表时建议调到的节数（无建议 null），供 UI 一键调整后重新导入。 */
        val suggestPeriods: Int? = null
    )

    fun isXls(bytes: ByteArray): Boolean = Ole2.isOle2(bytes)

    fun parse(bytes: ByteArray, maxPeriod: Int, defaultWeeks: Int = 0): Result {
        val workbook = Ole2.extractWorkbook(bytes) ?: throw Exception("不是有效的 .xls 文件")
        val cells = Biff.readCells(workbook)
        val courses = mutableListOf<Course>()
        val notes = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var fallback: List<String> = emptyList()
        var suggest: Int? = null
        var fallbackSuggest: Int? = null
        // 每个 sheet 都试（与 xlsx 一致）：有「星期」的当课表；只有「课程名称」的当无课表课程（D 列）
        for (sheet in cells.keys.map { it.first }.distinct().sorted()) {
            val grid = cells.filterKeys { it.first == sheet }.mapKeys { it.key.second to it.key.third }
            val rows = grid.keys.map { it.first }.distinct().sorted()
            val headerRow = rows.firstOrNull { r ->
                grid.filterKeys { it.first == r }.values.any { it.contains("星期") || it.contains("课程名称") }
            } ?: continue
            if (grid.filterKeys { it.first == headerRow }.values.any { it.contains("星期") }) {
                val out = GridParser.parseCascadeOutcome(grid, maxPeriod, defaultWeeks)
                if (out.courses.isNotEmpty()) {
                    courses.addAll(out.courses)
                    warnings.addAll(out.warnings)
                    out.suggestedPeriods?.let { suggest = maxOf(suggest ?: 0, it) }
                } else if (out.warnings.size > fallback.size) {
                    fallback = out.warnings
                    fallbackSuggest = out.suggestedPeriods
                }
            } else {
                for (r in rows) {
                    if (r <= headerRow) continue
                    val name = grid[r to 3]?.trim().orEmpty()   // D 列
                    if (name.isNotEmpty()) notes.add(name)
                }
            }
        }
        return Result(
            sortCourses(courses), notes,
            if (courses.isEmpty()) fallback else warnings,
            if (courses.isEmpty()) fallbackSuggest else suggest
        )
    }

    // ---------------- OLE2 复合文档 ----------------

    private object Ole2 {
        private val MAGIC = byteArrayOf(
            0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(),
            0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()
        )

        fun isOle2(b: ByteArray): Boolean =
            b.size >= 8 && MAGIC.indices.all { b[it] == MAGIC[it] }

        fun extractWorkbook(b: ByteArray): ByteArray? {
            if (!isOle2(b)) return null
            val sectorShift = u16(b, 0x1E)
            val miniShift = u16(b, 0x20)
            if (sectorShift !in 7..14 || miniShift !in 4..10) return null
            val sectorSize = 1 shl sectorShift
            val miniSize = 1 shl miniShift
            val dirStart = u32(b, 0x30)
            val miniCutoff = u32(b, 0x38)
            val miniFatStart = u32(b, 0x3C)
            val difatStart = u32(b, 0x44)

            // DIFAT：头内 109 项 + 后续 DIFAT 扇区
            val fatSectors = ArrayList<Int>()
            for (i in 0 until 109) {
                val s = u32(b, 0x4C + i * 4)
                if (s >= 0) fatSectors.add(s)
            }
            var dif = difatStart
            var guard = 0
            while (dif >= 0 && guard++ < 4096) {
                val base = (dif + 1) * sectorSize
                if (base + sectorSize > b.size) break
                for (i in 0 until sectorSize / 4 - 1) {
                    val s = u32(b, base + i * 4)
                    if (s >= 0) fatSectors.add(s)
                }
                dif = u32(b, base + sectorSize - 4)
            }

            // FAT 展开
            val fat = ArrayList<Int>(fatSectors.size * (sectorSize / 4))
            for (fs in fatSectors) {
                val base = (fs + 1) * sectorSize
                if (base + sectorSize > b.size) continue
                for (i in 0 until sectorSize / 4) fat.add(u32(b, base + i * 4))
            }

            // 目录：找 Workbook/Book 流
            val dir = readChain(b, fat, dirStart, -1, sectorSize)
            var wbStart = -1
            var wbSize = -1
            var rootStart = -1
            var off = 0
            while (off + 128 <= dir.size) {
                val nameLen = u16(dir, off + 64)
                val type = dir[off + 66].toInt() and 0xFF
                val start = u32(dir, off + 116)
                val size = u32(dir, off + 120)
                if (type == 5) rootStart = start
                if (type == 2 && nameLen >= 2) {
                    val name = String(dir, off, nameLen - 2, Charsets.UTF_16LE)
                    if (name == "Workbook" || name == "Book") { wbStart = start; wbSize = size }
                }
                off += 128
            }
            if (wbStart < 0) return null
            return if (wbSize in 0 until miniCutoff) {
                // 小流：Workbook 数据存放在根条目的小流里，用 miniFAT 链
                val miniStream = readChain(b, fat, rootStart, -1, sectorSize)
                val miniFatBytes = readChain(b, fat, miniFatStart, -1, sectorSize)
                val miniFat = IntArray(miniFatBytes.size / 4) { u32(miniFatBytes, it * 4) }
                readMiniChain(miniStream, miniFat, wbStart, wbSize, miniSize)
            } else {
                readChain(b, fat, wbStart, wbSize, sectorSize)
            }
        }

        private fun readChain(b: ByteArray, fat: List<Int>, start: Int, size: Int, sectorSize: Int): ByteArray {
            val out = ByteArrayOutputStream()
            var s = start
            var guard = 0
            while (s >= 0 && guard++ < 1_000_000) {
                val base = (s + 1) * sectorSize
                if (base < 0 || base + sectorSize > b.size) break
                out.write(b, base, sectorSize)
                s = fat.getOrElse(s) { -1 }
            }
            val arr = out.toByteArray()
            return if (size in 0 until arr.size) arr.copyOf(size) else arr
        }

        private fun readMiniChain(container: ByteArray, miniFat: IntArray, start: Int, size: Int, miniSize: Int): ByteArray {
            val out = ByteArrayOutputStream()
            var s = start
            var guard = 0
            while (s >= 0 && guard++ < 1_000_000) {
                val base = s * miniSize
                if (base < 0 || base + miniSize > container.size) break
                out.write(container, base, miniSize)
                s = miniFat.getOrElse(s) { -1 }
            }
            val arr = out.toByteArray()
            return if (size in 0 until arr.size) arr.copyOf(size) else arr
        }
    }

    // ---------------- BIFF8 记录 ----------------

    private object Biff {
        fun readCells(d: ByteArray): Map<Triple<Int, Int, Int>, String> {
            val cells = HashMap<Triple<Int, Int, Int>, String>()
            val sst = ArrayList<String>()
            var i = 0
            var bofCount = 0
            var sheet = -1
            while (i + 4 <= d.size) {
                val type = u16(d, i)
                val len = u16(d, i + 2)
                val body = i + 4
                if (body + len > d.size) break
                when (type) {
                    0x0809 -> {   // BOF：第 1 个是工作簿全局，之后依次是各工作表
                        bofCount++
                        if (bofCount >= 2) sheet = bofCount - 2
                    }
                    0x00FC -> {   // SST（含 CONTINUE）
                        val segs = ArrayList<ByteArray>()
                        segs.add(d.copyOfRange(body, body + len))
                        var j = body + len
                        while (j + 4 <= d.size && u16(d, j) == 0x003C) {
                            val l2 = u16(d, j + 2)
                            if (j + 4 + l2 > d.size) break
                            segs.add(d.copyOfRange(j + 4, j + 4 + l2))
                            j += 4 + l2
                        }
                        parseSst(segs, sst)
                        i = j
                        continue
                    }
                    0x00FD -> if (sheet >= 0 && len >= 10) {   // LABELSST
                        val row = u16(d, body)
                        val col = u16(d, body + 2)
                        val isst = u32(d, body + 6)
                        cells[Triple(sheet, row, col)] = sst.getOrElse(isst) { "" }
                    }
                    0x0203 -> if (sheet >= 0 && len >= 14) {   // NUMBER
                        val row = u16(d, body)
                        val col = u16(d, body + 2)
                        val v = Double.fromBits(u64(d, body + 6))
                        cells[Triple(sheet, row, col)] = numText(v)
                    }
                    0x027E -> if (sheet >= 0 && len >= 10) {   // RK
                        val row = u16(d, body)
                        val col = u16(d, body + 2)
                        cells[Triple(sheet, row, col)] = numText(rkToDouble(u32(d, body + 6)))
                    }
                    0x00BD -> if (sheet >= 0 && len >= 6) {    // MULRK
                        val row = u16(d, body)
                        var p = body + 2
                        var col = u16(d, p); p += 2
                        while (p + 6 <= body + len - 2) {
                            cells[Triple(sheet, row, col)] = numText(rkToDouble(u32(d, p + 2)))
                            p += 6
                            col++
                        }
                    }
                }
                i = body + len
            }
            return cells
        }

        private fun parseSst(segments: List<ByteArray>, out: MutableList<String>) {
            var si = 0
            var pos = 0
            fun ensure() { while (si < segments.size && pos >= segments[si].size) { si++; pos = 0 } }
            fun u8(): Int { ensure(); return if (si >= segments.size) 0 else segments[si][pos++].toInt() and 0xFF }
            fun u16(): Int = u8() or (u8() shl 8)
            fun u32(): Int = u16() or (u16() shl 16)
            fun skip(n: Int) { repeat(n) { u8() } }

            u32()   // 字符串总数
            val unique = u32()
            repeat(unique) {
                val cch = u16()
                var flags = u8()
                val rich = flags and 0x08 != 0
                val ext = flags and 0x04 != 0
                val cRun = if (rich) u16() else 0
                val cbExt = if (ext) u32() else 0
                val sb = StringBuilder(cch)
                var remaining = cch
                while (remaining > 0) {
                    ensure()
                    if (si >= segments.size) break
                    val high = flags and 0x01 != 0
                    val maxChars = if (high) (segments[si].size - pos) / 2 else segments[si].size - pos
                    val take = minOf(remaining, maxChars)
                    if (high) {
                        repeat(take) {
                            val lo = u8(); val hi = u8()
                            sb.append(((hi shl 8) or lo).toChar())
                        }
                    } else {
                        repeat(take) { sb.append(u8().toChar()) }
                    }
                    remaining -= take
                    if (remaining > 0) flags = u8()   // 跨 CONTINUE：续段首字节为新的编码标志
                }
                skip(cRun * 4)
                skip(cbExt)
                out.add(sb.toString())
            }
        }

        private fun rkToDouble(rk: Int): Double {
            val isInt = rk and 0x02 != 0
            val raw = if (isInt) {
                (rk shr 2).toDouble()
            } else {
                Double.fromBits((rk.toLong() and 0xFFFFFFFCL) shl 32)
            }
            return if (rk and 0x01 != 0) raw / 100.0 else raw
        }

        private fun numText(v: Double): String =
            if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
    }
    // ---------------- 小工具 ----------------

    private fun u16(b: ByteArray, i: Int): Int =
        if (i + 1 < b.size) (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8) else 0

    private fun u32(b: ByteArray, i: Int): Int =
        u16(b, i) or (u16(b, i + 2) shl 16)

    private fun u64(b: ByteArray, i: Int): Long =
        (u32(b, i).toLong() and 0xFFFFFFFFL) or ((u32(b, i + 4).toLong() and 0xFFFFFFFFL) shl 32)
}
