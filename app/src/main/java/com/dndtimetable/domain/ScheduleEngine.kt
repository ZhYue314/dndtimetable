package com.dndtimetable.domain

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.CourseDndRule
import com.dndtimetable.data.db.DndPolicy
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.SpecialDateType
import com.dndtimetable.data.db.WeekType
import com.dndtimetable.data.prefs.DndConfig
import com.dndtimetable.data.prefs.DndConfigCode
import com.dndtimetable.data.prefs.Period
import com.dndtimetable.data.prefs.PeriodRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 计算「第几周」「课程今天是否生效」以及「免打扰时间窗与下一次触发」。
 * 静音窗口由课程的【节次区间】叠加【该节是否自动静音】决定，并受
 * ① 课程整体策略（[Course.dndPolicy]：继承/关闭/单独方式）
 * ② 单节课免打扰规则（[CourseDndRule]：本学期/本周/本节课三级抑制）
 * 两者约束。
 */
object ScheduleEngine {

    data class Event(val timeMillis: Long, val start: Boolean)
    data class SyncResult(val silentNow: Boolean, val next: Event?, val currentWindowEndMs: Long? = null)

    /** 通知事件类型：PRE=会话首节课前提醒；CONTENT=内容刷新（上课中/课间）；HIDE=当天结束撤通知。 */
    enum class NoticeKind { PRE, CONTENT, HIDE }

    data class NoticeEvent(val timeMillis: Long, val kind: NoticeKind)

    const val NOTICE_LEAD_MS = 20 * 60 * 1000L     // 兼容保留（默认提前20分钟）

    /** 法定节假日已播种进特殊日期表（LegalHolidays + ScheduleHelper），引擎只认表。 */

    // ---------- 学期锚点与周次 ----------

    /**
     * 学期锚点归一：把用户选的开学日期落到**该周周一**。
     * 课表的第 N 周是高亮列「周一..周日」的整周，锚点必须是周一，
     * 否则周次与网格列错位（出现过「9/22 周二却排在周一列」的日期错乱）。
     */
    fun anchorStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    /**
     * 第 1 周的周一。课表的「第 N 周」永远是整周（周一..周日），所以周次一律以周一为锚点：
     * - 新保存的学期日期已归一到周一（[anchorStart]），锚点是它本身；
     * - 历史数据里开学日期不是周一（早于本次修复）时，取它所在周的周一——
     *   这样"周次"和网格里的星期列始终对齐（修掉「9/22 周二却排在周一列」的日期错乱），
     *   且学期起始日之前的那几天仍然算 0（学期外）。
     */
    fun weekAnchor(semester: Semester): LocalDate = anchorStart(LocalDate.ofEpochDay(semester.startEpochDay))

    fun weekNumber(semester: Semester, date: LocalDate): Int {
        val start = LocalDate.ofEpochDay(semester.startEpochDay)
        if (date.isBefore(start)) return 0
        val day = ChronoUnit.DAYS.between(weekAnchor(semester), date)
        if (day < 0) return 0
        return (day / 7).toInt() + 1
    }

    /** 由「今天是本学期第 week 周」反推第 1 周第一天（= 本周一往前 week-1 周）。 */
    fun semesterStartFromWeek(today: LocalDate, week: Int): LocalDate =
        today.minusDays((today.dayOfWeek.value - 1).toLong()).minusWeeks((week - 1).toLong())

    fun weekMatches(type: WeekType, week: Int): Boolean = when (type) {
        WeekType.ALL -> true
        WeekType.ODD -> week % 2 == 1
        WeekType.EVEN -> week % 2 == 0
    }

    /**
     * 从课程里删掉第 [from]..[to] 周后剩下的课（删除时问「仅当周 / 指定周数 / 整个学期」用）。
     * 返回 0..2 条：空 = 整门课删掉；1 条 = 只剩一段（保留原 id，原地更新）；
     * 2 条 = 拆成前后两段，[0] 保留原 id、[1] 是 id=0 的新行（调用方 insert）。
     * 与课程周范围不相交、或范围内这门课根本没有课（单双周对不上）时原样返回单条（调用方视为没删掉东西）。
     * 后者只在「调休日顶上来源日的课」时能碰到：点到的课按来源周判定，未必等于当前周。
     */
    fun minusWeeks(c: Course, from: Int, to: Int): List<Course> {
        val s = maxOf(from, c.startWeek)
        val e = minOf(to, c.endWeek)
        if (s > e || (s..e).none { weekMatches(c.weekType, it) }) return listOf(c)
        val left = if (c.startWeek < s) c.copy(endWeek = s - 1) else null
        val right = if (e < c.endWeek) c.copy(startWeek = e + 1) else null
        return when {
            left == null && right == null -> emptyList()
            left == null || right == null -> listOf(left ?: right!!)
            else -> listOf(left, right.copy(id = 0))
        }
    }

