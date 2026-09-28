package com.dndtimetable.data.prefs

import android.content.Context
import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.SpecialDateType
import com.dndtimetable.data.db.WeekType
import org.json.JSONArray
import org.json.JSONObject

/**
 * 首页数据快照：冷启动首帧直接用上一次的渲染数据填充，避免"杀掉后台再进"时的空白加载期。
 * DB（Room）加载完成后 homeStatus 重发射，界面无感刷新。
 *
 * v2 起同时快照活动课表的全部课程/学期/节次表：课表页（周网格）首帧也不再闪空网格或「还没有课程」卡片。
 */
object HomeSnapshot {

    data class Data(
        val today: List<Course>,
        val tomorrow: List<Course>,
        val nextText: String?,
        val currentCourse: String?,
        val hasSemester: Boolean,
        /** 活动课表 id 与全量课程/学期/节次表（课表页首帧用）。旧版本快照没有这些字段。 */
        val scheduleId: Long = 0L,
        val courses: List<Course> = emptyList(),
        val semester: Semester? = null,
        val periods: List<Period> = emptyList(),
        /** 特殊日期（放假日/调休）：课表页的「休/班」角标首帧不晚一帧。 */
        val specials: List<SpecialDate> = emptyList()
    )

    private fun courseJson(c: Course): JSONObject = JSONObject()
        .put("id", c.id)
        .put("name", c.name)
        .put("weekday", c.weekday)
        .put("startPeriod", c.startPeriod)
        .put("endPeriod", c.endPeriod)
        .put("startWeek", c.startWeek)
        .put("endWeek", c.endWeek)
        .put("weekType", c.weekType.name)
        .put("teacher", c.teacher ?: JSONObject.NULL)
        .put("location", c.location ?: JSONObject.NULL)
        .put("enabled", c.enabled)
        .put("scheduleId", c.scheduleId)

    private fun courseFromJson(j: JSONObject): Course = Course(
        id = j.optLong("id"),
        name = j.optString("name"),
        weekday = j.optInt("weekday"),
        startPeriod = j.optInt("startPeriod"),
        endPeriod = j.optInt("endPeriod"),
        startWeek = j.optInt("startWeek"),
        endWeek = j.optInt("endWeek"),
        weekType = runCatching { WeekType.valueOf(j.optString("weekType")) }.getOrDefault(WeekType.ALL),
        teacher = if (j.has("teacher") && !j.isNull("teacher")) j.optString("teacher") else null,
        location = if (j.has("location") && !j.isNull("location")) j.optString("location") else null,
        enabled = j.optBoolean("enabled"),
        scheduleId = j.optLong("scheduleId")
    )

    private fun semesterJson(s: Semester): JSONObject = JSONObject()
        .put("id", s.id)
        .put("name", s.name)
        .put("startEpochDay", s.startEpochDay)
        .put("totalWeeks", s.totalWeeks)

    private fun semesterFromJson(j: JSONObject): Semester = Semester(
        id = j.optLong("id"),
        name = j.optString("name", "我的课表"),
        startEpochDay = j.optLong("startEpochDay"),
        totalWeeks = j.optInt("totalWeeks")
    )

    private fun specialJson(s: SpecialDate): JSONObject = JSONObject()
        .put("id", s.id)
        .put("epochDay", s.epochDay)
        .put("endEpochDay", s.endEpochDay ?: JSONObject.NULL)
        .put("type", s.type.name)
        .put("courseId", s.courseId ?: JSONObject.NULL)
        .put("sourceEpochDay", s.sourceEpochDay ?: JSONObject.NULL)
        .put("builtin", s.builtin)
        .put("name", s.name ?: JSONObject.NULL)
        .put("enabled", s.enabled)
        .put("edited", s.edited)
        .put("scheduleId", s.scheduleId)

    private fun specialFromJson(j: JSONObject): SpecialDate = SpecialDate(
        id = j.optLong("id"),
        epochDay = j.optLong("epochDay"),
        endEpochDay = if (j.has("endEpochDay") && !j.isNull("endEpochDay")) j.optLong("endEpochDay") else null,
        type = runCatching { SpecialDateType.valueOf(j.optString("type")) }.getOrDefault(SpecialDateType.HOLIDAY),
        courseId = if (j.has("courseId") && !j.isNull("courseId")) j.optLong("courseId") else null,
        sourceEpochDay = if (j.has("sourceEpochDay") && !j.isNull("sourceEpochDay")) j.optLong("sourceEpochDay") else null,
        builtin = j.optBoolean("builtin"),
        name = if (j.has("name") && !j.isNull("name")) j.optString("name") else null,
        enabled = j.optBoolean("enabled", true),
        edited = j.optBoolean("edited"),
        scheduleId = j.optLong("scheduleId")
    )

    fun save(context: Context, d: Data) {
        fun arr(list: List<Course>) = JSONArray().apply { list.forEach { put(courseJson(it)) } }
        val json = JSONObject()
            .put("today", arr(d.today))
            .put("tomorrow", arr(d.tomorrow))
            .put("nextText", d.nextText)
            .put("currentCourse", d.currentCourse)
            .put("hasSemester", d.hasSemester)
            .put("scheduleId", d.scheduleId)
            .put("courses", arr(d.courses))
            .put("semester", d.semester?.let { semesterJson(it) } ?: JSONObject.NULL)
            .put("periods", PeriodTable.encode(d.periods))
            .put("specials", JSONArray().apply { d.specials.forEach { put(specialJson(it)) } })
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }

    fun load(context: Context): Data? = runCatching {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        val j = JSONObject(raw)
        fun arr(key: String): List<Course> {
            val a = j.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { courseFromJson(a.getJSONObject(it)) }
        }
        Data(
            today = arr("today"),
            tomorrow = arr("tomorrow"),
            nextText = j.optString("nextText").ifEmpty { null },
            currentCourse = j.optString("currentCourse").ifEmpty { null },
            hasSemester = j.optBoolean("hasSemester"),
            scheduleId = j.optLong("scheduleId"),
            courses = arr("courses"),
            semester = j.optJSONObject("semester")?.let { semesterFromJson(it) },
            periods = j.optString("periods", "").takeIf { it.isNotBlank() }?.let { PeriodTable.decode(it) } ?: emptyList(),
            specials = j.optJSONArray("specials")?.let { a ->
                (0 until a.length()).map { specialFromJson(a.getJSONObject(it)) }
            } ?: emptyList()
        )
    }.getOrNull()

    private const val PREF = "home_snapshot"
    private const val KEY = "snap"
}
