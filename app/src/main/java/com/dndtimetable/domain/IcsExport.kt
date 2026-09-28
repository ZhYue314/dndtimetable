package com.dndtimetable.domain

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.prefs.Period
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 课表导出为 .ics（RFC 5545，UTC 时间；无第三方库、无网络）：
 * 展开整个学期的**实际**课程（单双周过滤、放假跳过、调休补课按来源日），导入系统日历后
 * 即使 App 被系统杀死也能靠日历提醒兜底。周网格视图口径（放假显示名义课）不导出。
 */
object IcsExport {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    /** 一节课在日历里的展开结果（UTC epoch millis）；.ics 导出与系统日历直写共用同一份展开逻辑。 */
    data class Event(
        val uid: String,
        val title: String,
        val location: String?,
        val description: String?,
        val startMs: Long,
        val endMs: Long
    )

    /** 展开整个学期的实际课程（单双周过滤、放假跳过、调休补课按来源日），按时间顺序。 */
    fun expand(
        semester: Semester,
        courses: List<Course>,
        specials: List<SpecialDate>,
        periods: List<Period>,
        zone: ZoneId
    ): List<Event> {
        val out = ArrayList<Event>()
        val first = ScheduleEngine.weekAnchor(semester)
        val dayCount = semester.totalWeeks.coerceAtLeast(0) * 7
        for (i in 0 until dayCount) {
            val date = first.plusDays(i.toLong())
            ScheduleEngine.activeDayCourses(semester, courses, specials, date).forEach { c ->
                val start = periods.getOrNull(c.startPeriod - 1) ?: return@forEach
                val end = periods.getOrNull(c.endPeriod - 1) ?: return@forEach
                out.add(
                    Event(
                        uid = "dndtimetable-${date.toEpochDay()}-${c.id}@dndtimetable",
                        title = c.name,
                        location = c.location?.takeIf { it.isNotBlank() },
                        description = c.teacher?.takeIf { it.isNotBlank() }?.let { "教师：$it" },
                        startMs = date.atTime(start.startMin / 60, start.startMin % 60).atZone(zone).toInstant().toEpochMilli(),
                        endMs = date.atTime(end.endMin / 60, end.endMin % 60).atZone(zone).toInstant().toEpochMilli()
                    )
                )
            }
        }
        return out
    }

    fun build(
        semester: Semester,
        courses: List<Course>,
        specials: List<SpecialDate>,
        periods: List<Period>,
        zone: ZoneId,
        now: Instant
    ): String {
        val sb = StringBuilder()
        sb.append("BEGIN:VCALENDAR\r\n")
        sb.append("VERSION:2.0\r\n")
        sb.append("PRODID:-//dndtimetable//课表导出//CN\r\n")
        sb.append("CALSCALE:GREGORIAN\r\n")
        sb.append("METHOD:PUBLISH\r\n")
        prop(sb, "X-WR-CALNAME", escape(semester.name))
        val dtstamp = stamp.format(now.atZone(ZoneOffset.UTC))
        expand(semester, courses, specials, periods, zone).forEach { e ->
            sb.append("BEGIN:VEVENT\r\n")
            prop(sb, "UID", e.uid)
            prop(sb, "DTSTAMP", dtstamp)
            prop(sb, "DTSTART", stamp.format(Instant.ofEpochMilli(e.startMs).atZone(ZoneOffset.UTC)))
            prop(sb, "DTEND", stamp.format(Instant.ofEpochMilli(e.endMs).atZone(ZoneOffset.UTC)))
            prop(sb, "SUMMARY", escape(e.title))
            e.location?.let { prop(sb, "LOCATION", escape(it)) }
            e.description?.let { prop(sb, "DESCRIPTION", escape(it)) }
            sb.append("END:VEVENT\r\n")
        }
        sb.append("END:VCALENDAR\r\n")
        return sb.toString()
    }

    /** RFC 5545 文本转义。 */
    internal fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\n")

    /** 属性行 + RFC 折行：每行含前导空格不超过 75 个 UTF-8 字节，续行以空格开头。 */
    private fun prop(sb: StringBuilder, name: String, value: String) {
        val line = "$name:$value"
        var count = 0
        for (ch in line) {
            val n = ch.toString().toByteArray(Charsets.UTF_8).size
            if (count + n > 75) {
                sb.append("\r\n ")
                count = 1   // 续行的前导空格计入行宽
            }
            sb.append(ch)
            count += n
        }
        sb.append("\r\n")
    }
}