    /** 某一周（周次编号）的周一..周日；未设学期时返回 null。 */
    fun weekRange(semester: Semester?, week: Int): ClosedRange<Long>? {
        if (semester == null || week < 1) return null
        val mon = weekAnchor(semester).plusWeeks((week - 1).toLong())
        return mon.toEpochDay()..mon.plusDays(6).toEpochDay()
    }

    /** 整个学期的日期范围（第 1 周周一..最后一周周日）；未设学期时返回 null。 */
    fun semesterRange(semester: Semester?): ClosedRange<Long>? {
        if (semester == null || semester.totalWeeks < 1) return null
        val mon = weekAnchor(semester)
        return mon.toEpochDay()..mon.plusWeeks(semester.totalWeeks.toLong()).minusDays(1).toEpochDay()
    }

    /** 日期 → 星期序号（1=周一..7=周日）。等价于 [LocalDate.getDayOfWeek]，集中一处避免各处口径不一。 */
    fun weekdayOf(date: LocalDate): Int = date.dayOfWeek.value

    /** 从 [DayOfWeek.MONDAY] 起的星期中文名（单字）。 */
    private val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
    fun weekdayName(weekday: Int): String = dayNames.getOrElse(weekday - 1) { "?" }

    // ---------- 单节课免打扰（本学期 / 本周 / 本节课） ----------

    /**
     * 该课在 [date] 的第 [period] 节是否被「不自动免打扰」规则命中。
     * 同一层级重复设置时以最后一条为准（enabled=false 可覆盖更宽的抑制，用于「恢复免打扰」）。
     */
    fun dndSuppressed(rules: List<CourseDndRule>, courseId: Long, dateEpoch: Long, period: Int): Boolean {
        var suppressed = false
        for (r in rules) {
            if (r.courseId != courseId) continue
            if (dateEpoch < r.dateStart || dateEpoch > r.dateEnd) continue
            if (r.periodStart != null && r.periodEnd != null && period !in r.periodStart..r.periodEnd) continue
            suppressed = r.enabled
        }
        return suppressed
    }

    /** 该课整体策略；CUSTOM 时返回课程自定义方式，其余返回 null（=继承全局/关闭）。 */
    fun customConfig(course: Course): DndConfig? =
        if (course.dndPolicy == DndPolicy.CUSTOM) DndConfigCode.decode(course.dndCode) else null

    /** 该课在本学期是否整体不自动免打扰。 */
    fun dndOffForSemester(course: Course): Boolean = course.dndPolicy == DndPolicy.OFF

    /** 网络课/MOOC 识别（导入时默认关闭自动免打扰）。 */
    private val onlineKeywords = listOf(
        "mooc", "spoc", "慕课", "网课", "网络课", "网络课程", "在线课程", "线上课程", "线上课", "云课堂"
    )
    private val onlineLocationKeywords = listOf("网络", "线上", "在线", "慕课", "mooc")

    /**
     * 是否为线上课（MOOC/网课/线上课）。
     * 判据：课名/教师含线上课关键词，或地点是「网络/线上」这类无实体教室的写法。
     * 这类课不需要把手机静音（学生在宿舍/任意地点看视频，甚至需要外放），
     * 因此导入时默认「不自动免打扰」，用户仍可在课程详情里改回来。
     */
    fun isOnlineCourse(name: String?, teacher: String?, location: String?): Boolean {
        val n = name.orEmpty().lowercase()
        if (onlineKeywords.any { n.contains(it) }) return true
        val t = teacher.orEmpty().lowercase()
        if (onlineKeywords.any { t.contains(it) }) return true
        val l = location.orEmpty().lowercase()
        if (l.isNotBlank() && onlineLocationKeywords.any { l.contains(it) }) return true
        return false
    }

    fun isOnlineCourse(c: Course): Boolean = isOnlineCourse(c.name, c.teacher, c.location)

