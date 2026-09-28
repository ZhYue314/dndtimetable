package com.dndtimetable.widget

import java.time.LocalDate

/**
 * 小组件条目标签（纯逻辑，可 JVM 单测）。
 *
 * 背景（已修 bug）：第二区取"从明天起第一个有课日"（最多 7 天）以避免空白，周五时周六无课会取到周日，
 * 但标签此前写死「明日」——出现"周日课程标着明日"的错误。现按**真实天差**给标签：
 * - +1 天 → 明日；+2 天 → 后天；≥3 天 → 具体日期（M.d）；
 * - 今天已上完的课 → 已上；今天的未上/进行中 → 无标签。
 */
internal fun courseTagLabel(dayOffset: Int, isPast: Boolean, date: LocalDate?): String? = when {
    dayOffset == 1 -> "明日"
    dayOffset == 2 -> "后天"
    dayOffset >= 3 -> date?.let { "${it.monthValue}.${it.dayOfMonth}" } ?: "未排课"
    isPast -> "已上"
    else -> null
}
