package com.dndtimetable.scheduling

import com.dndtimetable.system.AppLog

import android.content.Context
import com.dndtimetable.di.AppGraph
import com.dndtimetable.domain.LegalHolidays
import com.dndtimetable.domain.ScheduleEngine
import com.dndtimetable.notify.StatusNotification
import com.dndtimetable.system.DevClock
import com.dndtimetable.system.DndStateStore
import com.dndtimetable.system.MediaKeepalive
import com.dndtimetable.system.SilentExecutor
import com.dndtimetable.system.VolumeWatcher
import com.dndtimetable.widget.CourseWidgetProvider
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/**
 * 计算下一个触发点、同步当前免打扰状态，并排好精确闹钟。
 * 在 App 打开 / 改课表 / 开机 / 每次触发后调用。
 */
/**
 * 窗口外的残留会话是否应收尾（纯逻辑，可 JVM 单测）：
 * - 无有效会话 → 否；
 * - 会话快照属于别的课表（切表后旧会话未收尾）→ 是；
 * - 未来无任何事件（DND_END 闹钟丢失的孤儿会话，原逻辑）→ 是；
 * - 同课表且仍有后续事件（如用户手动开启的会话）→ 否。
 */
internal fun shouldEndOrphanSession(
    sessionActive: Boolean,
    sessionScheduleId: Long?,
    activeScheduleId: Long,
    nextEventExists: Boolean
): Boolean = sessionActive &&
    ((sessionScheduleId != null && sessionScheduleId != activeScheduleId) || !nextEventExists)

object ScheduleHelper {
    private const val NOTICE_KEY_MASK = 31   // 与 AlarmScheduler.NOTICE_KEY_MAX 对应

    /**
     * 通知事件 key：进程内单调递增（0..31 循环）。
     * scheduleNext 每次先 cancel 全部再重排，同一轮内 key 必然互不相同（旧实现用
     * timeMillis.hashCode() and 31，两个不同时刻哈希低位相同会被 FLAG_UPDATE_CURRENT 顶掉）；
     * 进程重启后从 0 重计，因重排前必先全量 cancel，也不会与旧闹钟冲突。
     */
    private val noticeKeyCounter = java.util.concurrent.atomic.AtomicInteger(0)

    internal fun nextNoticeKey(): Int = noticeKeyCounter.getAndIncrement() and NOTICE_KEY_MASK

    /** 确保存在默认课表；活动 id 失效时自动指向第一份课表。返回有效的活动课表 id。 */
    suspend fun ensureDefaultSchedule(): Long {
        val dao = AppGraph.db.semesterDao()
        val active = AppGraph.settings.getActiveScheduleId()
        if (dao.byIdOnce(active) != null) return active
        val list = dao.allOnce()
        return if (list.isNotEmpty()) {
            AppGraph.settings.setActiveScheduleId(list.first().id)
            list.first().id
        } else {
            val id = dao.insert(
                com.dndtimetable.data.db.Semester(
                    name = "我的课表",
                    startEpochDay = DevClock.today().toEpochDay(),
                    totalWeeks = 20
                )
            )
            AppGraph.settings.setActiveScheduleId(id)
            id
        }
    }

    /**
     * 一次性修复历史课表的开学日期：把不是周一的锚点移到所在周的周一。
     * 「第 N 周」必须是整周（周一..周日），否则周次与网格的星期列错位——会出现
     * 「9/22 是周二，却被排在高亮的周一列」这类日期错乱。前置条件：该课表还没有任何课程。
     * 有课程时不擅自挪动日期（只影响显示，周次仍按周一锚点计算，见 ScheduleEngine.weekNumber）。
     */
    suspend fun normalizeScheduleAnchors() {
        val dao = AppGraph.db.semesterDao()
        for (s in dao.allOnce()) {
            val mon = ScheduleEngine.anchorStart(java.time.LocalDate.ofEpochDay(s.startEpochDay))
            if (mon.toEpochDay() == s.startEpochDay) continue
            if (AppGraph.db.courseDao().allCoursesOnce(s.id).isNotEmpty()) continue
            AppLog.w("dndtimetable", "开学日期归一：课表 ${s.id} ${java.time.LocalDate.ofEpochDay(s.startEpochDay)} → $mon（周一）")
            dao.update(s.copy(startEpochDay = mon.toEpochDay()))
        }
    }

