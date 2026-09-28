package com.dndtimetable.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun weekTypeToString(t: WeekType): String = t.name

    @TypeConverter
    fun stringToWeekType(s: String): WeekType = WeekType.valueOf(s)

    @TypeConverter
    fun specialDateTypeToString(t: SpecialDateType): String = t.name

    @TypeConverter
    fun stringToSpecialDateType(s: String): SpecialDateType = SpecialDateType.valueOf(s)

    @TypeConverter
    fun dndPolicyToString(p: DndPolicy): String = p.name

    @TypeConverter
    fun stringToDndPolicy(s: String): DndPolicy =
        runCatching { DndPolicy.valueOf(s) }.getOrDefault(DndPolicy.INHERIT)   // 脏数据不崩溃
}
