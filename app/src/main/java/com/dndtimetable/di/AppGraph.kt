package com.dndtimetable.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.dndtimetable.data.db.AppDatabase
import com.dndtimetable.data.db.MIGRATION_3_4
import com.dndtimetable.data.db.MIGRATION_4_5
import com.dndtimetable.data.db.MIGRATION_5_6
import com.dndtimetable.data.db.MIGRATION_6_7
import com.dndtimetable.data.prefs.SettingsStore

/**
 * 轻量手工依赖容器：提供数据库与设置，供 UI / 广播接收器访问（避免引入 DI 框架以控制体积）。
 */
object AppGraph {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set

    fun init(context: Context) {
        if (!::db.isInitialized) {
            val app = context.applicationContext
            db = Room.databaseBuilder(app, AppDatabase::class.java, "dndtimetable.db")
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
            settings = SettingsStore(app)
            // 联网节假日缓存启动即装入：进程被广播（开机/闹钟）拉起时播种也能用上最新表
            runCatching {
                val f = java.io.File(app.filesDir, com.dndtimetable.domain.HolidayRemote.CACHE_FILE)
                if (f.exists()) com.dndtimetable.domain.LegalHolidays.installRemote(com.dndtimetable.domain.HolidayRemote.parse(f.readText()))
            }
        }
    }
}