    /** 导入/新建课程时的默认策略：线上课 → 关闭自动免打扰，其余继承全局。 */
    fun defaultPolicyFor(name: String?, teacher: String?, location: String?): DndPolicy =
        if (isOnlineCourse(name, teacher, location)) DndPolicy.OFF else DndPolicy.INHERIT

    // ---------- 调休补课：补的是「哪一天」的课 ----------

    /**
     * 覆盖 [date] 的调休补课项（同一天多条时取第一条）。**未设置来源日也算补课日**：
     * 「班」角标与周末免打扰例外照常生效，课表按当天星期几显示，头天提醒用户补全结束日期；
     * 设置 [SpecialDate.sourceEpochDay] 后课表同步到来源日。
     */
    fun makeupFor(specials: List<SpecialDate>, dateEpoch: Long): SpecialDate? = specials.firstOrNull {
        it.enabled && it.type == SpecialDateType.MAKEUP &&
            dateEpoch in it.epochDay..(it.endEpochDay ?: it.epochDay)
    }

    fun isHoliday(specials: List<SpecialDate>, dateEpoch: Long): Boolean = specials.any {
        it.enabled && it.type == SpecialDateType.HOLIDAY && dateEpoch in it.epochDay..(it.endEpochDay ?: it.epochDay)
    }

    /** 调休补课提醒：[dateEpoch] 补课日；提醒时刻 = 前一天 20:00（[remindMs]）。 */
    data class MakeupNotice(val dateEpoch: Long, val sourceEpochDay: Long?, val name: String?, val remindMs: Long)

    /**
     * 下一个需要提醒的补课日：提醒时刻未过；**未设置结束日期的补课日必提醒**（让用户补全）；
     * 已设置的按来源日**真有课**才提醒（没课不打扰）。只返回最近一条；
     * 闹钟触发后 scheduleNext 重排自然轮到再下一条，不会重复提醒。
     */
    fun nextMakeupNotice(
        semester: Semester?, courses: List<Course>, specials: List<SpecialDate>,
        zone: ZoneId, now: Instant
    ): MakeupNotice? = specials.asSequence()
        .filter { it.enabled && it.type == SpecialDateType.MAKEUP }
        .map { sp ->
            val d = LocalDate.ofEpochDay(sp.epochDay)
            MakeupNotice(
                sp.epochDay, sp.sourceEpochDay, sp.name,
                d.minusDays(1).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
            )
        }
        .filter { it.remindMs > now.toEpochMilli() }
        .filter {
            it.sourceEpochDay == null ||
                activeDayCourses(semester, courses, specials, LocalDate.ofEpochDay(it.dateEpoch)).isNotEmpty()
        }
        .minByOrNull { it.remindMs }