    suspend fun scheduleNext(context: Context) {
        // 总开关关闭：撤掉一切调度与通知
        if (!AppGraph.settings.schedulingEnabled.first()) {
            AlarmScheduler.cancel(context)
            StatusNotification.cancel(context)
            return
        }
        // 每次唤醒（事件/开机/改时/打开 App/小组件刷新）顺带自愈：
        // ① 孤儿归零（音量卡 0）；② 残留的自定义 DND 策略（end() 未跑完时静默污染系统设置）
        SilentExecutor.healOrphanZeroedVolume(context)
        SilentExecutor.healOrphanDndPolicy(context)
        val activeId = ensureDefaultSchedule()
        val semester = AppGraph.db.semesterDao().byIdOnce(activeId)
        val courses = AppGraph.db.courseDao().allCoursesOnce(activeId)
        seedBuiltinHolidays(semester)
        val specials = AppGraph.db.specialDateDao().allOnce(activeId)
        val dndRules = AppGraph.db.courseDndRuleDao().allOnce(activeId)
        val pre = AppGraph.settings.bufferPre.first()
        val post = AppGraph.settings.bufferPost.first()
        val periods = AppGraph.settings.getActivePeriods()
        val weekendDnd = AppGraph.settings.weekendDnd.first()

        val now = DevClock.now()
        val zone = ZoneId.systemDefault()
        val r = ScheduleEngine.compute(semester, courses, specials, periods, zone, now, pre, post, dndRules, weekendDnd)

        // 若当前正处于免打扰时间窗内，按静音方式保证状态存在
        if (r.silentNow) {
            SilentExecutor.start(context)
        } else {
            // 孤儿会话自愈：窗口已过/切到无课课表，但快照仍在（DND_END 闹钟丢失、切换课表后旧会话未收尾）
            // → 结束会话还原状态。否则残留快照会让免打扰与常驻通知一直挂着（切回无课课表后 DND 关不掉）。
            val store = DndStateStore(context)
            val sessionActive = store.loadFilter() != null && !store.isSessionStale()
            if (shouldEndOrphanSession(sessionActive, store.loadSessionScheduleId(), activeId, r.next != null)) {
                AppLog.w("dndtimetable", "scheduleNext: 窗口外残留会话（切表/无后续事件），自动结束")
                SilentExecutor.end(context)
            } else if (sessionActive) {
                // 会话仍活着（含手动开的）但不在窗口内：HyperOS 上滑清理=强停会取消保活通知并清闹钟，
                // 强停后只有手动打开 App 才能走到这里——把保活服务与音量监听拉回来（通知/再屏蔽随之恢复）。
                AppLog.w("dndtimetable", "scheduleNext: 会话存活，重拉保活服务与音量监听")
                MediaKeepalive.maybeStart(context)
                if (store.loadAppliedMedia()) VolumeWatcher.start(context)
            }
        }

        // 重排：免打扰下一个事件 + 今日剩余提醒/内容事件（alarm 直接触发，不依赖 app 打开）
        AlarmScheduler.cancel(context)
        r.next?.let {
            AlarmScheduler.schedule(context, it.timeMillis, if (it.start) AlarmKind.DND_START else AlarmKind.DND_END)
        }
        val lead = AppGraph.settings.noticeLeadMin.first()
        val rule = AppGraph.settings.periodRule.first()
        ScheduleEngine.nextNoticeEvents(semester, courses, specials, periods, rule, zone, now, lead, pre, post, dndRules).forEach { e ->
            AlarmScheduler.schedule(
                context, e.timeMillis,
                when (e.kind) {
                    ScheduleEngine.NoticeKind.PRE -> AlarmKind.NOTICE_PRE
                    ScheduleEngine.NoticeKind.CONTENT -> AlarmKind.NOTICE_CONTENT
                    ScheduleEngine.NoticeKind.HIDE -> AlarmKind.NOTICE_HIDE
                },
                key = nextNoticeKey()
            )
        }
        // 媒体屏蔽 = 事件驱动 + 前台服务保活（MediaKeepaliveService 由 start() 拉起、end() 停止）：
        // 进程被服务保住 → 拔/插耳机、DND 变化等回调常驻即时响应；被划后台时由服务 onTaskRemoved
        // 立即自愈 + 1 秒一次性闹钟拉回，不做周期闹钟（避免课中反复唤醒耗电）。

        // 调休补课日头天 20:00 提醒（最近一条、且那天真有课才排；触发后重排自然轮到下一条）
        ScheduleEngine.nextMakeupNotice(semester, courses, specials, zone, now)?.let { n ->
            AlarmScheduler.schedule(
                context, n.remindMs, AlarmKind.NOTICE_SPECIAL,
                extras = mapOf("title" to "调休提醒", "text" to ScheduleEngine.makeupNoticeText(n))
            )
        }

        // 同步刷新常驻通知与小组件
        StatusNotification.update(context)
        CourseWidgetProvider.updateAll(context)
    }

