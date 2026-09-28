package com.dndtimetable.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM course WHERE scheduleId = :sid AND enabled = 1 ORDER BY weekday, startPeriod")
    fun enabledCourses(sid: Long): Flow<List<Course>>

    @Query("SELECT * FROM course WHERE scheduleId = :sid ORDER BY weekday, startPeriod")
    fun allCourses(sid: Long): Flow<List<Course>>

    @Query("SELECT * FROM course WHERE scheduleId = :sid")
    suspend fun allCoursesOnce(sid: Long): List<Course>

    @Query("SELECT * FROM course WHERE id = :id")
    suspend fun byIdOnce(id: Long): Course?

    @Insert suspend fun insert(c: Course): Long
    @Update suspend fun update(c: Course)
    @Delete suspend fun delete(c: Course)

    @Query("DELETE FROM course WHERE scheduleId = :sid")
    suspend fun deleteAllOf(sid: Long)

    // ---- 全量备份/恢复 ----
    @Query("SELECT * FROM course")
    suspend fun allOnceAll(): List<Course>

    @Query("DELETE FROM course")
    suspend fun deleteEverything()
}

/** 单节课免打扰规则的增删改查（按课表维度读，交给 ScheduleEngine 计算）。 */
@Dao
interface CourseDndRuleDao {
    @Query("SELECT * FROM course_dnd_rule WHERE scheduleId = :sid")
    fun all(sid: Long): Flow<List<CourseDndRule>>

    @Query("SELECT * FROM course_dnd_rule WHERE scheduleId = :sid")
    suspend fun allOnce(sid: Long): List<CourseDndRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(r: CourseDndRule): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(list: List<CourseDndRule>)
    @Update suspend fun update(r: CourseDndRule)

    @Query("UPDATE course_dnd_rule SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** 按「课 + 日期段 + 节次段」精确删除（删除单节课/整周规则的开关用）。 */
    @Query(
        "DELETE FROM course_dnd_rule WHERE courseId = :courseId AND dateStart = :dateStart AND dateEnd = :dateEnd " +
            "AND IFNULL(periodStart, -1) = IFNULL(:periodStart, -1) AND IFNULL(periodEnd, -1) = IFNULL(:periodEnd, -1)"
    )
    suspend fun deleteExact(courseId: Long, dateStart: Long, dateEnd: Long, periodStart: Int?, periodEnd: Int?)

    @Query("DELETE FROM course_dnd_rule WHERE courseId = :courseId")
    suspend fun deleteOfCourse(courseId: Long)

    @Query("DELETE FROM course_dnd_rule WHERE scheduleId = :sid")
    suspend fun deleteAllOf(sid: Long)

    // ---- 全量备份/恢复 ----
    @Query("SELECT * FROM course_dnd_rule")
    suspend fun allOnceAll(): List<CourseDndRule>

    @Query("DELETE FROM course_dnd_rule")
    suspend fun deleteEverything()
}

@Dao
interface SpecialDateDao {
    @Query("SELECT * FROM special_date WHERE scheduleId = :sid")
    fun all(sid: Long): Flow<List<SpecialDate>>

    @Query("SELECT * FROM special_date WHERE scheduleId = :sid")
    suspend fun allOnce(sid: Long): List<SpecialDate>

    @Insert suspend fun insert(s: SpecialDate)
    @Insert suspend fun insertAll(list: List<SpecialDate>)
    @Update suspend fun update(s: SpecialDate)
    @Delete suspend fun delete(s: SpecialDate)

    @Query("DELETE FROM special_date WHERE scheduleId = :sid")
    suspend fun deleteAllOf(sid: Long)

    // ---- 全量备份/恢复 ----
    @Query("SELECT * FROM special_date")
    suspend fun allOnceAll(): List<SpecialDate>

    @Query("DELETE FROM special_date")
    suspend fun deleteEverything()
}

@Dao
interface SemesterDao {
    @Query("SELECT * FROM schedule ORDER BY id")
    fun all(): Flow<List<Semester>>

    @Query("SELECT * FROM schedule ORDER BY id")
    suspend fun allOnce(): List<Semester>

    @Query("SELECT * FROM schedule WHERE id = :id")
    fun byId(id: Long): Flow<Semester?>

    @Query("SELECT * FROM schedule WHERE id = :id")
    suspend fun byIdOnce(id: Long): Semester?

    @Insert suspend fun insert(s: Semester): Long
    @Update suspend fun update(s: Semester)

    @Query("DELETE FROM schedule WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM schedule")
    suspend fun deleteAll()
}
