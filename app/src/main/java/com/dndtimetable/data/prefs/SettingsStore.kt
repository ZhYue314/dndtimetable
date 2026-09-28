package com.dndtimetable.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 静音方式。SILENT_ONLY=仅静音；SILENT_MEDIA=静音+屏蔽媒体；DND_ONLY=仅勿扰；DND_AND_SILENT=勿扰+静音。 */
/** 上课静音三维度，可任意组合：静音（铃声）、屏蔽媒体音、免打扰（来电/通知）。默认三种全开。 */
data class DndConfig(
    val silent: Boolean = true,
    val media: Boolean = true,
    val filter: Boolean = true
)

/**
 * 单门课的静音方式编码（存进 `course.dndCode`）：`静音,媒体,免打扰` 三位 0/1。
 * 与全局设置同一语义，便于「这门课单独用另一种方式」。
 */
object DndConfigCode {
    fun encode(c: DndConfig): String =
        "${if (c.silent) 1 else 0},${if (c.media) 1 else 0},${if (c.filter) 1 else 0}"

    fun decode(s: String?): DndConfig? {
        if (s.isNullOrBlank()) return null
        val p = s.split(",")
        if (p.size < 3) return null
        return DndConfig(silent = p[0].trim() == "1", media = p[1].trim() == "1", filter = p[2].trim() == "1")
    }

    /** 三种方式的中文简称（列表/详情展示用）。 */
    fun label(c: DndConfig): String {
        val parts = buildList {
            if (c.silent) add("静音")
            if (c.media) add("屏蔽媒体音")
            if (c.filter) add("免打扰")
        }
        return if (parts.isEmpty()) "不静音" else parts.joinToString("+")
    }
}

/** 一节课的时间窗 + 是否自动静音。 */
data class Period(val startMin: Int, val endMin: Int, val auto: Boolean)

/** 节次规则：节时长、小课间、大课间（分钟）。 */
data class PeriodRule(val durMin: Int, val breakMin: Int, val bigBreakMin: Int)

val Context.dataStore by preferencesDataStore(name = "settings")

/**
 * 节次表：每节独立存 起/止 时间（分钟）与是否自动静音。默认按规则（45分/节，同大节内10分，大节间20分）生成。
 */
object PeriodTable {
    /** 节次表可调范围：默认 10 节（5 大节）；有的学校有第 6 大节（11-12 节），故上限放到 16。 */
    const val MIN_PERIODS = 6
    const val MAX_PERIODS = 16

    // 默认 10 节（5 大节），按真实校历生成
    val DEFAULT: List<Period> = listOf(
        Period(480, 525, true),   // 第1节 8:00-8:45
        Period(535, 580, true),   // 第2节 8:55-9:40
        Period(600, 645, true),   // 第3节 10:00-10:45
        Period(655, 700, true),   // 第4节 10:55-11:40
        Period(840, 885, true),   // 第5节 14:00-14:45
        Period(895, 940, true),   // 第6节 14:55-15:40
        Period(960, 1005, true),  // 第7节 16:00-16:45
        Period(1015, 1060, true), // 第8节 16:55-17:40
        Period(1140, 1185, true), // 第9节 19:00-19:45
        Period(1190, 1240, true)  // 第10节 19:50-20:40
    )

    /** 由每个大节的起始时间，按规则生成 10 节（用于"重置为规则"）。 */
    fun fromBigStarts(bigStarts: List<Int>): List<Period> {
        val out = ArrayList<Period>()
        bigStarts.forEach { s ->
            out.add(Period(s, s + 45, false))
            out.add(Period(s + 55, s + 100, false))
        }
        return out
    }

    fun encode(list: List<Period>): String =
        list.joinToString(";") { "${it.startMin},${it.endMin},${if (it.auto) 1 else 0}" }

