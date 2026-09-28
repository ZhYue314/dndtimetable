package com.dndtimetable.domain

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.CourseDndRule
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.prefs.DndConfig
import com.dndtimetable.data.prefs.Period
import com.dndtimetable.data.prefs.PeriodRule
import com.dndtimetable.data.prefs.PeriodTable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 通知阶段：决定常驻状态卡当前展示哪一类内容。
 * 与时间严格一一对应（按节次规则分类），保证"下课不会显示上课通知、上课前提醒不会变成大课间通知"：
 * - PRE：会话（上午/下午等）首节课前 lead 分钟起；
 * - SMALL_BREAK：同大节内两节课之间（间隔 ≤ 规则小课间）；
 * - BIG_BREAK：大节之间 / 大段休息（间隔 > 小课间，如大课间、午休、晚课前）；
 * - IN_CLASS：正在上课（按节次窗口，不含课间）；
 * - HIDDEN：当天通知窗口外（首节前 lead 之前 / 最后一节结束后）。
 */
enum class NoticePhase { HIDDEN, PRE, SMALL_BREAK, BIG_BREAK, IN_CLASS }

/** 想给用户/通知/小组件展示的当前状态快照。 */
data class Status(
    val silentNow: Boolean,
    val nextText: String?,          // 如 "09-03 08:00 开启"
    val nextClock: String?,         // 如 "08:00"
    val nextStart: Boolean?,        // 下一个事件是否为「开启」
    val todayCount: Int,
    val currentCourse: String?,     // 当前正在上的课（若有，按节次窗口判定）
    val showNotice: Boolean,        // 是否应显示通知（首节前 lead 分钟起，直到末节结束）
    val currentCourseId: Long? = null,   // 当前正在上的课 id（单门课单独静音方式用）
    val currentCourseDnd: DndConfig? = null,   // 当前正在上的课单独指定的静音方式（null=用全局）
    val nextCourseName: String? = null,   // 下一节课（课间/课前展示用）
    val nextCourseRoom: String? = null,
    val nextStartClock: String? = null,
    val inBreak: Boolean = false,   // 当前处于课间（当天有课、不在课上）
    val smallBreak: Boolean = false, // 小课间（与下一节间隔 ≤ 规则小课间）；否则为大课间
    val phase: NoticePhase = NoticePhase.HIDDEN
)

private val fmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 单个自动静音节次的时间窗（按节次而非整门课，课间/课前分类才能按节次边界生效）。
 * startMs/endMs 为**缓冲后**（上课中窗口与通知事件时机，随上下课缓冲偏移）；
 * realStartMs/realEndMs 为节次表真实时间（课间类型/会话切分按真实时间判定）；
 * [period] 用于取节次表真实时间展示。
 */
internal data class DayWin(
    val startMs: Long, val endMs: Long,
    val realStartMs: Long, val realEndMs: Long,
    val course: Course, val period: Int
)

