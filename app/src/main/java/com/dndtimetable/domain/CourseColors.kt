package com.dndtimetable.domain

import com.dndtimetable.data.db.Course

/**
 * 课程配色（ARGB Int）：App 课表与桌面小组件共用。
 * 同课同色；不同课尽量不同色；相邻（同天上下相触或相邻天同节次）绝不相同。
 */
object CourseColors {
    val palette: List<Int> = listOf(
        0xFF3D5AFE.toInt(), 0xFF00BFA5.toInt(), 0xFFFF7043.toInt(), 0xFFAB47BC.toInt(),
        0xFFE53935.toInt(), 0xFF43A047.toInt(), 0xFFFB8C00.toInt(), 0xFF3949AB.toInt(),
        0xFFD81B60.toInt(), 0xFF00897B.toInt(), 0xFF5E35B1.toInt(), 0xFF7CB342.toInt(),
        0xFF00ACC1.toInt(), 0xFF8E24AA.toInt(), 0xFF6D4C41.toInt(), 0xFF546E7A.toInt()
    )

    /** 名称→色板槽位的稳定散列：与课程增删、课表位置无关（同名永远同槽）。 */
    private fun stableSlot(name: String): Int = Math.floorMod(name.hashCode(), palette.size)

    /**
     * 输入与 App 课表一致（同一课表的全部启用课程，DAO 顺序），输出课程名→色值。
     *
     * 稳定分配：按「名称哈希槽位」排序 + 从自身槽位起线性探测取空位（哈希表同款）。
     * 这样新增/删除一门课只会波及哈希槽相邻的极少数课，而不是像按课表位置贪心那样
     * 整表顺移（用户反馈：周一加一节课导致所有色块变色）。
     * 约束不变：相邻（同天上下相触或相邻天同节次）绝不相同；不同课尽量不同色（槽位不重复优先）。
     */
    fun assign(courses: List<Course>): Map<String, Int> {
        fun touch(a: Course, b: Course): Boolean =
            a.endPeriod + 1 >= b.startPeriod && b.endPeriod + 1 >= a.startPeriod
        fun adjacent(a: Course, b: Course): Boolean =
            (a.weekday == b.weekday || kotlin.math.abs(a.weekday - b.weekday) == 1) && touch(a, b)
        val names = courses.map { it.name }.distinct()
        val adj = HashMap<String, MutableSet<String>>()
        for (a in courses) for (b in courses) {
            if (a.name != b.name && adjacent(a, b)) adj.getOrPut(a.name) { mutableSetOf() }.add(b.name)
        }
        // 稳定顺序：按名称哈希槽位（同槽按名称），与"课表位置/课程增删"无关
        val order = names.sortedWith(compareBy({ stableSlot(it) }, { it }))
        val assigned = HashMap<String, Int>()
        val used = mutableSetOf<Int>()
        for (n in order) {
            val banned = adj[n].orEmpty().mapNotNull { assigned[it] }.toSet()
            val start = stableSlot(n)
            val slots = (0 until palette.size).map { (start + it) % palette.size }
            val pick = slots.firstOrNull { it !in banned && it !in used }
                ?: slots.firstOrNull { it !in banned }
                ?: start
            assigned[n] = pick
            used.add(pick)
        }
        return assigned.mapValues { palette[it.value] }
    }
}