    /**
     * 扩/缩到 target 节（「节数」调整用）：新增节按规则顺延——新大节的第一节接上一节结束 + 大课间，
     * 同大节的第二节接上一节开始 + 节时长 + 小课间；自动静音开关沿用上一节。缩短则直接截断。
     */
    fun extend(list: List<Period>, target: Int, rule: PeriodRule): List<Period> {
        val n = target.coerceIn(MIN_PERIODS, MAX_PERIODS)
        if (n <= list.size) return list.take(n)
        if (list.isEmpty()) return list
        val out = ArrayList<Period>(list)
        val dur = rule.durMin.coerceAtLeast(1)
        while (out.size < n) {
            val i = out.size
            val prev = out.last()
            val raw = if (i % 2 == 0) prev.endMin + rule.bigBreakMin
            else prev.startMin + dur + rule.breakMin
            // 不倒退、不越过当天 24:00（异常规则下也能收敛）
            val start = raw.coerceIn(0, 24 * 60 - dur).coerceAtLeast(prev.endMin)
            out.add(Period(start, start + dur, prev.auto))
        }
        return out
    }

    fun decode(s: String): List<Period> {
        val list = s.split(";").mapNotNull { part ->
            val p = part.split(",")
            if (p.size >= 3) {
                val st = p[0].toIntOrNull(); val en = p[1].toIntOrNull()
                if (st != null && en != null) Period(st, en, p[2] == "1") else null
            } else null
        }
        // 不丢弃任何节（即使 结束<=开始），避免编辑后某节消失；仅超过上限时截断
        return if (list.size > MAX_PERIODS) list.take(MAX_PERIODS) else list
    }

    fun minuteToText(min: Int): String {
        val h = (min / 60) % 24; val m = min % 60
        return "%02d:%02d".format(h, m)
    }

    fun textToMinute(text: String): Int {
        val parts = text.split(":")
        return ((parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)) % (24 * 60)
    }
}

class SettingsStore(private val context: Context) {
    companion object {
        private val BUFFER_PRE_SEC = intPreferencesKey("buffer_pre_sec")
        private val BUFFER_POST_SEC = intPreferencesKey("buffer_post_sec")
        private val AFTER_VOL_ENABLE = booleanPreferencesKey("after_vol_enable")
        private val AFTER_VOL_PERCENT = intPreferencesKey("after_vol_percent")
        private val DND_SILENT = booleanPreferencesKey("dnd_silent")
        private val DND_MEDIA = booleanPreferencesKey("dnd_media")
        private val DND_FILTER = booleanPreferencesKey("dnd_filter")
        private val WEEKEND_DND = booleanPreferencesKey("weekend_dnd")
        private val NOTIFY_REMINDER = booleanPreferencesKey("notify_reminder")
        private val NOTIFY_SMALL = booleanPreferencesKey("notify_small")
        private val NOTIFY_BIG = booleanPreferencesKey("notify_big")
        private val NOTIFY_INCLASS = booleanPreferencesKey("notify_inclass")
        private val STATUS_NOTIF = booleanPreferencesKey("status_notif")
        private val PERIODS = stringPreferencesKey("periods")
        private val PERIOD_RULE = stringPreferencesKey("period_rule")
        private val NOTICE_LEAD = intPreferencesKey("notice_lead_min")
        private val SCHEDULING_ENABLED = booleanPreferencesKey("scheduling_enabled")
        private val TPL_SMALL = stringPreferencesKey("tpl_small_break")
        private val TPL_BIG = stringPreferencesKey("tpl_big_break")
        private val TPL_REMINDER = stringPreferencesKey("tpl_reminder")
        private val TPL_IN_CLASS = stringPreferencesKey("tpl_in_class")
        private val TITLE_PRE = stringPreferencesKey("title_pre")
        private val TITLE_BREAK = stringPreferencesKey("title_break")
        private val TITLE_IN_CLASS = stringPreferencesKey("title_in_class")
        private val ACTIVE_SCHEDULE = longPreferencesKey("active_schedule_id")
        private val DEV_ENABLED = booleanPreferencesKey("dev_enabled")
        private val DEV_OFFSET = intPreferencesKey("dev_offset_min")
        private val GUIDE_SEEN = booleanPreferencesKey("guide_seen")
        private val FORCE_ZERO_VOLUME = booleanPreferencesKey("force_zero_volume")
        private val ONLINE_DND_BACKFILLED = booleanPreferencesKey("online_dnd_backfilled")
        private val JIAOWU_URL = stringPreferencesKey("jiaowu_url")
        const val DEFAULT_NOTICE_LEAD_MIN = 20
        const val DEFAULT_TPL_SMALL = "非静音，开始时间{时间}"
        const val DEFAULT_TPL_BIG = "{课程}，{教室}，{时间}"
        const val DEFAULT_TPL_REMINDER = "{课程}，{教室} · {时间}"
        const val DEFAULT_TPL_IN_CLASS = "静音中，关闭时间{时间}"
        const val DEFAULT_TITLE_PRE = "快要上课啦"
        const val DEFAULT_TITLE_BREAK = "休息一会吧"
        const val DEFAULT_TITLE_IN_CLASS = "认真上课中"
        const val DEFAULT_PRE_SEC = 0
        const val DEFAULT_POST_SEC = 0
        const val MIN_SEC = -300    // 最多提前 300 秒
        const val MAX_SEC = 300     // 最多延后 300 秒
    }

