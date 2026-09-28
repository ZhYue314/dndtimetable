package com.dndtimetable.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Course::class, SpecialDate::class, Semester::class, CourseDndRule::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun specialDateDao(): SpecialDateDao
    abstract fun semesterDao(): SemesterDao
    abstract fun courseDndRuleDao(): CourseDndRuleDao
}

/** v3→v4：特殊日期增加日期段/内置节日/开关字段。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE special_date ADD COLUMN endEpochDay INTEGER")
        db.execSQL("ALTER TABLE special_date ADD COLUMN builtin INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE special_date ADD COLUMN name TEXT")
        db.execSQL("ALTER TABLE special_date ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
    }
}

/** v4→v5：内置节日增加「用户改过」标记，未改过的随版本自动同步默认日期。 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE special_date ADD COLUMN edited INTEGER NOT NULL DEFAULT 0")
    }
}

/** v5→v6：多课表。semester 单例表升级为 schedule 多行表，课程/特殊日期挂 scheduleId。 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `schedule` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL DEFAULT '我的课表', `startEpochDay` INTEGER NOT NULL, `totalWeeks` INTEGER NOT NULL)"
        )
        // 迁移旧学期数据为 id=1 的默认课表；semester 为空则插入默认行
        db.execSQL("INSERT INTO schedule(id, name, startEpochDay, totalWeeks) SELECT 1, '我的课表', startEpochDay, totalWeeks FROM semester")
        db.execSQL(
            "INSERT INTO schedule(id, name, startEpochDay, totalWeeks) " +
                "SELECT 1, '我的课表', CAST(strftime('%s','now') AS INTEGER)/86400, 20 WHERE NOT EXISTS (SELECT 1 FROM schedule)"
        )
        db.execSQL("ALTER TABLE course ADD COLUMN scheduleId INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE special_date ADD COLUMN scheduleId INTEGER NOT NULL DEFAULT 1")
        db.execSQL("DROP TABLE semester")
    }
}

/**
 * v6→v7：单节课免打扰（course_dnd_rule）与调休补课来源日（special_date.sourceEpochDay）；
 * 课程增加整体免打扰策略（course.dndPolicy / dndCode）。
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE course ADD COLUMN dndPolicy TEXT NOT NULL DEFAULT 'INHERIT'")
        db.execSQL("ALTER TABLE course ADD COLUMN dndCode TEXT")
        db.execSQL("ALTER TABLE special_date ADD COLUMN sourceEpochDay INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `course_dnd_rule` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`scheduleId` INTEGER NOT NULL DEFAULT 1, " +
                "`courseId` INTEGER NOT NULL, " +
                "`dateStart` INTEGER NOT NULL, " +
                "`dateEnd` INTEGER NOT NULL, " +
                "`periodStart` INTEGER, " +
                "`periodEnd` INTEGER, " +
                "`enabled` INTEGER NOT NULL DEFAULT 1)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_dnd_rule_scheduleId` ON `course_dnd_rule` (`scheduleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_course_dnd_rule_courseId` ON `course_dnd_rule` (`courseId`)")
    }
}
