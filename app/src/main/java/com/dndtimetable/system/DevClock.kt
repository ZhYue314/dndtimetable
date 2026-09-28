package com.dndtimetable.system

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 开发者模式：全局时间源。offsetMs=0 时与真实时间一致；
 * 偏移由设置页写入（DataStore），DndTimetableApp 启动时同步到内存。
 * 所有业务逻辑（调度/状态/通知/组件）一律从这里取"现在"。
 */
object DevClock {
    @Volatile
    var offsetMs: Long = 0L

    fun now(): Instant = Instant.now().plusMillis(offsetMs)

    fun nowMs(): Long = System.currentTimeMillis() + offsetMs

    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = now().atZone(zone).toLocalDate()
}