    /**
     * 当前静音窗口的结束时间（毫秒）；不在窗口内返回 null。
     * 供"用户手动关闭本节自动静音"的抑制期使用（抑制到本节窗口结束，下一节恢复自动）。
     */
    suspend fun currentWindowEndMs(context: Context): Long? = runCatching {
        val activeId = ensureDefaultSchedule()
        val semester = AppGraph.db.semesterDao().byIdOnce(activeId)
        val courses = AppGraph.db.courseDao().allCoursesOnce(activeId)
        val specials = AppGraph.db.specialDateDao().allOnce(activeId)
        val dndRules = AppGraph.db.courseDndRuleDao().allOnce(activeId)
        val periods = AppGraph.settings.getActivePeriods()
        val pre = AppGraph.settings.bufferPre.first()
        val post = AppGraph.settings.bufferPost.first()
        ScheduleEngine.compute(
            semester, courses, specials, periods, ZoneId.systemDefault(),
            DevClock.now(), pre, post, dndRules, AppGraph.settings.weekendDnd.first()
        ).currentWindowEndMs
    }.getOrNull()

    /**
     * 播种进特殊日期表（幂等：已存在同名内置项则跳过，用户改过的不会被覆盖）：
     * - **放假区间**：内置/联网官方表，显示在「内置节假日」区；
     * - **调休补课日**：联网表（只给哪天补班），显示在「自添加日期」区，
     *   `source`（被补日）留空，头天提醒用户设置结束日期，设好后课表同步。
     * 播种窗口 = **今天** 到学期开始年次年 1/7（已过去的节日/调休不播种，
     * 且库里已过期的内置行在此自动删除；用户自添加的行不动）。
     * 调用时机：启动 / 导入课表 / 保存学期 / 拉取到新节假日表。
     */
    suspend fun seedBuiltinHolidays(semester: com.dndtimetable.data.db.Semester?) {
        if (semester == null) return
        val sid = semester.id
        val start = java.time.LocalDate.ofEpochDay(semester.startEpochDay)
        val dao = AppGraph.db.specialDateDao()
        val all = dao.allOnce(sid)
        // 已过去的内置行（放假/调休）自动删除——只动机器行，用户自添加的保留
        val today = java.time.LocalDate.now()
        val past = all.filter { it.builtin && (it.endEpochDay ?: it.epochDay) < today.toEpochDay() }
        past.forEach { dao.delete(it) }
        val live = all.filterNot { past.contains(it) }
        // 播种窗口：今天（过去的不再播种）到明年 1/7（兜底覆盖元旦假期顺延上限）
        val seedStart = maxOf(today, java.time.LocalDate.of(start.year, 1, 1))
        val seedEnd = java.time.LocalDate.of(start.year + 1, 1, 7)
        val (toInsert, toUpdate) = planBuiltinSeed(
            existing = live.filter { it.builtin },
            holidays = LegalHolidays.forYears(seedStart.year, seedEnd.year),
            makeups = LegalHolidays.makeupsForYears(seedStart.year, seedEnd.year),
            semesterStart = seedStart.toEpochDay(),
            semesterEnd = seedEnd.toEpochDay(),
            scheduleId = sid
        )
        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
        toUpdate.forEach { dao.update(it) }
    }
}

/**
 * 内置节假日/调休播种计划（纯逻辑，可 JVM 单测）。
 *
 * 同名可能多条（两条「国庆调休上课」、跨年的两个「元旦」）：必须用**池化消耗**匹配——
 * 旧实现 `associateBy { name }` 同名只留最后一条，二次播种会把 10/10 调休行改成 9/20。
 * 匹配优先级：① 日期完全一致（占用即可）② 同名且未被用户改过（同步官方新日期）
 * ③ 同年份/同类型未编辑的机器行（兜底上游改名，如 清明 → 清明节、国庆调休上课 → 国庆节调休上课）④ 对不上则新增。
 * 调休按「来源日」认领同名未编辑行，避免 9/20 那条被 10/10 的官方条目抢走改写。
 */
