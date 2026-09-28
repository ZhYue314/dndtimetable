package com.dndtimetable.scheduling

import android.content.Context
import com.dndtimetable.data.prefs.DndConfig
import com.dndtimetable.di.AppGraph
import com.dndtimetable.system.DevClock
import com.dndtimetable.domain.Status
import com.dndtimetable.domain.computeStatus
import java.time.ZoneId
import kotlinx.coroutines.flow.first

object StatusSource {
    suspend fun load(context: Context): Status {
        val sid = AppGraph.settings.getActiveScheduleId()
        val semester = AppGraph.db.semesterDao().byIdOnce(sid)
        val courses = AppGraph.db.courseDao().allCoursesOnce(sid)
        val specials = AppGraph.db.specialDateDao().allOnce(sid)
        val rules = AppGraph.db.courseDndRuleDao().allOnce(sid)
        val periods = AppGraph.settings.getActivePeriods()
        val rule = AppGraph.settings.periodRule.first()
        val pre = AppGraph.settings.bufferPre.first()
        val post = AppGraph.settings.bufferPost.first()
        val lead = AppGraph.settings.noticeLeadMin.first()
        return computeStatus(
            semester, courses, specials, periods, ZoneId.systemDefault(),
            DevClock.now(), pre, post, lead, rule, rules, AppGraph.settings.weekendDnd.first()
        )
    }

    /**
     * 上课时实际使用的静音方式：若当前正在上的课单独指定了方式（[Status.currentCourseDnd]）则用它，
     * 否则用全局设置。DND_START 闹钟在**上课时刻**触发，此时该课的静音窗口恰好打开，故能正确取到。
     */
    suspend fun effectiveDndConfig(context: Context): DndConfig {
        val global = AppGraph.settings.dndConfig.first()
        return runCatching { load(context).currentCourseDnd }.getOrNull() ?: global
    }
}
