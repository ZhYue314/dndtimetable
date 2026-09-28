package com.dndtimetable.domain

import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.WeekType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 课程配色稳定性测试。
 * 回归背景（已修）：旧实现按「课表位置」贪心分配色板，周一早上新增一门课会插到
 * 分配顺序最前、抢走靠前的色板槽，导致整张课表所有色块变色（用户反馈）。
 */
class CourseColorsTest {

    private fun course(name: String, weekday: Int, start: Int, end: Int = start) =
        Course(name = name, weekday = weekday, startPeriod = start, endPeriod = end,
            startWeek = 1, endWeek = 16, weekType = WeekType.ALL)

    private val base = listOf(
        course("马克思主义基本原理", 1, 3, 4),
        course("JAVA程序设计", 1, 5, 6),
        course("新中国史（四史）", 1, 9, 10),
        course("数字电路与逻辑设计", 2, 1, 2),
        course("计算机网络概论", 2, 3, 4),
        course("计算机组成原理", 3, 1, 2),
        course("大学外语III", 3, 3, 4),
        course("算法设计与分析", 4, 3, 4),
        course("大学体育III", 5, 3, 4)
    )

    @Test
    fun `相同输入配色完全一致`() {
        assertEquals(CourseColors.assign(base), CourseColors.assign(base))
    }

    @Test
    fun `相邻课程颜色绝不相同`() {
        val colors = CourseColors.assign(base)
        for (a in base) for (b in base) {
            if (a.name == b.name) continue
            val touch = a.endPeriod + 1 >= b.startPeriod && b.endPeriod + 1 >= a.startPeriod
            val near = a.weekday == b.weekday || kotlin.math.abs(a.weekday - b.weekday) == 1
            if (touch && near) {
                assertTrue("相邻课程同名色: ${a.name} / ${b.name}", colors[a.name] != colors[b.name])
            }
        }
    }

    @Test
    fun `周一早上新增课程不会重排整表颜色`() {
        val before = CourseColors.assign(base)
        // 用户场景：在周一 1-2 节新增一门课（原周一 3-4 节有课）
        val after = CourseColors.assign(base + course("计算机网络", 1, 1, 2))
        val changed = base.map { it.name }.count { before[it] != after[it] }
        assertTrue("新增一门课导致 $changed 门旧课变色（应仅少数相邻课受影响）", changed <= 2)
    }
}