internal fun planBuiltinSeed(
    existing: List<com.dndtimetable.data.db.SpecialDate>,
    holidays: List<LegalHolidays.Range>,
    makeups: List<LegalHolidays.Makeup>,
    semesterStart: Long,
    semesterEnd: Long,
    scheduleId: Long
): Pair<List<com.dndtimetable.data.db.SpecialDate>, List<com.dndtimetable.data.db.SpecialDate>> {
    val pool = existing.toMutableList()
    // name == null → 不按名字匹配（改名兜底用）
    fun take(type: com.dndtimetable.data.db.SpecialDateType, name: String?, pred: (com.dndtimetable.data.db.SpecialDate) -> Boolean): com.dndtimetable.data.db.SpecialDate? {
        val i = pool.indexOfFirst { it.type == type && (name == null || it.name == name) && pred(it) }
        return if (i >= 0) pool.removeAt(i) else null
    }
    val toInsert = ArrayList<com.dndtimetable.data.db.SpecialDate>()
    val toUpdate = ArrayList<com.dndtimetable.data.db.SpecialDate>()

    for (r in holidays) {
        if (r.end.toEpochDay() < semesterStart || r.start.toEpochDay() > semesterEnd) continue
        val s = r.start.toEpochDay(); val e = r.end.toEpochDay()
        if (take(com.dndtimetable.data.db.SpecialDateType.HOLIDAY, r.name) {
                it.epochDay == s && (it.endEpochDay ?: it.epochDay) == e
            } != null) continue
        // 同年同名未编辑：官方改了放假天数（如国庆 3 天 → 7 天）则同步
        var cur = take(com.dndtimetable.data.db.SpecialDateType.HOLIDAY, r.name) {
            !it.edited && java.time.LocalDate.ofEpochDay(it.epochDay).year == r.start.year
        }
        // 上游可能改名（清明 → 清明节）：同年份未编辑的机器行兜底认领
        if (cur == null) cur = take(com.dndtimetable.data.db.SpecialDateType.HOLIDAY, null) {
            !it.edited && java.time.LocalDate.ofEpochDay(it.epochDay).year == r.start.year
        }
        when {
            cur == null -> toInsert.add(
                com.dndtimetable.data.db.SpecialDate(
                    epochDay = s, endEpochDay = e,
                    type = com.dndtimetable.data.db.SpecialDateType.HOLIDAY,
                    builtin = true, name = r.name, scheduleId = scheduleId
                )
            )
            cur.epochDay != s || (cur.endEpochDay ?: cur.epochDay) != e || cur.name != r.name ->
                toUpdate.add(cur.copy(epochDay = s, endEpochDay = e, name = r.name))
        }
    }

    for (m in makeups) {
        if (m.date.toEpochDay() < semesterStart || m.date.toEpochDay() > semesterEnd) continue
        val d = m.date.toEpochDay(); val src = m.source?.toEpochDay()
        // ① 同名同日占用即可；未编辑的把来源日同步成官方值（联网表无来源 → 置空，重新走「设置结束日期」提醒）
        val sameDay = take(com.dndtimetable.data.db.SpecialDateType.MAKEUP, m.name) { it.epochDay == d }
        if (sameDay != null) {
            if (!sameDay.edited && sameDay.sourceEpochDay != src) toUpdate.add(sameDay.copy(sourceEpochDay = src))
            continue
        }
        // ② 同名未编辑行：官方改了调休日则同步；也兜底修历史同名撞车写坏的重复行
        var cur = take(com.dndtimetable.data.db.SpecialDateType.MAKEUP, m.name) { !it.edited }
        // ③ 上游可能改名（国庆调休上课 → 国庆节调休上课）：任意未编辑机器行兜底认领
        if (cur == null) cur = take(com.dndtimetable.data.db.SpecialDateType.MAKEUP, null) { !it.edited }
        when {
            cur == null -> toInsert.add(
                com.dndtimetable.data.db.SpecialDate(
                    epochDay = d, endEpochDay = d, sourceEpochDay = src,
                    type = com.dndtimetable.data.db.SpecialDateType.MAKEUP,
                    builtin = true, name = m.name, scheduleId = scheduleId
                )
            )
            cur.epochDay != d || cur.sourceEpochDay != src || cur.name != m.name ->
                toUpdate.add(cur.copy(epochDay = d, endEpochDay = d, sourceEpochDay = src, name = m.name))
        }
    }
    return toInsert to toUpdate
}