    // 单位：秒；越界脏数据直接钳回范围
    val bufferPre: Flow<Int> = context.dataStore.data.map { (it[BUFFER_PRE_SEC] ?: DEFAULT_PRE_SEC).coerceIn(MIN_SEC, MAX_SEC) }
    val bufferPost: Flow<Int> = context.dataStore.data.map { (it[BUFFER_POST_SEC] ?: DEFAULT_POST_SEC).coerceIn(MIN_SEC, MAX_SEC) }
    /** 下课后媒体音量：开关 + 百分比（0~100）。关闭则按现有逻辑还原课前状态。 */
    val afterClassVolEnabled: Flow<Boolean> = context.dataStore.data.map { it[AFTER_VOL_ENABLE] ?: false }
    val afterClassVolPercent: Flow<Int> = context.dataStore.data.map { (it[AFTER_VOL_PERCENT] ?: 50).coerceIn(0, 100) }
    val dndConfig: Flow<DndConfig> = context.dataStore.data.map {
        DndConfig(
            silent = it[DND_SILENT] ?: true,
            media = it[DND_MEDIA] ?: true,
            filter = it[DND_FILTER] ?: true
        )
    }
    /** 周末（周六/周日）是否也自动免打扰；默认关——补课、调休日照常。 */
    val weekendDnd: Flow<Boolean> = context.dataStore.data.map { it[WEEKEND_DND] ?: false }
    val statusNotif: Flow<Boolean> = context.dataStore.data.map { it[STATUS_NOTIF] ?: true }
    /** 通知管理：每类消息单独开关（默认全开）。 */
    val notifyReminder: Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_REMINDER] ?: true }
    val notifySmallBreak: Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_SMALL] ?: true }
    val notifyBigBreak: Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_BIG] ?: true }
    val notifyInClass: Flow<Boolean> = context.dataStore.data.map { it[NOTIFY_INCLASS] ?: true }
    /** 上课提醒提前分钟数（0 = 关闭提醒）。 */
    val noticeLeadMin: Flow<Int> = context.dataStore.data.map {
        (it[NOTICE_LEAD] ?: DEFAULT_NOTICE_LEAD_MIN).coerceIn(0, 120)
    }
    /** 总开关：关闭后不排闹钟、不发通知、不改系统状态。 */
    val schedulingEnabled: Flow<Boolean> = context.dataStore.data.map { it[SCHEDULING_ENABLED] ?: true }

    // 通知文案模板（占位符：{课程} {教室} {时间}）
    val tplSmallBreak: Flow<String> = context.dataStore.data.map { it[TPL_SMALL] ?: DEFAULT_TPL_SMALL }
    val tplBigBreak: Flow<String> = context.dataStore.data.map { it[TPL_BIG] ?: DEFAULT_TPL_BIG }
    val tplReminder: Flow<String> = context.dataStore.data.map { it[TPL_REMINDER] ?: DEFAULT_TPL_REMINDER }
    val tplInClass: Flow<String> = context.dataStore.data.map { it[TPL_IN_CLASS] ?: DEFAULT_TPL_IN_CLASS }
    /** 常驻状态通知的标题（按场景）：第一节课前 / 课间 / 上课中。 */
    val titlePre: Flow<String> = context.dataStore.data.map { it[TITLE_PRE] ?: DEFAULT_TITLE_PRE }
    val titleBreak: Flow<String> = context.dataStore.data.map { it[TITLE_BREAK] ?: DEFAULT_TITLE_BREAK }
    val titleInClass: Flow<String> = context.dataStore.data.map { it[TITLE_IN_CLASS] ?: DEFAULT_TITLE_IN_CLASS }

    /** 当前激活的课表 id。 */
    val activeScheduleId: Flow<Long> = context.dataStore.data.map { it[ACTIVE_SCHEDULE] ?: 1L }
    suspend fun getActiveScheduleId(): Long = activeScheduleId.first()
    suspend fun setActiveScheduleId(v: Long) { context.dataStore.edit { it[ACTIVE_SCHEDULE] = v } }

    // 开发者模式
    val devEnabled: Flow<Boolean> = context.dataStore.data.map { it[DEV_ENABLED] ?: false }
    /** 模拟时间偏移（分钟，可负）。 */
    val devOffsetMin: Flow<Int> = context.dataStore.data.map { (it[DEV_OFFSET] ?: 0).coerceIn(-10080, 10080) }
    suspend fun setDevEnabled(b: Boolean) { context.dataStore.edit { it[DEV_ENABLED] = b } }
    suspend fun setDevOffsetMin(v: Int) { context.dataStore.edit { it[DEV_OFFSET] = v.coerceIn(-10080, 10080) } }
    /**
     * 节次表按课表独立：`periods_<课表id>` 存覆盖值，没存过的课表回退到全局默认（老数据兼容）。
     * 全局默认只在备份恢复时写入；平时的读写都走 [periodsFor] / [setPeriodsFor]。
     */
    private fun periodsKey(scheduleId: Long) = stringPreferencesKey("periods_$scheduleId")

    val defaultPeriods: Flow<List<Period>> = context.dataStore.data.map {
        PeriodTable.decode(it[PERIODS] ?: PeriodTable.encode(PeriodTable.DEFAULT))
    }

    /** 某张课表的节次表（有覆盖用覆盖，否则用全局默认）。 */
    fun periodsFor(scheduleId: Long): Flow<List<Period>> = context.dataStore.data.map {
        PeriodTable.decode(
            it[periodsKey(scheduleId)] ?: it[PERIODS] ?: PeriodTable.encode(PeriodTable.DEFAULT)
        )
    }
    val periodRule: Flow<PeriodRule> = context.dataStore.data.map {
        val p = (it[PERIOD_RULE] ?: "45,10,20").split(",")
        PeriodRule(
            p.getOrNull(0)?.toIntOrNull()?.coerceIn(15, 120) ?: 45,
            p.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 45) ?: 10,
            p.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 120) ?: 20
        )
    }

    suspend fun setBufferPre(v: Int) { context.dataStore.edit { it[BUFFER_PRE_SEC] = v.coerceIn(MIN_SEC, MAX_SEC) } }
    suspend fun setBufferPost(v: Int) { context.dataStore.edit { it[BUFFER_POST_SEC] = v.coerceIn(MIN_SEC, MAX_SEC) } }
    suspend fun setAfterClassVol(enabled: Boolean, percent: Int) {
        context.dataStore.edit { it[AFTER_VOL_ENABLE] = enabled; it[AFTER_VOL_PERCENT] = percent.coerceIn(0, 100) }
    }
    suspend fun setDndConfig(c: DndConfig) {
        context.dataStore.edit { it[DND_SILENT] = c.silent; it[DND_MEDIA] = c.media; it[DND_FILTER] = c.filter }
    }
    suspend fun setWeekendDnd(b: Boolean) { context.dataStore.edit { it[WEEKEND_DND] = b } }
    suspend fun setStatusNotif(b: Boolean) { context.dataStore.edit { it[STATUS_NOTIF] = b } }
    suspend fun setNotifyReminder(b: Boolean) { context.dataStore.edit { it[NOTIFY_REMINDER] = b } }
    suspend fun setNotifySmallBreak(b: Boolean) { context.dataStore.edit { it[NOTIFY_SMALL] = b } }
    suspend fun setNotifyBigBreak(b: Boolean) { context.dataStore.edit { it[NOTIFY_BIG] = b } }
    suspend fun setNotifyInClass(b: Boolean) { context.dataStore.edit { it[NOTIFY_INCLASS] = b } }
    suspend fun setNoticeLeadMin(v: Int) { context.dataStore.edit { it[NOTICE_LEAD] = v.coerceIn(0, 120) } }
    suspend fun setSchedulingEnabled(b: Boolean) { context.dataStore.edit { it[SCHEDULING_ENABLED] = b } }
    suspend fun setTplSmallBreak(v: String) { context.dataStore.edit { it[TPL_SMALL] = v } }
    suspend fun setTplBigBreak(v: String) { context.dataStore.edit { it[TPL_BIG] = v } }
    suspend fun setTplReminder(v: String) { context.dataStore.edit { it[TPL_REMINDER] = v } }
    suspend fun setTplInClass(v: String) { context.dataStore.edit { it[TPL_IN_CLASS] = v } }
    suspend fun setTitlePre(v: String) { context.dataStore.edit { it[TITLE_PRE] = v } }
    suspend fun setTitleBreak(v: String) { context.dataStore.edit { it[TITLE_BREAK] = v } }
    suspend fun setTitleInClass(v: String) { context.dataStore.edit { it[TITLE_IN_CLASS] = v } }
    suspend fun setPeriodsFor(scheduleId: Long, list: List<Period>) {
        context.dataStore.edit { it[periodsKey(scheduleId)] = PeriodTable.encode(list) }
    }

    /** 删除课表时清掉它的节次表覆盖（没覆盖过的课表本就没有该键）。 */
    suspend fun clearPeriodsFor(scheduleId: Long) {
        context.dataStore.edit { it.remove(periodsKey(scheduleId)) }
    }

    /** 全局默认节次表：仅供备份恢复写入（新老课表没有覆盖值时都读它）。 */
    suspend fun setDefaultPeriods(list: List<Period>) {
        context.dataStore.edit { it[PERIODS] = PeriodTable.encode(list) }
    }
    suspend fun setPeriodRule(durMin: Int, breakMin: Int, bigBreakMin: Int) { context.dataStore.edit { it[PERIOD_RULE] = "$durMin,$breakMin,$bigBreakMin" } }

    /** 逐节改写（读-改-写合并进同一次 edit：快速连点多行时不会互相覆盖丢写）；按课表独立。 */
    suspend fun updatePeriod(scheduleId: Long, index: Int, startMin: Int, endMin: Int, auto: Boolean) {
        context.dataStore.edit { p ->
            val key = periodsKey(scheduleId)
            val list = PeriodTable.decode(p[key] ?: p[PERIODS] ?: PeriodTable.encode(PeriodTable.DEFAULT)).toMutableList()
            if (index !in list.indices) return@edit
            list[index] = Period(startMin, endMin, auto)
            p[key] = PeriodTable.encode(list)
        }
    }

    /** 改某节「开始」时间：结束=开始+durMin，其后所有节次平移相同差值（原子，同上）；按课表独立。 */
    suspend fun updatePeriodStart(scheduleId: Long, index: Int, h: Int, m: Int, durMin: Int) {
        context.dataStore.edit { p ->
            val key = periodsKey(scheduleId)
            val list = PeriodTable.decode(p[key] ?: p[PERIODS] ?: PeriodTable.encode(PeriodTable.DEFAULT)).toMutableList()
            val cur = list.getOrNull(index) ?: return@edit
            val newStart = (h * 60 + m).coerceIn(0, 24 * 60 - durMin)
            val delta = newStart - cur.startMin
            list[index] = Period(newStart, newStart + durMin, cur.auto)
            for (j in index + 1 until list.size) {
                val q = list[j]
                list[j] = Period(q.startMin + delta, q.endMin + delta, q.auto)
            }
            p[key] = PeriodTable.encode(list)
        }
    }

    /** 新手引导：是否已看过并点过「开始使用」。 */
    val guideSeen: Flow<Boolean> = context.dataStore.data.map { it[GUIDE_SEEN] ?: false }
    suspend fun setGuideSeen(b: Boolean) { context.dataStore.edit { it[GUIDE_SEEN] = b } }

    /**
     * 兼容模式（开发者选项）：强制用「降媒体音量到 0」屏蔽，跳过静音标记。
     * 面向"标记谎报成功但实际没有静音"的 ROM（开发模式自检确认后手动开启）。
     * 开启时同样写归零守卫，保证课后还原。
     */
    val forceZeroVolume: Flow<Boolean> = context.dataStore.data.map { it[FORCE_ZERO_VOLUME] ?: false }
    suspend fun setForceZeroVolume(b: Boolean) { context.dataStore.edit { it[FORCE_ZERO_VOLUME] = b } }

    /**
     * 「网络课/MOOC 默认不自动免打扰」的一次性回填标记：
     * 老用户升级后只跑一次，之后用户自己改回来的设置不会被再次覆盖。
     */
    suspend fun onlineDndBackfilled(): Boolean = context.dataStore.data.first()[ONLINE_DND_BACKFILLED] ?: false
    suspend fun setOnlineDndBackfilled(b: Boolean) { context.dataStore.edit { it[ONLINE_DND_BACKFILLED] = b } }

    /** 教务网页导入：上次使用的网址（预填，免每次手输）。 */
    val jiaowuUrl: Flow<String> = context.dataStore.data.map { it[JIAOWU_URL] ?: "" }
    suspend fun setJiaowuUrl(v: String) { context.dataStore.edit { it[JIAOWU_URL] = v.trim() } }

    suspend fun getDefaultPeriods(): List<Period> = defaultPeriods.first()
    suspend fun getPeriodsFor(scheduleId: Long): List<Period> = periodsFor(scheduleId).first()
    /** 当前激活课表的节次表（接收器/小组件等无 UI 场景用）。 */
    suspend fun getActivePeriods(): List<Period> = getPeriodsFor(getActiveScheduleId())

    /** 备份用：所有课表的独立节次表覆盖值。 */
    suspend fun allPeriodOverrides(): Map<Long, List<Period>> {
        val p = context.dataStore.data.first()
        val out = LinkedHashMap<Long, List<Period>>()
        p.asMap().forEach { (k, v) ->
            if (!k.name.startsWith("periods_") || v !is String) return@forEach
            k.name.removePrefix("periods_").toLongOrNull()?.let { id -> out[id] = PeriodTable.decode(v) }
        }
        return out
    }

    suspend fun getPeriodRule(): PeriodRule = periodRule.first()
}