    /** 补课提醒文案（在补课日前一天 20:00 发出，故写「明天」）。 */
    fun makeupNoticeText(n: MakeupNotice): String {
        val d = LocalDate.ofEpochDay(n.dateEpoch)
        val wd = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.CHINA)
        val base = "明天 ${d.monthValue}/${d.dayOfMonth}（$wd）是调休补课日"
        val src = n.sourceEpochDay?.let { LocalDate.ofEpochDay(it) }
            ?: return "$base，未设置结束日期（补哪天的课），去「设置 → 假期设置」补全。"
        if (src == d) return "$base，当天照常上课。"
        return "$base，按 ${src.monthValue}/${src.dayOfMonth} 的课上。"
    }

    /**
     * 把某天「按哪一天的课来上」翻译成该上的课程列表（周次/单双周都按来源日判定）。
     * 注意：来源日本身往往是法定假日（如 9/20 补 10/6 的课，10/6 在国庆假期里），
     * 这里取的是它**名义上**的课表，不能再查一次放假——否则整个调休功能会被清空。
     */
    private fun coursesOfSourceDay(
        semester: Semester?, courses: List<Course>, source: LocalDate
    ): List<Course> {
        val week = semester?.let { weekNumber(it, source) } ?: 1
        if (week <= 0) return emptyList()
        val dow = weekdayOf(source)
        return courses.filter {
            it.enabled && it.weekday == dow &&
                week in it.startWeek..it.endWeek && weekMatches(it.weekType, week)
        }
    }

    /**
     * 返回某天**实际**应该上的课（用于「今日课程」、小组件与免打扰；未设学期则返回空）。
     * - 放假 → 无课；
     * - 调休补课（指定了来源日/来源课程）→ **顶上来源那一天的课**（如 9/20 补 10/6 的课），
     *   而不是照常上周几的课：这正是调休的语义，保留会让用户上错课；
     * - 其余 → 当天的课（星期 + 周次 + 单双周）。
     */
    fun activeDayCourses(
        semester: Semester?,
        courses: List<Course>,
        specials: List<SpecialDate>,
        date: LocalDate
    ): List<Course> {
        if (semester == null) return emptyList()
        val week = weekNumber(semester, date)
        if (week <= 0) return emptyList()
        if (isHoliday(specials, date.toEpochDay())) return emptyList()
        return dayCoursesIgnoringHoliday(semester, courses, specials, date, week)
    }

    /**
     * 课表网格展示专用：放假日照常显示当天名义上的课（网格是学期模板视图，休/班角标另行提示），
     * 调休日照样顶上来那天的课。真正「今天上什么/要不要静音」仍以 [activeDayCourses] 为准（放假无课）。
     */
    fun gridDayCourses(
        semester: Semester?,
        courses: List<Course>,
        specials: List<SpecialDate>,
        date: LocalDate
    ): List<Course> {
        if (semester == null) return emptyList()
        val week = weekNumber(semester, date)
        if (week <= 0) return emptyList()
        return dayCoursesIgnoringHoliday(semester, courses, specials, date, week)
    }

    /** 某天要显示的课（不看放假）：调休日顶上来源日的课，否则取当天名义课表；按内容去重。 */
    private fun dayCoursesIgnoringHoliday(
        semester: Semester, courses: List<Course>, specials: List<SpecialDate>, date: LocalDate, week: Int
    ): List<Course> {
        val makeup = makeupFor(specials, date.toEpochDay())
        val base = when {
            makeup?.sourceEpochDay != null ->
                coursesOfSourceDay(semester, courses, LocalDate.ofEpochDay(makeup.sourceEpochDay))
            // 兼容旧数据：只关联了某门课 → 那天补这一门
            makeup?.courseId != null -> courses.filter { it.enabled && it.id == makeup.courseId }
            else -> courses.filter {
                it.enabled && it.weekday == weekdayOf(date) &&
                    week in it.startWeek..it.endWeek && weekMatches(it.weekType, week)
            }
        }
        return base.distinctBy {
            listOf(it.name, it.weekday, it.startPeriod, it.endPeriod, it.startWeek, it.endWeek, it.weekType, it.teacher, it.location)
        }
    }

    // ---------- 静音窗口与下一事件 ----------

    /**
     * 该日期是否参与自动免打扰：周末（周六/周日）默认不参与（设置可开）；补课/调休日始终照常。
     * DND 排程与课表「不静音」角标共用同一判定，避免显示与实际不一致。
     */
    fun dndAppliesOn(specials: List<SpecialDate>, date: LocalDate, weekendDnd: Boolean): Boolean =
        weekendDnd || date.dayOfWeek.value < 6 || makeupFor(specials, date.toEpochDay()) != null

    fun compute(
        semester: Semester?,
        courses: List<Course>,
        specials: List<SpecialDate>,
        periods: List<Period>,
        zone: ZoneId,
        now: Instant,
        bufferPreSec: Int,
        bufferPostSec: Int,
        dndRules: List<CourseDndRule> = emptyList(),
        /** 周末是否也自动免打扰；默认 false（周六/周日不静音）。补课/调休日照常生效，与开关无关。 */
        weekendDnd: Boolean = true
    ): SyncResult {
        val todayDate = now.atZone(zone).toLocalDate()
        val holidayEpochs = specials.asSequence()
            .filter { it.enabled && it.type == SpecialDateType.HOLIDAY }
            .flatMap { it.epochDay..(it.endEpochDay ?: it.epochDay) }
            .toSet()

        val windows = mutableListOf<Pair<Instant, Instant>>()
        // 扫描期覆盖到学期结束（或课程 endWeek 最远一天），不再只扫 7 天：
        // 长假/连续无课日若提前排空闹钟，节后首日课将无人触发（旧实现 bug）。
        val scanEnd = scanEndEpoch(semester, courses, todayDate.toEpochDay())
        var off = 0L
        while (true) {
            val date = todayDate.plusDays(off)
            if (date.toEpochDay() > scanEnd) break
            val dateEpoch = date.toEpochDay()
            val isHoliday = dateEpoch in holidayEpochs
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayWindows = mutableListOf<Pair<Instant, Instant>>()

            if (!isHoliday) {
                // 周末默认不排静音；调休补课日（周末补课）照常——用户要求的正是「补课、调课除外」
                if (dndAppliesOn(specials, date, weekendDnd)) {
                    // 每天按 activeDayCourses 取课：调休补课日自动换成「来源那一天的课」
                    for (c in activeDayCourses(semester, courses, specials, date)) {
                        addSilence(c, periods, dayStart, bufferPreSec, bufferPostSec, dndRules, dateEpoch, dayWindows)
                    }
                }
            }
            val validDay = dayWindows.filter { it.second.isAfter(it.first) }
            windows.addAll(validDay)
            // 遇到第一个「今天之后」有课的日期即可停：更远日子的事件必然更晚，无需再扫。
            if (off > 0 && validDay.isNotEmpty()) break
            off++
        }
        val nowMs = now.toEpochMilli()
        val curWindow = windows.firstOrNull { it.first.toEpochMilli() <= nowMs && nowMs < it.second.toEpochMilli() }
        val silentNow = curWindow != null
        val next = windows
            .flatMap { listOf(Event(it.first.toEpochMilli(), true), Event(it.second.toEpochMilli(), false)) }
            .filter { it.timeMillis > nowMs }
            .minByOrNull { it.timeMillis }
        // currentWindowEndMs 供"用户手动关闭本节自动静音"的抑制期使用（抑制到本节窗口结束）
        return SyncResult(silentNow, next, curWindow?.second?.toEpochMilli())
    }

    /** 一门课在 [dateEpoch] 这一天的生效节次（自动静音 + 未被单节课规则关掉）。 */
    fun effectivePeriods(
        c: Course, periods: List<Period>, rules: List<CourseDndRule>, dateEpoch: Long
    ): List<Pair<Int, Period>> {
        if (c.dndPolicy == DndPolicy.OFF) return emptyList()
        return (c.startPeriod..c.endPeriod).mapNotNull { p ->
            val per = periods.getOrNull(p - 1) ?: return@mapNotNull null
            if (!per.auto) return@mapNotNull null
            if (dndSuppressed(rules, c.id, dateEpoch, p)) return@mapNotNull null
            p to per
        }
    }

    private fun addSilence(
        c: Course, periods: List<Period>, dayStart: Instant,
        bufferPreSec: Int, bufferPostSec: Int, rules: List<CourseDndRule>, dateEpoch: Long,
        out: MutableList<Pair<Instant, Instant>>
    ) {
        for ((_, per) in effectivePeriods(c, periods, rules, dateEpoch)) {
            val s = dayStart.plusSeconds(per.startMin * 60L - bufferPreSec)
            val e = dayStart.plusSeconds(per.endMin * 60L - bufferPostSec)   // 正=提前恢复，负=延后恢复
            out.add(s to e)
        }
    }

    /**
     * 会话首节课（上午/下午/晚上各一段）的开始时间：相邻节次间隔 > 大课间 即开启新会话。
     * 只有会话首节发「上课前提醒」（如 8:00 第一节课→课前 lead 分钟；下午第一大节无课则 16:00 首节）。
     */
    fun sessionFirstStarts(wins: List<Pair<Long, Long>>, bigBreakMin: Int): List<Long> {
        val out = ArrayList<Long>()
        var prevEnd: Long? = null
        for ((s, e) in wins) {
            if (prevEnd == null || s - prevEnd > bigBreakMin * 60_000L) out.add(s)
            prevEnd = e
        }
        return out
    }

    /**
     * 通知节次窗口：start/end 为缓冲后（事件时机），realStart/realEnd 为节次表真实时间（会话/课间类型判定）。
     */
    private data class NoticeWin(
        val start: Instant, val end: Instant, val realStart: Instant, val realEnd: Instant
    )

    /**
     * 当天（或下一个有课日）剩余的所有通知事件：
     * - PRE：各会话首节课前 lead 分钟（lead=0 不排）；
     * - CONTENT：每个自动节次的开始/结束（触发时重算内容：上课中 / 小课间 / 大课间）；
     * - HIDE：当天最后一节结束（撤掉通知）。
     * 事件时机（开始/结束/课前窗口）随「上下课缓冲」偏移，与 DND 事件一致；
     * 会话切分与课间类型按节次表真实时间（缓冲只移动时机，不改变"这是小课间"的事实）。
     */
    fun nextNoticeEvents(
        semester: Semester?,
        courses: List<Course>,
        specials: List<SpecialDate>,
        periods: List<Period>,
        rule: PeriodRule,
        zone: ZoneId,
        now: Instant,
        leadMin: Int = 20,
        bufferPreSec: Int = 0,
        bufferPostSec: Int = 0,
        dndRules: List<CourseDndRule> = emptyList()
    ): List<NoticeEvent> {
        val today = now.atZone(zone).toLocalDate()
        val nowMs = now.toEpochMilli()
        // 与 compute 同扫描期：今天的链排完后，直接取第一个还有课的日期（长假后/今日课已结束不再漏提醒）。
        val scanEnd = scanEndEpoch(semester, courses, today.toEpochDay())
        var off = 0L
        while (true) {
            val date = today.plusDays(off)
            if (date.toEpochDay() > scanEnd) return emptyList()
            val dateEpoch = date.toEpochDay()
            val dayStart = date.atStartOfDay(zone).toInstant()
            // 仅统计「自动静音」的节次；被关闭的节次（晚课/单节课关闭/线上课）不显示通知
            val wins = activeDayCourses(semester, courses, specials, date).flatMap { c ->
                effectivePeriods(c, periods, dndRules, dateEpoch).map { (_, per) ->
                    NoticeWin(
                        dayStart.plusSeconds(per.startMin * 60L - bufferPreSec),
                        dayStart.plusSeconds(per.endMin * 60L - bufferPostSec),
                        dayStart.plusSeconds(per.startMin * 60L),
                        dayStart.plusSeconds(per.endMin * 60L)
                    )
                }
            }.sortedBy { it.start.toEpochMilli() }

            val events = buildDayNoticeEvents(wins, leadMin, rule)
            val remaining = events.filter { it.timeMillis > nowMs }
            if (remaining.isNotEmpty()) return remaining
            off++
        }
    }

    /** 某个有课日的全部通知事件（含已过去的；由调用方过滤）。 */
    private fun buildDayNoticeEvents(
        wins: List<NoticeWin>,
        leadMin: Int,
        rule: PeriodRule
    ): List<NoticeEvent> {
        val events = ArrayList<NoticeEvent>()
        if (wins.isEmpty()) return events
        val firsts = sessionFirstStarts(wins.map { it.realStart.toEpochMilli() to it.realEnd.toEpochMilli() }, rule.bigBreakMin)
        for ((i, w) in wins.withIndex()) {
            val sMs = w.start.toEpochMilli()
            val eMs = w.end.toEpochMilli()
            // 会话首节（按真实时间判定首节，窗口按缓冲开始）：课前 lead 分钟提醒（lead=0 表示关闭提醒，不排）
            if (leadMin > 0 && w.realStart.toEpochMilli() in firsts) events.add(NoticeEvent(sMs - leadMin * 60_000L, NoticeKind.PRE))
            // 上课中：节次开始刷新内容（不依赖 DND 事件，节次关闭自动静音时也能更新）
            events.add(NoticeEvent(sMs, NoticeKind.CONTENT))
            // 节次结束：课间内容（最后一节结束 = 撤通知）
            events.add(NoticeEvent(eMs, if (i == wins.lastIndex) NoticeKind.HIDE else NoticeKind.CONTENT))
        }
        return events.sortedBy { it.timeMillis }
    }

    /**
     * 闹钟扫描终点（epochDay，含）：默认今天+30 天；有学期时覆盖到学期结束，
     * 并考虑 endWeek 超出总周数的课程；保证长假/长空窗不会把未来的课排空。
     */
    private fun scanEndEpoch(semester: Semester?, courses: List<Course>, todayEpoch: Long): Long {
        var end = todayEpoch + 30L
        if (semester != null && semester.totalWeeks > 0) {
            end = maxOf(end, LocalDate.ofEpochDay(semester.startEpochDay)
                .plusWeeks(semester.totalWeeks.toLong()).toEpochDay())
            for (c in courses) {
                if (!c.enabled) continue
                end = maxOf(end, LocalDate.ofEpochDay(semester.startEpochDay)
                    .plusWeeks(c.endWeek.toLong()).toEpochDay())
            }
        }
        return end
    }
}
