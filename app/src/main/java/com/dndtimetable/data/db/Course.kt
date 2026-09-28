package com.dndtimetable.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WeekType { ALL, ODD, EVEN }

enum class SpecialDateType { HOLIDAY, MAKEUP }

/** 课程/某次课的自动免打扰策略：继承全局（课表设置）开关、关闭免打扰、或单独指定一种免打扰方式。 */
enum class DndPolicy { INHERIT, OFF, CUSTOM }

/** 一门课：按「节次」编号存储（例如 第1~2节），具体时间由节次时间表动态换算。 */
@Entity(tableName = "course")
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val weekday: Int,             // 1=周一 ... 7=周日
    val startPeriod: Int,         // 1-based 起始节次
    val endPeriod: Int,           // 1-based 结束节次
    val startWeek: Int,
    val endWeek: Int,
    val weekType: WeekType = WeekType.ALL,
    val teacher: String? = null,
    val location: String? = null,
    val enabled: Boolean = true,
    /** 本门课整体策略：默认继承全局；OFF=本学期都不自动免打扰（网络课/MOOC 默认如此）。 */
    val dndPolicy: DndPolicy = DndPolicy.INHERIT,
    /** CUSTOM 时单独指定的方式（三段编码，见 `prefs.DndConfigCode`）；仅 policy=CUSTOM 时有意义。 */
    val dndCode: String? = null,
    val scheduleId: Long = 1      // 所属课表
)

/**
 * 针对「某门课的某一段日期/节次」的免打扰开关（需求：每节课都可以单独设置是否开启免打扰）。
 * 语义是**抑制**：命中的日期段与节次区间内不自动静音（同样不发上下课通知），
 * 从而用同一条规则表达三个层级（[dndScopeLabel]）：
 *   - 本节课：dateStart = dateEnd = 某天，periodStart/periodEnd = 某几节；
 *   - 本周课：dateStart/dateEnd = 该周的周一..周日（period 留空 = 整门课）；
 *   - 本学期课：dateStart/dateEnd 覆盖整个学期（period 留空 = 整门课）。
 */
@Entity(
    tableName = "course_dnd_rule",
    indices = [Index("scheduleId"), Index("courseId")]
)
data class CourseDndRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long = 1,
    val courseId: Long,
    val dateStart: Long,                  // 起始日（epochDay，闭区间）
    val dateEnd: Long,                    // 结束日（epochDay，闭区间）
    val periodStart: Int? = null,         // null = 该课全部节次
    val periodEnd: Int? = null,
    val enabled: Boolean = true           // false = 该范围恢复免打扰（覆盖更宽的规则）
)

/** 特殊日期：某天/某时间段放假或调休补课。 */
@Entity(tableName = "special_date")
data class SpecialDate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,                // java.time.LocalDate.toEpochDay()
    val endEpochDay: Long? = null,     // 日期段（闭区间）；null = 单日
    val type: SpecialDateType = SpecialDateType.HOLIDAY,
    val courseId: Long? = null,        // MAKEUP 时可关联某课程（旧的"补单门课"用法，保留兼容）
    /** MAKEUP：补的是**哪一天**的课（epochDay）。如 9/20 补 10/6 的课 → 该字段 = 10/6。 */
    val sourceEpochDay: Long? = null,
    val builtin: Boolean = false,      // 内置法定节假日（自动播种）
    val name: String? = null,          // 内置节日名（如「中秋节」）
    val enabled: Boolean = true,       // 内置项开关；用户项恒为 true
    val edited: Boolean = false,       // 用户改过日期 → 播种不再覆盖
    val scheduleId: Long = 1           // 所属课表
)

/** 课表：名称 + 学期信息。可有多份，激活其中一份。 */
@Entity(tableName = "schedule")
data class Semester(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "我的课表",
    val startEpochDay: Long,           // 第 1 周周一（保存前统一归一到周一，见 ScheduleEngine.anchorStart）
    val totalWeeks: Int
)