fun computeStatus(
    semester: Semester?,
    courses: List<Course>,
    specials: List<SpecialDate>,
    periods: List<Period>,
    zone: ZoneId,
    now: Instant,
    bufferPreSec: Int,
    bufferPostSec: Int,
    noticeLeadMin: Int = 20,
    rule: PeriodRule = PeriodRule(45, 10, 20),
    dndRules: List<CourseDndRule> = emptyList(),
    /** 周末是否也自动免打扰（见 ScheduleEngine.compute）；首页状态与 DND 事件保持一致。 */
    weekendDnd: Boolean = true
): Status {
    val today = now.atZone(zone).toLocalDate()
    val todayCourses = ScheduleEngine.activeDayCourses(semester, courses, specials, today)
    val r = ScheduleEngine.compute(semester, courses, specials, periods, zone, now, bufferPreSec, bufferPostSec, dndRules, weekendDnd)
    val nextText = r.next?.let { e ->
        val dt = Instant.ofEpochMilli(e.timeMillis).atZone(zone)
        if (e.start) "${dt.format(fmt)} 开启" else "${dt.format(fmt)} 恢复"
    }
    val nextClock = r.next?.let { Instant.ofEpochMilli(it.timeMillis).atZone(zone).format(clockFmt) }
    val nextStart = r.next?.start

    val nowMs = now.toEpochMilli()
    val dayStart = today.atStartOfDay(zone).toInstant()

    // 通知窗口：全天显示（第一节课前 lead 分钟起 → 最后一节课结束），课间内容区分大小课间。
    // 窗口边界按「上下课缓冲」偏移（与 DND 事件一致：上课/下课通知随提前/延后量一起发出）；
    // 展示用时间（nextStartClock 等）仍取节次表真实时间。
    val wins: List<DayWin> = todayCourses.flatMap { c ->
        ScheduleEngine.effectivePeriods(c, periods, dndRules, today.toEpochDay()).map { (p, per) ->
            DayWin(
                dayStart.plusSeconds(per.startMin * 60L - bufferPreSec).toEpochMilli(),
                dayStart.plusSeconds(per.endMin * 60L - bufferPostSec).toEpochMilli(),
                dayStart.plusSeconds(per.startMin * 60L).toEpochMilli(),
                dayStart.plusSeconds(per.endMin * 60L).toEpochMilli(),
                c, p
            )
        }
    }.sortedBy { it.startMs }
    val showNotice = wins.isNotEmpty() &&
        nowMs >= wins.first().startMs - noticeLeadMin * 60_000L &&
        nowMs <= wins.maxOf { it.endMs }

    // 当前上课中：按节次窗口判定（整门课跨两节时，中间的小课间不算上课中）
    val curWin = wins.firstOrNull { it.startMs <= nowMs && nowMs < it.endMs }
    val currentCourse = curWin?.course?.name
    // 单门课可单独指定静音方式（如仅静音的课不屏蔽媒体）：由当前课的窗口带回方式
    val currentCourseDnd = curWin?.course?.let { ScheduleEngine.customConfig(it) }

    // 下一节课与课间类型
    val nextW = wins.firstOrNull { it.startMs > nowMs }
    val prevEnd = wins.lastOrNull { it.endMs <= nowMs }?.endMs
    val prevEndReal = wins.lastOrNull { it.endMs <= nowMs }?.realEndMs
    // 会话首节课：按节次表真实间隔 > 大课间 切分（上午/下午/晚上），仅这些课发「上课前提醒」；
    // 课前窗口从「缓冲后的开始时刻 - lead」起（随提前/延后量）。
    val sessionFirsts = ScheduleEngine.sessionFirstStarts(wins.map { it.realStartMs to it.realEndMs }, rule.bigBreakMin)
    val leadMs = noticeLeadMin * 60_000L
    val phase = when {
        !showNotice -> NoticePhase.HIDDEN
        curWin != null -> NoticePhase.IN_CLASS
        nextW == null -> NoticePhase.HIDDEN
        nextW.realStartMs in sessionFirsts && nowMs >= nextW.startMs - leadMs -> NoticePhase.PRE
        prevEnd != null && prevEndReal != null && nextW.realStartMs - prevEndReal <= rule.breakMin * 60_000L -> NoticePhase.SMALL_BREAK
        else -> NoticePhase.BIG_BREAK
    }
    val nextStartClock = nextW?.let { PeriodTable.minuteToText(periods.getOrNull(it.period - 1)?.startMin ?: 0) }

    return Status(
        r.silentNow, nextText, nextClock, nextStart, todayCourses.size, currentCourse, showNotice,
        currentCourseId = curWin?.course?.id,
        currentCourseDnd = currentCourseDnd,
        nextCourseName = nextW?.course?.name,
        nextCourseRoom = nextW?.course?.location,
        nextStartClock = nextStartClock,
        inBreak = phase == NoticePhase.SMALL_BREAK || phase == NoticePhase.BIG_BREAK,
        smallBreak = phase == NoticePhase.SMALL_BREAK,
        phase = phase
    )
}

/** 把课程按节次换算成时间文本，如 "08:00–09:40"。 */
fun courseTimeText(c: Course, periods: List<Period>): String {
    val s = periods.getOrNull(c.startPeriod - 1)?.startMin ?: 0
    val e = periods.getOrNull(c.endPeriod - 1)?.endMin ?: 0
    return "${PeriodTable.minuteToText(s)}–${PeriodTable.minuteToText(e)}"
}
