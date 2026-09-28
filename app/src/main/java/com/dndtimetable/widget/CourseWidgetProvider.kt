package com.dndtimetable.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.dndtimetable.MainActivity
import com.dndtimetable.R
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.prefs.PeriodTable
import com.dndtimetable.di.AppGraph
import com.dndtimetable.domain.ScheduleEngine
import com.dndtimetable.domain.Status
import com.dndtimetable.scheduling.ScheduleHelper
import com.dndtimetable.scheduling.StatusSource
import com.dndtimetable.system.DevClock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 单一可缩放小组件，按「格子数」（min 宽/高，约 70dp/格）选择布局：
 * - 1×1      widget_small：状态图标 + 下次时间
 * - n×1      widget_row   ：横条（当前/下一节大字）
 * - 1×n      文字描述条目：头部 + 课程名（可换行）/地址（仅字母数字）/时间段 多条
 * - 2×n      窄式条目：色条 + 课程名/教室 老师/时间段（图2 样式）
 * - n×m(n≥3) 宽式条目：色条 + 课程名/教室 老师，右侧两行时间（图3 样式）
 * 内容按「今天 + 明天」双区展示：今天的课没上完 → 上今天下明天；今天全上完 → 上明天下今天
 * （与 App 首页「今天/明天课程」一致）；今天无课则只显示最近有课日。
 */
class CourseWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // 兜底重排（P1-10 看门狗）：小组件按 updatePeriodMillis 每 30 分钟唤起一次，
        // 趁机恢复被「强制停止/异常」清掉的闹钟——桌上有小组件即自愈，无需手动打开 App。
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { ScheduleHelper.scheduleNext(context.applicationContext) }
        }
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onEnabled(context: Context) {
        updateAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        updateWidget(context, manager, appWidgetId)
    }

    companion object {
        private const val CELL_DP = 70     // 每格大约 70dp（含边距）
        private const val CELL_OFFSET = 30 // 系统公式 minSize = 70*格数 - 30

        private const val STATE_PAST = 0
        private const val STATE_CURRENT = 1
        private const val STATE_UPCOMING = 2

        private data class Timeline(
            val startMin: Int, val endMin: Int, val name: String,
            val room: String?, val teacher: String?,
            val dayOffset: Int = 0,            // 相对今天的天数：0=今天、1=明日、2=后天、≥3=具体日期
            val isPast: Boolean = false        // 今天已上完的课 → 时间前显示「已上」（与日期标签同款样式）
        )

        private data class WidgetData(
            val status: Status,
            val semester: Semester?,
            val timeline: List<Timeline>,   // 已排序：今天区 + 第二区（今天未上完：上今天下第二；全上完：上第二下今天）
            val colorMap: Map<String, Int>, // 课程名 → 色值（与 App 课表同算法同色）
            val nowMs: Long,
            val today: LocalDate,           // 头部日期（今天）
            val nextDayDate: LocalDate?     // 第二区日期（≥3 天时标签显示 M.d）
        )

        /** dp → 格子数（≥1）。系统报告 minSize 约 70*格数-30。 */
        private fun cellCount(dp: Int): Int = ((dp + CELL_OFFSET) / CELL_DP).coerceAtLeast(1)

        /**
         * 列表条目（课程）数：按高度格数加成（条目行高已压缩，尽量填满不留大片空白）。
         * - 1×n 文字条目（无时间行、课程名可两行）：1×2→2、1×3→4、1×4→6、1×5→8（优先保证条目数）；
         * - 2 格高：2×2 显示 2 节；宽 ≥3 格（3×2/4×2/5×2）显示 3 节；
         * - 3 格高：4 节（2×3）；宽 ≥3 格 5 节（3×3+）；
         * - 4 格高：5 节（2×4）；宽 ≥3 格 6 节（3×4+）；
         * - ≥5 格高：7 节（2×5）；宽 ≥3 格 8 节（3×5+）。
         */
        private fun listEntries(cw: Int, ch: Int): Int = when {
            cw == 1 -> when {
                ch <= 2 -> 2
                ch == 3 -> 4
                ch == 4 -> 6
                else -> 8
            }
            ch <= 2 -> if (cw >= 3) 3 else 2
            ch == 3 -> if (cw >= 3) 5 else 4
            ch == 4 -> if (cw >= 3) 6 else 5
            else -> if (cw >= 3) 8 else 7
        }

        /** 条目样式：1 格宽 = 窄文字（无列表）；2 格宽 = 窄式（色条+三行）；≥3 格宽 = 宽式（右侧时间）。 */
        private fun rowLayout(cw: Int): Int = when {
            cw == 1 -> R.layout.widget_narrow_text_row
            cw == 2 -> R.layout.widget_course_row
            else -> R.layout.widget_course_row_wide
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CourseWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val opts = manager.getAppWidgetOptions(id)
                val cw = cellCount(opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110))
                val ch = cellCount(opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110))
                val layoutId = when {
                    cw == 1 && ch == 1 -> R.layout.widget_small
                    ch == 1 -> R.layout.widget_row             // n×1 横条
                    else -> R.layout.widget_large              // 1×n / 2×n / n×m：头部 + 课程列表
                }

                val data = runCatching { loadData(context) }.getOrNull() ?: return@launch
                val views = RemoteViews(context.packageName, layoutId)

                when (layoutId) {
                    R.layout.widget_small -> fillSmall(views, data)
                    R.layout.widget_row -> fillRow(views, data)
                    else -> {
                        fillHeader(views, data)
                        fillRows(context, views, data, entries = listEntries(cw, ch), rowLayout = rowLayout(cw))
                    }
                }

                val open = PendingIntent.getActivity(
                    context, 1, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, open)
                runCatching { manager.updateAppWidget(id, views) }
            }
        }

        private suspend fun loadData(context: Context): WidgetData {
            val status = StatusSource.load(context)
            val sid = AppGraph.settings.getActiveScheduleId()
            val semester = AppGraph.db.semesterDao().byIdOnce(sid)
            val courses = AppGraph.db.courseDao().allCoursesOnce(sid)
            val specials = AppGraph.db.specialDateDao().allOnce(sid)
            val periods = AppGraph.settings.getActivePeriods()
            val zone = ZoneId.systemDefault()
            val now = DevClock.now()
            val nowMs = now.toEpochMilli()
            val today = now.atZone(zone).toLocalDate()

            val todayTl = buildTimeline(semester, courses, specials, periods, zone, today)
            val todayDayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val hasUnfinishedToday = todayTl.any { stateOf(it, todayDayStart, nowMs) != STATE_PAST }

            // 第二区：明天有课用明天；否则向后找最近有课日（最多 7 天；标签按真实天差显示
            // 「明日 / 后天 / M.d」——此前的 bug 是周五取到周日却仍标「明日」）
            val nextDay = (1..7L).map { today.plusDays(it) }
                .firstOrNull { buildTimeline(semester, courses, specials, periods, zone, it).isNotEmpty() }
            val nextTl = nextDay?.let { buildTimeline(semester, courses, specials, periods, zone, it) } ?: emptyList()
            val nextDayOffset = nextDay?.let { it.toEpochDay() - today.toEpochDay() }?.toInt() ?: 0

            val todaySeq = if (hasUnfinishedToday) orderToday(todayTl, todayDayStart, nowMs) else todayTl
            // 归属显式标记：今天区 dayOffset=0（已上完 → isPast=true 显示「已上」）、
            // 第二区 dayOffset=1/2/≥3（显示「明日/后天/M.d」）；不做相等性推断
            val todayMarked = todaySeq.map {
                it.copy(dayOffset = 0, isPast = stateOf(it, todayDayStart, nowMs) == STATE_PAST)
            }
            val nextMarked = nextTl.map { it.copy(dayOffset = nextDayOffset, isPast = false) }
            val seq = if (hasUnfinishedToday) todayMarked + nextMarked else nextMarked + todayMarked
            // 与 App 课表同算法配色：全量启用课程 → 课程名 → 色值
            val colorMap = com.dndtimetable.domain.CourseColors.assign(courses.filter { it.enabled })
            return WidgetData(status, semester, seq, colorMap, nowMs, today, nextDay)
        }

        /** 今天的展示序列：未上课（按时间序）→ 当前课 → 已上课（按时间序）。例：1,2,3 —— 开课前 1,2,3；第 1 节中 2,3,1。 */
        private fun orderToday(today: List<Timeline>, dayStart: Long, nowMs: Long): List<Timeline> =
            today.filter { stateOf(it, dayStart, nowMs) == STATE_UPCOMING } +
                today.filter { stateOf(it, dayStart, nowMs) == STATE_CURRENT } +
                today.filter { stateOf(it, dayStart, nowMs) == STATE_PAST }

        private fun buildTimeline(
            semester: Semester?,
            courses: List<com.dndtimetable.data.db.Course>,
            specials: List<com.dndtimetable.data.db.SpecialDate>,
            periods: List<com.dndtimetable.data.prefs.Period>,
            zone: ZoneId, date: LocalDate
        ): List<Timeline> = ScheduleEngine.activeDayCourses(semester, courses, specials, date)
            .sortedBy { it.startPeriod }
            .map { c ->
                Timeline(
                    startMin = periods.getOrNull(c.startPeriod - 1)?.startMin ?: 0,
                    endMin = periods.getOrNull(c.endPeriod - 1)?.endMin ?: 0,
                    name = c.name,
                    room = c.location,
                    teacher = c.teacher
                )
            }

        private fun stateOf(it: Timeline, dayStart: Long, nowMs: Long): Int {
            val s = dayStart + it.startMin * 60_000L
            val e = dayStart + it.endMin * 60_000L
            return when {
                nowMs < s -> STATE_UPCOMING
                nowMs < e -> STATE_CURRENT
                else -> STATE_PAST
            }
        }

        private fun titleText(data: WidgetData): String =
            if (data.status.silentNow) "静音中" else data.semester?.name ?: "免打扰课表"

        private fun dateText(data: WidgetData): String {
            val cn = arrayOf("一", "二", "三", "四", "五", "六", "日")[data.today.dayOfWeek.value - 1]
            val week = data.semester?.let { ScheduleEngine.weekNumber(it, data.today) } ?: 0
            val md = "${data.today.monthValue}.${data.today.dayOfMonth}"
            return if (week > 0) "$md 第${week}周 周$cn" else "$md 周$cn"
        }

        /** 课程色条颜色：与 App 课表同算法（CourseColors.assign），同名同色。 */
        private fun courseColor(data: WidgetData, name: String): Int =
            data.colorMap[name] ?: com.dndtimetable.domain.CourseColors.palette[Math.floorMod(name.hashCode(), com.dndtimetable.domain.CourseColors.palette.size)]

        /** 地址只显示字母数字部分（如 "s50609 xx实验室" → "s50609"）；无则返回 null。 */
        private fun shortRoom(room: String?): String? {
            if (room == null) return null
            return room.split(" ", "　", "、")
                .firstOrNull { it.isNotEmpty() && it.all { ch -> ch.isLetterOrDigit() && ch.code < 128 } }
        }

        private fun fillSmall(views: RemoteViews, data: WidgetData) {
            views.setTextViewText(R.id.widget_status, if (data.status.silentNow) "🔕" else "🔔")
            // 空态：近期无课（10sp 保证 1×1 内单行放下；恢复时回 11sp）
            val empty = data.timeline.isEmpty()
            views.setTextViewText(R.id.widget_next_time, if (empty) "近期无课" else data.status.nextClock ?: "—")
            views.setTextViewTextSize(R.id.widget_next_time, TypedValue.COMPLEX_UNIT_SP, if (empty) 10f else 11f)
        }

        /** n×1 横条：头部（课表名/日期）+ 左课程名·教室 + 右时间两行；空态整块居中显示「近期无课」。 */
        private fun fillRow(views: RemoteViews, data: WidgetData) {
            views.setTextViewText(R.id.widget_row_title, titleText(data))
            views.setTextViewText(R.id.widget_row_date, dateText(data))
            val e = data.timeline.firstOrNull()
            // reapply 可能复用已渲染的视图树，两个分支都要显式回置 gravity
            views.setInt(R.id.widget_row_course, "setGravity", if (e == null) Gravity.CENTER else Gravity.TOP)
            if (e != null) {
                views.setTextViewText(R.id.widget_row_course, e.name)
                views.setTextViewText(R.id.widget_row_room, e.room ?: "")
                views.setTextViewText(R.id.widget_row_time_start, PeriodTable.minuteToText(e.startMin))
                views.setTextViewText(R.id.widget_row_time_end, PeriodTable.minuteToText(e.endMin))
            } else {
                views.setTextViewText(R.id.widget_row_course, "近期无课")
                views.setTextViewText(R.id.widget_row_room, "")
                views.setTextViewText(R.id.widget_row_time_start, "")
                views.setTextViewText(R.id.widget_row_time_end, "")
            }
        }

        /** 列表布局头部：课表名 + 日期（课程列表承担全部展示，不再用大字「下一节」区）。 */
        private fun fillHeader(views: RemoteViews, data: WidgetData) {
            views.setTextViewText(R.id.widget_title, titleText(data))
            views.setTextViewText(R.id.widget_date, dateText(data))
            views.setViewVisibility(R.id.widget_next_course, View.GONE)
            views.setViewVisibility(R.id.widget_next_room, View.GONE)
            views.setViewVisibility(R.id.widget_next_time, View.GONE)
        }

        /**
         * 课程列表：按尺寸取条目数（今天未上完：上今天下明天；全上完：上明天下今天），
         * 条目样式按宽度：1 格=窄文字（课程名/短地址/时间）、2 格=窄式色条、≥3 格=宽式右侧时间。
         */
        private fun fillRows(
            context: Context, views: RemoteViews, data: WidgetData,
            entries: Int, rowLayout: Int
        ) {
            views.removeAllViews(R.id.widget_courses)
            val items = data.timeline.take(entries.coerceAtLeast(1))
            if (items.isEmpty()) {
                // 空态：列表区显示居中「近期无课」（1×n / 2×n / n×m 通用）
                views.setViewVisibility(R.id.widget_courses, View.VISIBLE)
                views.addView(R.id.widget_courses, RemoteViews(views.getPackage(), R.layout.widget_empty))
                return
            }
            views.setViewVisibility(R.id.widget_courses, View.VISIBLE)
            val textStyle = rowLayout == R.layout.widget_narrow_text_row
            items.forEach { it ->
                val row = RemoteViews(views.getPackage(), rowLayout)
                if (textStyle) {
                    // 1×n 文字版：课程名（可两行）+ 地址（仅字母数字），无时间行（挤空间给更多条目）
                    row.setTextViewText(R.id.widget_narrow_name, it.name)
                    row.setTextViewText(R.id.widget_narrow_addr, shortRoom(it.room) ?: "")
                } else {
                    row.setInt(R.id.widget_bar, "setBackgroundColor", courseColor(data, it.name))
                    row.setTextViewText(R.id.widget_course_name, it.name)
                    val meta = listOfNotNull(it.room, it.teacher).joinToString(" ")
                    row.setTextViewText(R.id.widget_course_meta, meta)
                    if (rowLayout == R.layout.widget_course_row_wide) {
                        // 标签：第二区按真实天差（明日/后天/M.d，强调蓝）；今日已上完 → 「已上」（红）；其余无
                        val tag = courseTagLabel(it.dayOffset, it.isPast, data.nextDayDate)
                        row.setViewVisibility(R.id.widget_tomorrow_tag, if (tag != null) View.VISIBLE else View.GONE)
                        if (tag != null) {
                            row.setTextViewText(R.id.widget_tomorrow_tag, tag)
                            row.setTextColor(
                                R.id.widget_tomorrow_tag,
                                if (it.isPast) context.getColor(R.color.widget_done) else context.getColor(R.color.widget_accent)
                            )
                        }
                        row.setTextViewText(R.id.widget_course_time_start, PeriodTable.minuteToText(it.startMin))
                        row.setTextViewText(R.id.widget_course_time_end, PeriodTable.minuteToText(it.endMin))
                    } else {
                        val prefix = courseTagLabel(it.dayOffset, it.isPast, data.nextDayDate)?.let { "$it " } ?: ""
                        row.setTextViewText(
                            R.id.widget_course_time,
                            "$prefix${PeriodTable.minuteToText(it.startMin)} - ${PeriodTable.minuteToText(it.endMin)}"
                        )
                    }
                }
                views.addView(R.id.widget_courses, row)
            }
        }
    }
}
