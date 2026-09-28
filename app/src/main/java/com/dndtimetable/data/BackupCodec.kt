package com.dndtimetable.data

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.CourseDndRule
import com.dndtimetable.data.db.DndPolicy
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.SpecialDateType
import com.dndtimetable.data.db.WeekType
import com.dndtimetable.data.prefs.DndConfig
import com.dndtimetable.data.prefs.Period
import com.dndtimetable.data.prefs.PeriodRule
import com.dndtimetable.data.prefs.PeriodTable
import org.json.JSONArray
import org.json.JSONObject

/**
 * 全量备份文件（JSON，v1）：换机/重装迁移用，纯编解码无 IO（文件读写见 MainViewModel）。
 * 内容 = 全部课表（学期）+ 课程 + 特殊日期 + 单课免打扰规则 + 节次表/规则 + 全局免打扰方式；
 * 权限/通知等设备级开关不备份（新机重新引导更稳）。
 * ponytail: 只覆盖核心数据；要连通知文案一起搬时再往 Snapshot 里加字段。
 */
object BackupCodec {
    /** 备份文件的 `app` 标记；项目改名前的旧备份写的是 [LEGACY_APP_TAG]，读取时两者都认。 */
    const val APP_TAG = "dndtimetable"
    const val LEGACY_APP_TAG = "autoDND"
    const val VERSION = 1

    data class Snapshot(
        val activeScheduleId: Long,
        val schedules: List<Semester>,
        val courses: List<Course>,
        val specials: List<SpecialDate>,
        val rules: List<CourseDndRule>,
        val periods: List<Period>,
        val periodRule: PeriodRule,
        val dndConfig: DndConfig,
        /** 每张课表的独立节次表覆盖值（没覆盖的课表回退 [periods]）；旧版备份没有该字段时为 emptyMap。 */
        val schedulePeriods: Map<Long, List<Period>> = emptyMap()
    )

