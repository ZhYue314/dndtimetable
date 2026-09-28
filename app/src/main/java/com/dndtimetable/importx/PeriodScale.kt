package com.dndtimetable.importx

import com.dndtimetable.data.db.Course

/**
 * 大节→小节换算：部分课表/大模型按「大节」编号（第 1 大节 = 第 1-2 节，值 1-5）。
 * 判定条件：整个导入里所有课都是单节号（start == end）且都不超过总节数一半。
 * 命中时全部展开为 (2N-1, 2N)；学校导出的标准课表是小节区间（如 3-4），不受影响。
 */
fun expandBigPeriods(courses: List<Course>, maxPeriod: Int): List<Course> {
    if (courses.isEmpty() || maxPeriod < 2) return courses
    if (courses.any { it.startPeriod != it.endPeriod }) return courses
    if (courses.any { it.startPeriod * 2 > maxPeriod }) return courses
    return courses.map {
        it.copy(startPeriod = it.startPeriod * 2 - 1, endPeriod = it.endPeriod * 2)
    }
}

/** 统一排序：星期 → 开始节次 → 课程名（先周一后周二，同天按节次；导入预览与写库顺序一致）。 */
fun sortCourses(courses: List<Course>): List<Course> =
    courses.sortedWith(compareBy({ it.weekday }, { it.startPeriod }, { it.name }))

/**
 * 合并相邻节次的同一门课（AI 常把跨 5-7 节的课拆成 5-6 与 7-7 两行，或把跨两个大节的课拆成两行）：
 * 名称/星期/老师/地点/周次完全相同且节次相邻（上一条 end+1 == 下一条 start）时合并为一条。
 * 在 expandBigPeriods 之后调用（大节文件先换算成小节区间，再合并连续块）。
 */
fun mergeAdjacentPeriods(courses: List<Course>): List<Course> {
    val out = mutableListOf<Course>()
    courses.sortedWith(compareBy({ it.weekday }, { it.name }, { it.startPeriod })).forEach { c ->
        val last = out.lastOrNull()
        if (last != null &&
            last.weekday == c.weekday && last.name == c.name &&
            last.teacher == c.teacher && last.location == c.location &&
            last.startWeek == c.startWeek && last.endWeek == c.endWeek && last.weekType == c.weekType &&
            last.endPeriod + 1 == c.startPeriod
        ) {
            out[out.lastIndex] = last.copy(endPeriod = c.endPeriod)
        } else {
            out.add(c)
        }
    }
    return out
}
