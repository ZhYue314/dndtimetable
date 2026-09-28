package com.dndtimetable.system

import android.Manifest
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import com.dndtimetable.domain.IcsExport

/**
 * 一键写入系统日历（CalendarProvider）：把整学期课程批量插进用户选择的日历，可带提前提醒；
 * 每条事件打 `CUSTOM_APP_PACKAGE` 标记，重复导入先清理上次导入的（防重复），也能一键移除——
 * 只动本 App 写的事件，不碰用户自己的日程。需要 READ_CALENDAR + WRITE_CALENDAR 运行时权限（UI 负责申请）。
 */
object CalendarImport {
    data class Cal(val id: Long, val name: String, val account: String)

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** 可见且可写的日历（系统里没有的话给引导文案）。 */
    fun calendars(context: Context): List<Cal> {
        val out = ArrayList<Cal>()
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, proj,
            "${CalendarContract.Calendars.VISIBLE}=1", null, null
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(3) < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                out.add(Cal(c.getLong(0), c.getString(1) ?: "日历", c.getString(2) ?: ""))
            }
        }
        return out
    }

    /** 清理旧导入 + 分批写入新事件；返回写入的课程事件条数。 */
    fun replaceAll(context: Context, calendarId: Long, events: List<IcsExport.Event>, remindMin: Int): Int {
        removeAll(context)
        if (events.isEmpty()) return 0
        val zone = java.util.TimeZone.getDefault().id
        val batch = ArrayList<ContentProviderOperation>()
        var inserted = 0
        var pending = 0
        fun flush() {
            if (batch.isEmpty()) return
            context.contentResolver.applyBatch(CalendarContract.AUTHORITY, batch)
            inserted += pending
            pending = 0
            batch.clear()
        }
        events.forEach { e ->
            val eventOpIndex = batch.size
            batch.add(
                ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                    .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                    .withValue(CalendarContract.Events.TITLE, e.title)
                    .withValue(CalendarContract.Events.DESCRIPTION, e.description)
                    .withValue(CalendarContract.Events.EVENT_LOCATION, e.location)
                    .withValue(CalendarContract.Events.DTSTART, e.startMs)
                    .withValue(CalendarContract.Events.DTEND, e.endMs)
                    .withValue(CalendarContract.Events.EVENT_TIMEZONE, zone)
                    .withValue(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
                    .withValue(CalendarContract.Events.CUSTOM_APP_URI, "dndtimetable://${e.uid}")
                    .withValue(CalendarContract.Events.HAS_ALARM, if (remindMin > 0) 1 else 0)
                    .build()
            )
            pending++
            if (remindMin > 0) {
                batch.add(
                    ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                        .withValueBackReference(CalendarContract.Reminders.EVENT_ID, eventOpIndex)
                        .withValue(CalendarContract.Reminders.MINUTES, remindMin)
                        .withValue(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        .build()
                )
            }
            if (batch.size >= 100) flush()   // 分批，避免单次 applyBatch 过大
        }
        flush()
        return inserted
    }

    /** 移除本 App 导入的全部日历事件（按标记删，返回删除条数）。 */
    fun removeAll(context: Context): Int =
        context.contentResolver.delete(
            CalendarContract.Events.CONTENT_URI,
            "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ?", arrayOf(context.packageName)
        )
}