    fun encode(s: Snapshot): String {
        val root = JSONObject()
        root.put("app", APP_TAG)
        root.put("version", VERSION)
        root.put("activeScheduleId", s.activeScheduleId)
        root.put("periods", PeriodTable.encode(s.periods))
        root.put("schedulePeriods", JSONObject().apply {
            s.schedulePeriods.forEach { (id, list) -> put(id.toString(), PeriodTable.encode(list)) }
        })
        root.put("periodRule", JSONArray().apply {
            put(s.periodRule.durMin); put(s.periodRule.breakMin); put(s.periodRule.bigBreakMin)
        })
        root.put("dndConfig", JSONObject().apply {
            put("silent", s.dndConfig.silent); put("media", s.dndConfig.media); put("filter", s.dndConfig.filter)
        })
        root.put("schedules", JSONArray().apply {
            s.schedules.forEach { m ->
                put(JSONObject().apply {
                    put("id", m.id); put("name", m.name)
                    put("startEpochDay", m.startEpochDay); put("totalWeeks", m.totalWeeks)
                })
            }
        })
        root.put("courses", JSONArray().apply {
            s.courses.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id); put("scheduleId", c.scheduleId); put("name", c.name)
                    put("weekday", c.weekday); put("startPeriod", c.startPeriod); put("endPeriod", c.endPeriod)
                    put("startWeek", c.startWeek); put("endWeek", c.endWeek); put("weekType", c.weekType.name)
                    put("teacher", c.teacher ?: JSONObject.NULL); put("location", c.location ?: JSONObject.NULL)
                    put("enabled", c.enabled); put("dndPolicy", c.dndPolicy.name)
                    put("dndCode", c.dndCode ?: JSONObject.NULL)
                })
            }
        })
        root.put("specials", JSONArray().apply {
            s.specials.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id); put("scheduleId", d.scheduleId); put("epochDay", d.epochDay)
                    put("endEpochDay", d.endEpochDay ?: JSONObject.NULL); put("type", d.type.name)
                    put("courseId", d.courseId ?: JSONObject.NULL)
                    put("sourceEpochDay", d.sourceEpochDay ?: JSONObject.NULL)
                    put("builtin", d.builtin); put("name", d.name ?: JSONObject.NULL)
                    put("enabled", d.enabled); put("edited", d.edited)
                })
            }
        })
        root.put("rules", JSONArray().apply {
            s.rules.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id); put("scheduleId", r.scheduleId); put("courseId", r.courseId)
                    put("dateStart", r.dateStart); put("dateEnd", r.dateEnd)
                    put("periodStart", r.periodStart ?: JSONObject.NULL); put("periodEnd", r.periodEnd ?: JSONObject.NULL)
                    put("enabled", r.enabled)
                })
            }
        })
        return root.toString()
    }

    /** 解析失败抛 [IllegalArgumentException]（带中文原因，直接可展示给用户）。 */
    fun decode(text: String): Snapshot {
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            throw IllegalArgumentException("不是有效的 JSON 文件")
        }
        val appTag = root.optString("app")
        if (appTag != APP_TAG && appTag != LEGACY_APP_TAG)
            throw IllegalArgumentException("不是本应用的备份文件（app=$appTag）")
        val version = root.optInt("version", 0)
        if (version < 1) throw IllegalArgumentException("备份文件缺少版本信息")
        if (version > VERSION) throw IllegalArgumentException("备份来自更新版本（v$version），请先升级 App 再恢复")
        if (root.array("schedules").length() == 0) throw IllegalArgumentException("备份里没有课表数据")
        return try {
            build(root)
        } catch (_: Exception) {
            throw IllegalArgumentException("备份文件不完整或已损坏")
        }
    }

    private fun build(root: JSONObject): Snapshot {
        val schedules = root.array("schedules").objects {
            Semester(
                id = it.getLong("id"), name = it.optString("name", "我的课表"),
                startEpochDay = it.getLong("startEpochDay"), totalWeeks = it.getInt("totalWeeks")
            )
        }
        val scheduleIds = schedules.map { it.id }.toSet()

        val courses = root.array("courses").objects {
            Course(
                id = it.getLong("id"),
                name = it.getString("name"),
                weekday = it.getInt("weekday"),
                startPeriod = it.getInt("startPeriod"),
                endPeriod = it.getInt("endPeriod"),
                startWeek = it.getInt("startWeek"),
                endWeek = it.getInt("endWeek"),
                weekType = enumOr(it.optString("weekType"), WeekType.ALL),
                teacher = it.stringOrNull("teacher"),
                location = it.stringOrNull("location"),
                enabled = it.optBoolean("enabled", true),
                dndPolicy = enumOr(it.optString("dndPolicy"), DndPolicy.INHERIT),
                dndCode = it.stringOrNull("dndCode"),
                scheduleId = it.optLong("scheduleId", schedules.first().id)
            )
        }.filter { it.scheduleId in scheduleIds }   // 防止指向不存在课表的脏行

        val specials = root.array("specials").objects {
            SpecialDate(
                id = it.getLong("id"),
                epochDay = it.getLong("epochDay"),
                endEpochDay = it.longOrNull("endEpochDay"),
                type = enumOr(it.optString("type"), SpecialDateType.HOLIDAY),
                courseId = it.longOrNull("courseId"),
                sourceEpochDay = it.longOrNull("sourceEpochDay"),
                builtin = it.optBoolean("builtin", false),
                name = it.stringOrNull("name"),
                enabled = it.optBoolean("enabled", true),
                edited = it.optBoolean("edited", false),
                scheduleId = it.optLong("scheduleId", schedules.first().id)
            )
        }.filter { it.scheduleId in scheduleIds }

        val rules = root.array("rules").objects {
            CourseDndRule(
                id = it.getLong("id"),
                scheduleId = it.optLong("scheduleId", schedules.first().id),
                courseId = it.getLong("courseId"),
                dateStart = it.getLong("dateStart"),
                dateEnd = it.getLong("dateEnd"),
                periodStart = it.intOrNull("periodStart"),
                periodEnd = it.intOrNull("periodEnd"),
                enabled = it.optBoolean("enabled", true)
            )
        }.filter { it.scheduleId in scheduleIds }

        val periodEncoded = root.optString("periods", "")
        val periods = if (periodEncoded.isBlank()) PeriodTable.DEFAULT else PeriodTable.decode(periodEncoded)

        val schedulePeriods = LinkedHashMap<Long, List<Period>>()
        root.optJSONObject("schedulePeriods")?.let { obj ->
            obj.keys().forEach { k ->
                val encoded = obj.optString(k, "")
                k.toLongOrNull()?.let { id -> if (encoded.isNotBlank()) schedulePeriods[id] = PeriodTable.decode(encoded) }
            }
        }

        val ruleArr = root.optJSONArray("periodRule")
        val periodRule = if (ruleArr != null && ruleArr.length() >= 3)
            PeriodRule(ruleArr.optInt(0, 45), ruleArr.optInt(1, 10), ruleArr.optInt(2, 20))
        else PeriodRule(45, 10, 20)

        val dndObj = root.optJSONObject("dndConfig")
        val dndConfig = DndConfig(
            silent = dndObj?.optBoolean("silent", true) ?: true,
            media = dndObj?.optBoolean("media", true) ?: true,
            filter = dndObj?.optBoolean("filter", true) ?: true
        )

        val active = root.optLong("activeScheduleId", schedules.first().id)
        return Snapshot(
            activeScheduleId = if (active in scheduleIds) active else schedules.first().id,
            schedules = schedules, courses = courses, specials = specials, rules = rules,
            periods = periods, periodRule = periodRule, dndConfig = dndConfig,
            schedulePeriods = schedulePeriods.filterKeys { it in scheduleIds }   // 只保留存在的课表
        )
    }

    // ---------------- 小工具 ----------------

    private fun <T> JSONArray.objects(block: (JSONObject) -> T): List<T> =
        (0 until length()).mapNotNull { optJSONObject(it)?.let(block) }

    private fun JSONObject.array(key: String): JSONArray = optJSONArray(key) ?: JSONArray()

    private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else optString(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
}
