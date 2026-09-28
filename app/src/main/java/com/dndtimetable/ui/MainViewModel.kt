package com.dndtimetable.ui

import com.dndtimetable.system.AppLog

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.dndtimetable.data.BackupCodec
import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.CourseDndRule
import com.dndtimetable.data.db.DndPolicy
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.WeekType
import com.dndtimetable.data.prefs.Period
import com.dndtimetable.data.prefs.PeriodRule
import com.dndtimetable.data.prefs.PeriodTable
import com.dndtimetable.data.prefs.SettingsStore
import com.dndtimetable.data.prefs.DndConfig
import com.dndtimetable.data.prefs.DndConfigCode
import com.dndtimetable.data.prefs.HomeSnapshot
import com.dndtimetable.di.AppGraph
import com.dndtimetable.system.DevClock
import com.dndtimetable.domain.HolidayRemote
import com.dndtimetable.domain.IcsExport
import com.dndtimetable.domain.LegalHolidays
import com.dndtimetable.domain.ScheduleEngine
import com.dndtimetable.domain.computeStatus
import com.dndtimetable.importx.TextImporter
import com.dndtimetable.importx.WebTableParser
import com.dndtimetable.importx.XlsxImporter
import com.dndtimetable.notify.StatusNotification
import com.dndtimetable.scheduling.AlarmScheduler
import com.dndtimetable.scheduling.ScheduleHelper
import com.dndtimetable.system.CalendarImport
import com.dndtimetable.system.DndController
import com.dndtimetable.system.DndStateStore
import com.dndtimetable.system.HolidayFetcher
import com.dndtimetable.system.MediaKeepalive
import com.dndtimetable.system.SilentExecutor
import com.dndtimetable.system.UpdateChecker
import com.dndtimetable.widget.CourseWidgetProvider
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeStatus(
    val dndGranted: Boolean,
    val silentNow: Boolean,
    val nextText: String?,
    val todayCourses: List<Course>,
    val tomorrowCourses: List<Course> = emptyList(),
    val currentCourse: String?,
    val hasSemester: Boolean,
    val loaded: Boolean = false,
    /** 刷新序号：refresh() 递增，使状态在权限等非 Flow 数据变化时也重发（StateFlow 按 equals 去重）。 */
    val refreshSeq: Int = 0
)

private data class Snapshot(
    val c: List<Course>, val s: Semester?, val sp: List<SpecialDate>,
    val rules: List<CourseDndRule>, val pre: Int = 0, val post: Int = 0
)

/** 首页按钮是否应「开启」：按钮 = 本 App 静音会话开关——无会话→打开（按设置模式），有会话→关闭（自动判定保留/不保留）。 */
internal fun toggleShouldStart(sessionActive: Boolean): Boolean = !sessionActive

/** 应用内更新阶段。 */
enum class UpdatePhase { IDLE, CHECKING, READY, LATEST, FAILED, DOWNLOADING }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dnd = DndController(app)
    private val dndStateStore = DndStateStore(app)   // 复用实例：phoneQuiet() 每 2s 都要读一次

    /** 首帧快照（上次渲染数据）：冷启动直接填充，避免闪「还没有课程」卡片/空网格。 */
    private val boot: HomeSnapshot.Data? = HomeSnapshot.load(app)

    /** 刷新信号：refresh() 递增后并入 homeStatus，让从系统设置返回时权限状态立即重算（权限授权无广播可监听）。 */
    private val refreshTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val sid = ScheduleHelper.ensureDefaultSchedule()   // 首次启动/迁移后保证有活动课表
            // 老用户升级：开学日期归一到周一（空课表）、补种内置节假日/调休日、网络课默认不静音（都幂等）
            runCatching { ScheduleHelper.normalizeScheduleAnchors() }
            runCatching { ScheduleHelper.seedBuiltinHolidays(AppGraph.db.semesterDao().byIdOnce(sid)) }
            runCatching { backfillOnlineCourseDnd(sid) }
            runCatching { refreshHolidayTable() }   // 自托管节假日表：静默拉取，失败回退内置表
            runCatching { checkUpdate() }           // 启动静默检查新版本（GitHub Releases）
        }
    }

    /** 应用更新检查状态（启动自动查一次；关于页「检查更新」行展示并触发下载安装）。 */
    data class UpdateState(
        val phase: UpdatePhase = UpdatePhase.IDLE,
        val version: String? = null,
        val apkUrl: String? = null
    )

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState

    fun checkUpdate() {
        if (_updateState.value.phase == UpdatePhase.CHECKING) return
        _updateState.value = UpdateState(UpdatePhase.CHECKING)
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
            val app = getApplication<Application>()
            val current = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }
                .getOrNull() ?: "?"
            _updateState.value = when {
                latest == null -> UpdateState(UpdatePhase.FAILED)
                UpdateChecker.isNewer(latest.version, current) ->
                    UpdateState(UpdatePhase.READY, latest.version, latest.apkUrl)
                else -> UpdateState(UpdatePhase.LATEST)
            }
        }
    }

    /** 下载新 APK 到缓存 share/ 目录（FileProvider 已放行该路径），完成后回调文件供调用方拉起安装。 */
    fun downloadUpdate(onDone: (File?) -> Unit) {
        val st = _updateState.value
        if (st.phase != UpdatePhase.READY || st.apkUrl == null) { onDone(null); return }
        _updateState.value = st.copy(phase = UpdatePhase.DOWNLOADING)
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val dest = File(File(getApplication<Application>().cacheDir, "share").apply { mkdirs() },
                    "免打扰课表-${st.version}.apk")
                if (UpdateChecker.download(st.apkUrl, dest)) dest else null
            }
            _updateState.value = if (file != null) st else UpdateState(UpdatePhase.FAILED)
            onDone(file)
        }
    }

    /**
     * 拉取自托管节假日 JSON（[HolidayRemote.sourceUrls]：主源 + jsDelivr 镜像按序试）：成功则缓存到 filesDir、
     * 注入 [LegalHolidays]、重新播种特殊日期（未被用户改过的内置行会同步到新日期）并重排闹钟。
     * 任何一步失败都不影响现有数据（缓存/内置表兜底）。返回是否同步成功。
     */
    private suspend fun refreshHolidayTable(): Boolean {
        val urls = HolidayRemote.sourceUrls()
        if (urls.isEmpty()) return false
        val json = withContext(Dispatchers.IO) { HolidayFetcher.fetchFirst(urls) } ?: return false
        val table = HolidayRemote.parse(json)
        if (table.isEmpty()) return false
        withContext(Dispatchers.IO) {
            runCatching { File(getApplication<Application>().filesDir, HolidayRemote.CACHE_FILE).writeText(json) }
        }
        LegalHolidays.installRemote(table)
        ScheduleHelper.seedBuiltinHolidays(AppGraph.db.semesterDao().byIdOnce(ScheduleHelper.ensureDefaultSchedule()))
        ScheduleHelper.scheduleNext(getApplication())
        return true
    }

    /** 特殊日期页「立即同步节假日」：多源拉取 → 缓存 → 注入 → 重新播种 → 重排，回调结果文案。 */
    fun syncHolidays(onResult: (String) -> Unit) = viewModelScope.launch {
        val ok = runCatching { refreshHolidayTable() }.getOrDefault(false)
        onResult(
            if (ok) "已同步：放假与调休已更新"
            else "同步失败，仍用内置/缓存表"
        )
    }

    val activeScheduleId: StateFlow<Long> = AppGraph.settings.activeScheduleId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), boot?.scheduleId?.takeIf { it > 0 } ?: 1L)
    val schedules: StateFlow<List<Semester>> = AppGraph.db.semesterDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val courses: StateFlow<List<Course>> = activeScheduleId
        .flatMapLatest { AppGraph.db.courseDao().allCourses(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), boot?.courses ?: emptyList())
    val semester: StateFlow<Semester?> = activeScheduleId
        .flatMapLatest { AppGraph.db.semesterDao().byId(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), boot?.semester)
    val specials: StateFlow<List<SpecialDate>> = activeScheduleId
        .flatMapLatest { AppGraph.db.specialDateDao().all(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), boot?.specials ?: emptyList())
    /** 单节课免打扰规则（本学期/本周/本节课三级）；随活动课表切换。 */
    val dndRules: StateFlow<List<CourseDndRule>> = activeScheduleId
        .flatMapLatest { AppGraph.db.courseDndRuleDao().all(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bufferPre: StateFlow<Int> = AppGraph.settings.bufferPre
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val bufferPost: StateFlow<Int> = AppGraph.settings.bufferPost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val afterVolEnabled: StateFlow<Boolean> = AppGraph.settings.afterClassVolEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val afterVolPercent: StateFlow<Int> = AppGraph.settings.afterClassVolPercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)
    val dndConfig: StateFlow<DndConfig> = AppGraph.settings.dndConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DndConfig())
    /** 周末是否也自动免打扰（默认关；补课/调休日照常）。 */
    val weekendDnd: StateFlow<Boolean> = AppGraph.settings.weekendDnd
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val statusNotif: StateFlow<Boolean> = AppGraph.settings.statusNotif
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyReminder: StateFlow<Boolean> = AppGraph.settings.notifyReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifySmallBreak: StateFlow<Boolean> = AppGraph.settings.notifySmallBreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyBigBreak: StateFlow<Boolean> = AppGraph.settings.notifyBigBreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val notifyInClass: StateFlow<Boolean> = AppGraph.settings.notifyInClass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val noticeLeadMin: StateFlow<Int> = AppGraph.settings.noticeLeadMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 20)
    val schedulingEnabled: StateFlow<Boolean> = AppGraph.settings.schedulingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    /** 新手引导是否已看过（默认 true，避免老用户升级后闪一下引导页）。 */
    val guideSeen: StateFlow<Boolean> = AppGraph.settings.guideSeen
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    /** 兼容模式（开发者选项）：强制降音量屏蔽，面向"静音标记谎报成功"的 ROM。 */
    val forceZeroVolume: StateFlow<Boolean> = AppGraph.settings.forceZeroVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val devEnabled: StateFlow<Boolean> = AppGraph.settings.devEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val devOffsetMin: StateFlow<Int> = AppGraph.settings.devOffsetMin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val tplSmallBreak: StateFlow<String> = AppGraph.settings.tplSmallBreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsStore.DEFAULT_TPL_SMALL)
    val tplBigBreak: StateFlow<String> = AppGraph.settings.tplBigBreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsStore.DEFAULT_TPL_BIG)
    val tplReminder: StateFlow<String> = AppGraph.settings.tplReminder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsStore.DEFAULT_TPL_REMINDER)
    val tplInClass: StateFlow<String> = AppGraph.settings.tplInClass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsStore.DEFAULT_TPL_IN_CLASS)
    val titlePre: StateFlow<String> = AppGraph.settings.titlePre
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsStore.DEFAULT_TITLE_PRE)
    val titleBreak: StateFlow<String> = AppGraph.settings.titleBreak
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsStore.DEFAULT_TITLE_BREAK)
    val titleInClass: StateFlow<String> = AppGraph.settings.titleInClass
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsStore.DEFAULT_TITLE_IN_CLASS)
    /** 当前课表的节次表（每张课表独立；没覆盖过的课表回退全局默认）。首帧用快照，避免网格闪一次默认作息。 */
    val periods: StateFlow<List<Period>> = activeScheduleId
        .flatMapLatest { AppGraph.settings.periodsFor(it) }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            boot?.periods?.takeIf { it.isNotEmpty() } ?: PeriodTable.DEFAULT
        )
    val periodRule: StateFlow<PeriodRule> = AppGraph.settings.periodRule
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeriodRule(45, 10, 20))
    /** 手机当前是否安静：免打扰开启（含部分系统手动免打扰读回 UNKNOWN 的情形）、非响铃（静音/震动）或本 app 正在会话中。 */
    private fun phoneQuiet(): Boolean =
        dnd.isSilentNow() ||
            (dnd.isAccessGranted() && dnd.currentFilter() == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) ||
            dnd.currentRingerMode() != AudioManager.RINGER_MODE_NORMAL ||
            dndStateStore.loadFilter() != null

    private val _dndOn = MutableStateFlow(phoneQuiet())
    /**
     * 前台轮询系统静音/免打扰状态（用户在快速设置/音量键改完后首页立刻反映）。
     * merge + WhileSubscribed 门控：UI 不在收集（App 退后台）时停止 2s 轮询，
     * 不产生无谓唤醒（与「不常驻，仅闹钟唤醒」的耗电目标一致）；按钮/刷新仍直接更新 _dndOn 即时生效。
     */
    val dndOn: StateFlow<Boolean> = merge(
        flow {
            while (true) {
                emit(phoneQuiet())
                delay(2000)
            }
            // 轮询是 4 次系统 IPC，放 IO 线程：不占主线程帧（StateFlow 发射线程安全）
        }.flowOn(Dispatchers.IO),
        _dndOn
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _dndOn.value)

    // 首页数据合并 Room 原始 Flow：首次发射即真实数据，避免 stateIn 初始值（空列表/无学期）造成的"未加载"闪现
    val homeStatus: StateFlow<HomeStatus> = combine(
        activeScheduleId.flatMapLatest { AppGraph.db.courseDao().allCourses(it) },
        activeScheduleId.flatMapLatest { AppGraph.db.semesterDao().byId(it) },
        activeScheduleId.flatMapLatest { AppGraph.db.specialDateDao().all(it) },
        activeScheduleId.flatMapLatest { AppGraph.db.courseDndRuleDao().all(it) }
    ) { c, s, sp, rules -> Snapshot(c, s, sp, rules) }
        .combine(AppGraph.settings.bufferPre) { snap, pre -> snap.copy(pre = pre) }
        .combine(AppGraph.settings.bufferPost) { snap, post -> snap.copy(post = post) }
        .combine(periods) { snap, periods -> snap to periods }
        .combine(AppGraph.settings.periodRule) { (snap, periods), rule -> Triple(snap, periods, rule) }
        .combine(AppGraph.settings.devOffsetMin) { triple, offsetMin -> triple to offsetMin }
        .combine(AppGraph.settings.weekendDnd) { state, weekendDnd -> state to weekendDnd }
        .combine(refreshTick) { combined, tick ->
            val (state, weekendDnd) = combined
            val (triple, offsetMin) = state
            val (snap, periods, rule) = triple
            // 直接用发射出的偏移构造"现在"，避免与全局时钟同步的时序竞争
            val now = Instant.now().plusSeconds(offsetMin * 60L)
            val zone = ZoneId.systemDefault()
            val today = now.atZone(zone).toLocalDate()
            val todayCourses = ScheduleEngine.activeDayCourses(snap.s, snap.c, snap.sp, today)
            val tomorrowCourses = ScheduleEngine.activeDayCourses(snap.s, snap.c, snap.sp, today.plusDays(1))
            val lead = AppGraph.settings.noticeLeadMin.first()
            val st = computeStatus(snap.s, snap.c, snap.sp, periods, zone, now, snap.pre, snap.post, lead, rule, snap.rules, weekendDnd)
            // 快照编码+写盘是 I/O，放 IO 线程：它是每次 refresh/数据变化都要跑的热路径，别卡主线程
            withContext(Dispatchers.IO) {
                runCatching {   // 供下次冷启动首帧使用（首页 + 课表页）
                    HomeSnapshot.save(
                        getApplication(),
                        HomeSnapshot.Data(
                            today = todayCourses,
                            tomorrow = tomorrowCourses,
                            nextText = st.nextText,
                            currentCourse = st.currentCourse,
                            hasSemester = snap.s != null,
                            scheduleId = snap.s?.id ?: activeScheduleId.value,
                            courses = snap.c,
                            semester = snap.s,
                            periods = periods,
                            specials = snap.sp
                        )
                    )
                }
            }
            HomeStatus(
                dndGranted = dnd.isAccessGranted(),
                silentNow = st.silentNow,
                nextText = st.nextText,
                todayCourses = todayCourses.sortedBy { it.startPeriod },
                tomorrowCourses = tomorrowCourses.sortedBy { it.startPeriod },
                currentCourse = st.currentCourse,
                hasSemester = snap.s != null,
                loaded = true,
                refreshSeq = tick
            )
        }
        // 整条重算链（引擎×3 + 状态计算 + 权限 IPC）放 Default 线程：主线程只收结果做组合
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            initialHome()  // 快照优先：杀掉后台再进时首帧即完整页面（DB 加载完无缝刷新）
        )

    /** 首帧数据：上次快照优先，无快照则置空等 DB（首次安装）。 */
    private fun initialHome(): HomeStatus {
        val snap = boot
        return if (snap != null) {
            HomeStatus(dnd.isAccessGranted(), false, snap.nextText, snap.today, snap.tomorrow, snap.currentCourse, snap.hasSemester, true)
        } else {
            HomeStatus(dnd.isAccessGranted(), false, null, emptyList(), emptyList(), null, true, false)
        }
    }

    fun refresh() {
        refreshTick.value++
        viewModelScope.launch(Dispatchers.IO) {
            ScheduleHelper.scheduleNext(getApplication())
            _dndOn.value = phoneQuiet()
        }
    }

    fun addCourse(c: Course) = viewModelScope.launch {
        AppGraph.db.courseDao().insert(withOnlineDefault(c).copy(scheduleId = activeScheduleId.value)); refresh()
    }
    fun updateCourse(c: Course) = viewModelScope.launch {
        AppGraph.db.courseDao().update(c.copy(scheduleId = activeScheduleId.value)); refresh()
    }
    fun deleteCourse(c: Course) = viewModelScope.launch {
        AppGraph.db.courseDao().delete(c)
        AppGraph.db.courseDndRuleDao().deleteOfCourse(c.id)   // 连带清掉它的单节课免打扰规则
        refresh()
    }
    fun duplicateCourse(c: Course) = viewModelScope.launch {
        AppGraph.db.courseDao().insert(withOnlineDefault(c).copy(id = 0, scheduleId = activeScheduleId.value)); refresh()
    }

    /** 新建课程的默认免打扰策略：网络课/MOOC 默认关闭自动免打扰，其余继承全局。 */
    private fun withOnlineDefault(c: Course): Course =
        if (c.dndPolicy == DndPolicy.INHERIT)
            c.copy(dndPolicy = ScheduleEngine.defaultPolicyFor(c.name, c.teacher, c.location))
        else c

    /**
     * 老数据一次性回填：把「网络课/MOOC」课程的整体策略补成 OFF（默认不自动免打扰）。
     * 已明确设置过（OFF/CUSTOM）的课程不动；用 DataStore 标记保证只跑一次，用户改回来不会被再次改掉。
     */
    private suspend fun backfillOnlineCourseDnd(sid: Long) {
        if (AppGraph.settings.onlineDndBackfilled()) return
        val dao = AppGraph.db.courseDao()
        dao.allCoursesOnce(sid).filter { it.dndPolicy == DndPolicy.INHERIT && ScheduleEngine.isOnlineCourse(it) }
            .forEach { dao.update(it.copy(dndPolicy = DndPolicy.OFF)) }
        AppGraph.settings.setOnlineDndBackfilled(true)
    }

    fun saveSemester(startEpochDay: Long, totalWeeks: Int) = viewModelScope.launch {
        val dao = AppGraph.db.semesterDao()
        val id = ScheduleHelper.ensureDefaultSchedule()
        // 学期锚点归一到周一：第 N 周必须是整周（周一..周日），否则周次与网格列错位
        val anchor = ScheduleEngine.anchorStart(LocalDate.ofEpochDay(startEpochDay)).toEpochDay()
        val cur = dao.byIdOnce(id)
        if (cur != null) dao.update(cur.copy(startEpochDay = anchor, totalWeeks = totalWeeks))
        else dao.insert(Semester(id = id, startEpochDay = anchor, totalWeeks = totalWeeks))
        runCatching { ScheduleHelper.seedBuiltinHolidays(dao.byIdOnce(id)) }   // 学期变更后补种假期
        refresh()
    }

    /** 切换活动课表。 */
    fun switchSchedule(id: Long) = viewModelScope.launch {
        AppGraph.settings.setActiveScheduleId(id)
        refresh()
    }

    fun renameSchedule(id: Long, name: String) = viewModelScope.launch {
        val cur = AppGraph.db.semesterDao().byIdOnce(id) ?: return@launch
        AppGraph.db.semesterDao().update(cur.copy(name = name))
    }

    /** 删除非活动课表（含其课程、特殊日期与单节课免打扰规则）。 */
    fun deleteSchedule(id: Long) = viewModelScope.launch {
        if (id == activeScheduleId.value) return@launch
        AppGraph.db.semesterDao().delete(id)
        AppGraph.db.courseDao().deleteAllOf(id)
        AppGraph.db.specialDateDao().deleteAllOf(id)
        AppGraph.db.courseDndRuleDao().deleteAllOf(id)
        AppGraph.settings.clearPeriodsFor(id)
        refresh()
    }
    fun addSpecial(s: SpecialDate) = viewModelScope.launch {
        AppGraph.db.specialDateDao().insert(s.copy(scheduleId = activeScheduleId.value)); refresh()
    }
    fun deleteSpecial(s: SpecialDate) = viewModelScope.launch { AppGraph.db.specialDateDao().delete(s); refresh() }

    // ---- 单门课的自动免打扰（本学期 / 本周 / 本节课三级） ----

    /** 课程整体策略（继承全局 / 关闭 / 单独方式）。 */
    fun setCourseDndPolicy(c: Course, policy: DndPolicy, custom: DndConfig? = null) = viewModelScope.launch {
        val updated = c.copy(
            dndPolicy = policy,
            dndCode = if (policy == DndPolicy.CUSTOM) DndConfigCode.encode(custom ?: DndConfig()) else null
        )
        AppGraph.db.courseDao().update(updated)
        refresh()
    }

    /** 打开/关闭某段日期（+可选节次）内该课的自动免打扰，用于「本节课/本周课/本学期课」三个层级。 */
    fun setCourseDndRange(
        c: Course, dateStart: Long, dateEnd: Long,
        periodStart: Int? = null, periodEnd: Int? = null, on: Boolean
    ) = viewModelScope.launch {
        val dao = AppGraph.db.courseDndRuleDao()
        // 同范围已有规则 → 直接改开关，避免重复行；否则插入一条新规则
        val exist = dao.allOnce(activeScheduleId.value).firstOrNull {
            it.courseId == c.id && it.dateStart == dateStart && it.dateEnd == dateEnd &&
                it.periodStart == periodStart && it.periodEnd == periodEnd
        }
        if (exist != null) dao.setEnabled(exist.id, on)
        else dao.insert(
            CourseDndRule(
                scheduleId = activeScheduleId.value, courseId = c.id,
                dateStart = dateStart, dateEnd = dateEnd,
                periodStart = periodStart, periodEnd = periodEnd, enabled = on
            )
        )
        refresh()
    }

    /** 内置法定节假日开关：关 = 该假期不拦截免打扰。 */
    fun setSpecialEnabled(s: SpecialDate, on: Boolean) =
        viewModelScope.launch { AppGraph.db.specialDateDao().update(s.copy(enabled = on)); refresh() }

    /** 修改特殊日期（含内置节假日的日期调整）。 */
    fun updateSpecial(s: SpecialDate) =
        viewModelScope.launch { AppGraph.db.specialDateDao().update(s); refresh() }

    fun setBufferPre(v: Int) = viewModelScope.launch { AppGraph.settings.setBufferPre(v); refresh() }
    fun setBufferPost(v: Int) = viewModelScope.launch { AppGraph.settings.setBufferPost(v); refresh() }
    fun setAfterClassVol(enabled: Boolean, percent: Int) = viewModelScope.launch {
        AppGraph.settings.setAfterClassVol(enabled, percent); refresh()
    }
    fun setDnd(cfg: DndConfig) = viewModelScope.launch { AppGraph.settings.setDndConfig(cfg); refresh() }
    fun setWeekendDnd(b: Boolean) = viewModelScope.launch { AppGraph.settings.setWeekendDnd(b); refresh() }
    fun setStatusNotif(b: Boolean) = viewModelScope.launch { AppGraph.settings.setStatusNotif(b); refresh() }
    fun setNotifyReminder(b: Boolean) = viewModelScope.launch { AppGraph.settings.setNotifyReminder(b); refresh() }
    fun setNotifySmallBreak(b: Boolean) = viewModelScope.launch { AppGraph.settings.setNotifySmallBreak(b); refresh() }
    fun setNotifyBigBreak(b: Boolean) = viewModelScope.launch { AppGraph.settings.setNotifyBigBreak(b); refresh() }
    fun setNotifyInClass(b: Boolean) = viewModelScope.launch { AppGraph.settings.setNotifyInClass(b); refresh() }
    fun setNoticeLeadMin(v: Int) = viewModelScope.launch { AppGraph.settings.setNoticeLeadMin(v); refresh() }

    /** 总开关：关闭时恢复系统状态并撤掉全部调度。 */
    fun setSchedulingEnabled(b: Boolean) = viewModelScope.launch {
        AppGraph.settings.setSchedulingEnabled(b)
        if (!b) SilentExecutor.end(getApplication())
        refresh()
    }
    fun setTplSmallBreak(v: String) = viewModelScope.launch { AppGraph.settings.setTplSmallBreak(v); refresh() }
    fun setTplBigBreak(v: String) = viewModelScope.launch { AppGraph.settings.setTplBigBreak(v); refresh() }
    fun setTplReminder(v: String) = viewModelScope.launch { AppGraph.settings.setTplReminder(v); refresh() }
    fun setTplInClass(v: String) = viewModelScope.launch { AppGraph.settings.setTplInClass(v); refresh() }
    fun setTitlePre(v: String) = viewModelScope.launch { AppGraph.settings.setTitlePre(v); refresh() }
    fun setTitleBreak(v: String) = viewModelScope.launch { AppGraph.settings.setTitleBreak(v); refresh() }
    fun setTitleInClass(v: String) = viewModelScope.launch { AppGraph.settings.setTitleInClass(v); refresh() }

    /** 标记新手引导已看（「开始使用」或返回时调用）。 */
    fun setGuideSeen(b: Boolean) = viewModelScope.launch { AppGraph.settings.setGuideSeen(b) }

    /** 兼容模式开关（开发者选项）：强制降音量屏蔽。 */
    fun setForceZeroVolume(b: Boolean) = viewModelScope.launch {
        AppGraph.settings.setForceZeroVolume(b)
        refresh()
    }

    /**
     * 开发者：媒体屏蔽自检——探测本机静音能力的真实行为（很多 ROM 会"谎报成功"）。
     * 依次尝试 setStreamMute(true) / adjustStreamVolume(ADJUST_MUTE)，各读取回值，
     * 最后**恢复探测前的音量与静音状态**（不影响用户当前状态），把结论交给 Toast/日志。
     */
    fun devMediaSelfCheck(onResult: (String) -> Unit) = viewModelScope.launch {
        val app = getApplication<Application>()
        val audio = app.getSystemService(AudioManager::class.java)
        val beforeVol = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val beforeMute = audio.isStreamMute(AudioManager.STREAM_MUSIC)
        val maxVol = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val devs = runCatching {
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.joinToString(",")
        }.getOrDefault("?")
        val muteReadback1 = runCatching {
            audio.setStreamMute(AudioManager.STREAM_MUSIC, true)
            audio.isStreamMute(AudioManager.STREAM_MUSIC)
        }.getOrDefault(false)
        runCatching { audio.setStreamMute(AudioManager.STREAM_MUSIC, false) }
        val muteReadback2 = runCatching {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            audio.isStreamMute(AudioManager.STREAM_MUSIC)
        }.getOrDefault(false)
        // 复原探测前状态（避免自检把用户的音量/静音改掉）
        runCatching {
            audio.setStreamMute(AudioManager.STREAM_MUSIC, beforeMute)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, beforeVol, 0)
        }
        val msg = "输出设备=$devs｜音量 $beforeVol/$maxVol｜探测前静音=$beforeMute\n" +
            "setStreamMute 读回=$muteReadback1｜ADJUST_MUTE 读回=$muteReadback2\n" +
            if (muteReadback1 || muteReadback2) "读回均表示支持 → 请播放音乐确认是否真的无声；若仍有声请开启「强制降音量屏蔽」"
            else "本机不支持静音标记 → 已自动降音量兜底（可开启「强制降音量屏蔽」固定该行为）"
        AppLog.w("dndtimetable", "媒体屏蔽自检: " + msg.replace("\n", " | "))
        onResult(msg)
    }

    /** 默认课表名：无「默认课表」用「默认课表」，否则「默认课表N」取第一个未占用的名字。 */
    /** 「默认课表 / 默认课表N」去重取名（新建与导入共用）。 */
    private suspend fun nextDefaultName(): String {
        val names = AppGraph.db.semesterDao().allOnce().map { it.name }.toSet()
        var i = 0
        while (true) {
            val name = if (i == 0) "默认课表" else "默认课表$i"
            if (name !in names) return name
            i++
        }
    }

    fun nextDefaultScheduleName(onResult: (String) -> Unit) = viewModelScope.launch {
        onResult(nextDefaultName())
    }

    /**
     * 生成指定课表（[scheduleId]，null=活动课表）的分享文字：元数据头（#名称/#开学/#周数，
     * 供 TextImporter 解析写入学期设置）+ 标准 7 列课程行（与 AI 生成格式一致，对方走「文字导入课表」）。
     * 节次时间表/特殊日期不随分享覆盖（导入方保持本地设置）。
     */
    fun buildShareText(scheduleId: Long? = null, onResult: (String) -> Unit) = viewModelScope.launch {
        val sid = scheduleId ?: ScheduleHelper.ensureDefaultSchedule()
        val sem = AppGraph.db.semesterDao().byIdOnce(sid) ?: return@launch
        val courses = AppGraph.db.courseDao().allCoursesOnce(sid).filter { it.enabled }
            .sortedWith(compareBy({ it.weekday }, { it.startPeriod }, { it.startWeek }))
        val sb = StringBuilder()
        sb.appendLine("#dndtimetable 课表")
        sb.appendLine("#名称: ${sem.name}")
        sb.appendLine("#开学: ${LocalDate.ofEpochDay(sem.startEpochDay)}")
        sb.appendLine("#周数: ${sem.totalWeeks}")
        sb.appendLine("课程名称|星期|开始节数|结束节数|老师|地点|周数")
        courses.forEach { c ->
            val weeks = when (c.weekType) {
                WeekType.ALL -> "${c.startWeek}-${c.endWeek}"
                WeekType.ODD -> "${c.startWeek}-${c.endWeek}单"
                WeekType.EVEN -> "${c.startWeek}-${c.endWeek}双"
            }
            sb.appendLine("${c.name}|${c.weekday}|${c.startPeriod}|${c.endPeriod}|${c.teacher ?: ""}|${c.location ?: ""}|$weeks")
        }
        onResult(sb.toString())
    }

    // ---- 全量备份 / 恢复（JSON 文件；换机迁移） ----

    /** 导出：汇总全部课表/课程/特殊日期/规则/节次表，写出 JSON 到用户选择的文件（SAF）。 */
    fun exportBackup(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        try {
            val snap = BackupCodec.Snapshot(
                activeScheduleId = AppGraph.settings.getActiveScheduleId(),
                schedules = AppGraph.db.semesterDao().allOnce(),
                courses = AppGraph.db.courseDao().allOnceAll(),
                specials = AppGraph.db.specialDateDao().allOnceAll(),
                rules = AppGraph.db.courseDndRuleDao().allOnceAll(),
                periods = AppGraph.settings.getDefaultPeriods(),
                periodRule = AppGraph.settings.getPeriodRule(),
                dndConfig = AppGraph.settings.dndConfig.first(),
                schedulePeriods = AppGraph.settings.allPeriodOverrides()
            )
            val text = BackupCodec.encode(snap)
            val ok = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
            }
            onResult(if (ok) "已导出：${snap.schedules.size} 个课表 · ${snap.courses.size} 门课程 · ${snap.specials.size} 条假期/调休" else "无法写入所选文件")
        } catch (e: Exception) {
            onResult(e.message ?: "导出失败")
        }
    }

    /** 导出调试日志（运行自检页「调试日志」）：old+当前日志拼成文本，写到用户选择的文件。 */
    fun exportLog(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) { AppLog.exportText() }
        if (text.isEmpty()) { onResult("暂无日志"); return@launch }
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray())
                } != null
            }.getOrDefault(false)
        }
        onResult(if (ok) "日志已导出" else "无法写入所选文件")
    }

    /** 恢复第一步：只解析不写库，交给 UI 预览（与导入同一原则：确认后才动数据）。 */
    fun previewBackup(context: Context, uri: Uri, onResult: (BackupCodec.Snapshot?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw Exception("无法读取文件")
                }
                onResult(BackupCodec.decode(text), null)
            } catch (e: Exception) {
                onResult(null, e.message ?: "备份解析失败")
            }
        }
    }

    /** 恢复第二步：覆盖当前全部数据（事务里先清后写，避免写一半）。 */
    fun applyBackup(snap: BackupCodec.Snapshot, onResult: (String) -> Unit) = viewModelScope.launch {
        try {
            AppGraph.db.withTransaction {
                AppGraph.db.courseDao().deleteEverything()
                AppGraph.db.specialDateDao().deleteEverything()
                AppGraph.db.courseDndRuleDao().deleteEverything()
                AppGraph.db.semesterDao().deleteAll()
                snap.schedules.forEach { AppGraph.db.semesterDao().insert(it) }
                snap.courses.forEach { AppGraph.db.courseDao().insert(it) }
                AppGraph.db.specialDateDao().insertAll(snap.specials)
                AppGraph.db.courseDndRuleDao().insertAll(snap.rules)
            }
            AppGraph.settings.setDefaultPeriods(snap.periods)
            snap.schedulePeriods.forEach { (id, list) -> AppGraph.settings.setPeriodsFor(id, list) }
            AppGraph.settings.setPeriodRule(snap.periodRule.durMin, snap.periodRule.breakMin, snap.periodRule.bigBreakMin)
            AppGraph.settings.setDndConfig(snap.dndConfig)
            AppGraph.settings.setActiveScheduleId(snap.activeScheduleId)
            runCatching { ScheduleHelper.seedBuiltinHolidays(AppGraph.db.semesterDao().byIdOnce(snap.activeScheduleId)) }
            refresh()
            onResult("已恢复：${snap.schedules.size} 个课表 · ${snap.courses.size} 门课程 · ${snap.specials.size} 条假期/调休")
        } catch (e: Exception) {
            onResult(e.message ?: "恢复失败")
        }
    }

    /** 导出活动课表为 .ics（系统日历可直接导入；按实际口径展开，含放假跳过与调休补课）。 */
    fun exportIcs(uri: Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        try {
            val sid = ScheduleHelper.ensureDefaultSchedule()
            val sem = AppGraph.db.semesterDao().byIdOnce(sid) ?: throw Exception("请先设置学期")
            val courses = AppGraph.db.courseDao().allCoursesOnce(sid)
            if (courses.none { it.enabled }) throw Exception("当前课表还没有课程")
            val text = IcsExport.build(
                sem, courses, AppGraph.db.specialDateDao().allOnce(sid), AppGraph.settings.getPeriodsFor(sid),
                ZoneId.systemDefault(), Instant.ofEpochMilli(DevClock.nowMs())
            )
            val ok = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } != null
            }
            val n = text.split("BEGIN:VEVENT").size - 1
            onResult(if (ok) "已导出 $n 节课" else "无法写入所选文件")
        } catch (e: Exception) {
            onResult(e.message ?: "导出失败")
        }
    }

    // ---- 一键导入系统日历 ----

    /** 可写日历列表（调用前 UI 已确保拿到日历权限）。 */
    fun loadCalendars(onResult: (List<CalendarImport.Cal>) -> Unit) = viewModelScope.launch {
        onResult(withContext(Dispatchers.IO) { runCatching { CalendarImport.calendars(getApplication()) }.getOrDefault(emptyList()) })
    }

    /** 一键把当前课表写入系统日历：先清上次导入的（防重复），remindMin>0 时每节课提前提醒。 */
    fun importToCalendar(calendarId: Long, remindMin: Int, onResult: (String) -> Unit) = viewModelScope.launch {
        try {
            val sid = ScheduleHelper.ensureDefaultSchedule()
            val sem = AppGraph.db.semesterDao().byIdOnce(sid) ?: throw Exception("请先设置学期")
            val courses = AppGraph.db.courseDao().allCoursesOnce(sid)
            if (courses.none { it.enabled }) throw Exception("当前课表还没有课程")
            val events = IcsExport.expand(
                sem, courses, AppGraph.db.specialDateDao().allOnce(sid),
                AppGraph.settings.getPeriodsFor(sid), ZoneId.systemDefault()
            )
            if (events.isEmpty()) throw Exception("按当前学期展开后没有课程")
            val n = withContext(Dispatchers.IO) { CalendarImport.replaceAll(getApplication(), calendarId, events, remindMin) }
            onResult("已导入 $n 节课到日历" + if (remindMin > 0) "（提前 $remindMin 分钟提醒）" else "")
        } catch (e: Exception) {
            onResult(e.message ?: "导入失败")
        }
    }

    /** 从系统日历移除本 App 导入的课程事件。 */
    fun removeFromCalendar(onResult: (String) -> Unit) = viewModelScope.launch {
        try {
            val n = withContext(Dispatchers.IO) { CalendarImport.removeAll(getApplication()) }
            onResult(if (n > 0) "已移除 $n 节课" else "没有本 App 导入的日历事件")
        } catch (e: Exception) {
            onResult(e.message ?: "移除失败")
        }
    }

    // ---- 开发者模式 ----
    fun setDevEnabled(b: Boolean) = viewModelScope.launch {
        AppGraph.settings.setDevEnabled(b)
        if (!b) {
            AppGraph.settings.setDevOffsetMin(0)   // 关闭时时间偏移归零，避免"看不见的模拟时间"
            refresh()
        }
    }
    fun setDevOffsetMin(v: Int) = viewModelScope.launch { AppGraph.settings.setDevOffsetMin(v); refresh() }

    /** 从活动课表挑一门课填模板（开发者模拟不挑时机，有课就用真实数据）。 */
    private suspend fun fillWithCourse(tpl: String, useEndTime: Boolean): String {
        val periods = AppGraph.settings.getActivePeriods()
        val c = AppGraph.db.courseDao()
            .allCoursesOnce(ScheduleHelper.ensureDefaultSchedule())
            .filter { it.enabled }
            .minByOrNull { it.weekday * 100 + it.startPeriod }
        val name = c?.name ?: "测试课程"
        val room = c?.location ?: "测试教室"
        val t = if (c == null) "10:00" else {
            val min = if (useEndTime) periods.getOrNull(c.endPeriod - 1)?.endMin ?: 0
            else periods.getOrNull(c.startPeriod - 1)?.startMin ?: 0
            PeriodTable.minuteToText(min)
        }
        return tpl.replace("{课程}", name).replace("{教室}", room).replace("{时间}", t)
    }

    /** 开发者：按设置页勾选的静音配置模拟上课开启。 */
    fun devStartCurrent() = viewModelScope.launch {
        val cfg = AppGraph.settings.dndConfig.first()
        devStartAs(cfg.silent, cfg.media, cfg.filter)
    }

    /** 开发者：按指定静音配置模拟上课开启（临时切换，结束后还原设置）。 */
    fun devStartAs(silent: Boolean, media: Boolean, filter: Boolean) = viewModelScope.launch {
        val app = getApplication<Application>()
        val text = fillWithCourse(AppGraph.settings.tplInClass.first(), useEndTime = true)
        val saved = AppGraph.settings.dndConfig.first()
        AppGraph.settings.setDndConfig(DndConfig(silent, media, filter))
        SilentExecutor.start(app)
        AppGraph.settings.setDndConfig(saved)   // 还原（SilentExecutor 已读完配置）
        StatusNotification.devShowStatus(app, AppGraph.settings.titleInClass.first(), text)
        _dndOn.value = phoneQuiet()
    }
    fun devShowSmallBreak() = viewModelScope.launch {
        val app = getApplication<Application>()
        SilentExecutor.end(app)   // 课间=下课：解除静音，还原到课前状态
        StatusNotification.devShowStatus(app, AppGraph.settings.titleBreak.first(), fillWithCourse(AppGraph.settings.tplSmallBreak.first(), useEndTime = false))
        _dndOn.value = phoneQuiet()
    }
    fun devShowBigBreak() = viewModelScope.launch {
        val app = getApplication<Application>()
        SilentExecutor.end(app)
        StatusNotification.devShowStatus(app, AppGraph.settings.titleBreak.first(), fillWithCourse(AppGraph.settings.tplBigBreak.first(), useEndTime = false))
        _dndOn.value = phoneQuiet()
    }
    fun devTestReminder() = viewModelScope.launch { StatusNotification.devShowReminder(getApplication()) }

    // ---- 以下为开发者调试入口：全部复用生产链路（不另写平行实现）----

    /** 刷新状态通知：与闹钟触发后同一条 [StatusNotification.update]。 */
    fun devRefreshNotif() = viewModelScope.launch { StatusNotification.update(getApplication()) }

    /** 刷新小组件：与 scheduleNext 收尾同一条 [CourseWidgetProvider.updateAll]。 */
    fun devRefreshWidget() = viewModelScope.launch { CourseWidgetProvider.updateAll(getApplication()) }

    /** 模拟划后台：与服务 onTaskRemoved 完全同一条 [MediaKeepalive.healAfterSwipe]；返回是否触发。 */
    fun devSimulateSwipe(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(MediaKeepalive.healAfterSwipe(getApplication()))
    }

    /** 孤儿自愈：音量归零残留 + DND 策略残留（scheduleNext 每次也跑，这里可单独触发）。 */
    fun devHealOrphans() = viewModelScope.launch {
        runCatching { SilentExecutor.healOrphanZeroedVolume(getApplication()) }
        runCatching { SilentExecutor.healOrphanDndPolicy(getApplication()) }
    }
    fun setPeriods(list: List<Period>) = viewModelScope.launch {
        AppGraph.settings.setPeriodsFor(activeScheduleId.value, list); refresh()
    }

    /**
     * 调整节次数（[PeriodTable.MIN_PERIODS]..[PeriodTable.MAX_PERIODS]）：
     * 增加时新增节按当前规则顺延（新大节的第一节接大课间）；减少时若当前课表仍有课程用到被删的节次则拒绝，
     * 避免课程留在库里却因为超出节次表而在网格上消失。结果通过 onResult 回给 UI（null = 无需提示）。
     */
    fun setPeriodCount(count: Int, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val cur = AppGraph.settings.getActivePeriods()
            val target = count.coerceIn(PeriodTable.MIN_PERIODS, PeriodTable.MAX_PERIODS)
            if (target == cur.size) { onResult(null); return@launch }
            if (target < cur.size) {
                val used = AppGraph.db.courseDao().allCoursesOnce(activeScheduleId.value)
                    .count { it.enabled && it.endPeriod > target }
                if (used > 0) {
                    onResult("$used 门课用到第 ${target + 1} 节及以后，先把它们改到 $target 节以内")
                    return@launch
                }
            }
            val next = PeriodTable.extend(cur, target, AppGraph.settings.getPeriodRule())
            if (next.size == cur.size) return@launch
            AppGraph.settings.setPeriodsFor(activeScheduleId.value, next)
            refresh()
            onResult(if (next.size > cur.size) "已增加到 ${next.size} 节" else "已减少到 ${next.size} 节")
        }
    }

    /** 逐节更新：仅改某一节的时间与静音开关（读改写合并进单次 DataStore.edit，快速连点不丢写）。 */
    fun updatePeriod(index: Int, startMin: Int, endMin: Int, auto: Boolean) = viewModelScope.launch {
        AppGraph.settings.updatePeriod(activeScheduleId.value, index, startMin, endMin, auto)
        refresh()
    }

    /** 改某节「开始」时间：结束=开始+节时长，其后所有节次平移相同差值（原子，见 SettingsStore）。 */
    fun updatePeriodStart(index: Int, h: Int, m: Int) = viewModelScope.launch {
        AppGraph.settings.updatePeriodStart(activeScheduleId.value, index, h, m, AppGraph.settings.getPeriodRule().durMin)
        refresh()
    }

    /** 按规则重排：保持各大节（第1/3/5/7/9节）开始时间；节变长导致重叠时按大课间顺延。 */
    fun resetPeriodsByRule() {        viewModelScope.launch {
            val rule = AppGraph.settings.getPeriodRule()
            val cur = AppGraph.settings.getActivePeriods()
            val out = ArrayList<Period>(cur.size)
            var prevEnd = Int.MIN_VALUE
            for (i in cur.indices step 2) {
                val s = maxOf(cur[i].startMin, prevEnd + rule.bigBreakMin)
                out.add(Period(s, s + rule.durMin, cur[i].auto))
                if (i + 1 < cur.size) {
                    val s2 = s + rule.durMin + rule.breakMin
                    out.add(Period(s2, s2 + rule.durMin, cur[i + 1].auto))
                    prevEnd = s2 + rule.durMin
                }
            }
            AppGraph.settings.setPeriodsFor(activeScheduleId.value, out)
            refresh()
        }
    }

    fun setPeriodRule(durMin: Int, breakMin: Int, bigBreakMin: Int) =
        viewModelScope.launch { AppGraph.settings.setPeriodRule(durMin, breakMin, bigBreakMin) }

    fun toggleDndNow() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            // 按钮 = 本 App 静音会话开关：无会话→打开（按设置的静音方式），有会话→关闭。
            // 关闭时保留/不保留由课前状态自动判定（手动免打扰→保留；手动静音/正常→全部关闭）。
            val store = DndStateStore(app)
            val sessionActive = store.loadFilter() != null && !store.isSessionStale()
            val started = toggleShouldStart(sessionActive)
            AppLog.w(
                "dndtimetable", "toggleDndNow: start=$started sessionActive=$sessionActive quiet=${phoneQuiet()} filter=${dnd.currentFilter()} ringer=${dnd.currentRingerMode()}"
            )
            if (started) {
                // 用户主动开启：清除"手动关闭抑制"，恢复自动静音
                store.clearManualSuppress()
                SilentExecutor.start(app)
            } else {
                // 用户主动关闭：抑制到本节静音窗口结束，避免下一次事件（scheduleNext / DND_START 闹钟）
                // 又把静音自动拉起来（用户反馈的"关了又自己打开"）；下一节窗口恢复自动。
                val until = ScheduleHelper.currentWindowEndMs(app)
                    ?: (System.currentTimeMillis() + 10 * 60_000L)   // 不在窗口内（如课间手动关）：抑制 10 分钟
                store.saveManualSuppressUntil(until)
                AppLog.w("dndtimetable", "toggleDndNow: 手动关闭，抑制自动静音至 $until")
                SilentExecutor.end(app)
            }
            _dndOn.value = phoneQuiet()
            StatusNotification.update(app)
        }
    }

    /**
     * 文件导入第一步：只解析不写库，结果交给 UI 预览确认（避免解析错了还覆盖掉原课表）。
     * onResult：(解析结果, 错误信息)，二者必居其一。
     */
    fun previewFileImport(context: Context, uri: Uri, onResult: (XlsxImporter.Result?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val maxPeriod = PeriodTable.MAX_PERIODS   // 按上限解析；超出当前节次表的课在导入确认时自动扩容（importParsed）
                // 文件完全没写周次时，按当前学期周数兜底，而不是只导入第 1 周
                val weeks = AppGraph.db.semesterDao().byIdOnce(ScheduleHelper.ensureDefaultSchedule())?.totalWeeks ?: 20
                // 解压 xlsx + DOM 解析是 I/O+CPU，放 IO 线程，导入预览不卡 UI
                val parsed = withContext(Dispatchers.IO) { XlsxImporter.import(context, uri, maxPeriod, weeks) }
                onResult(parsed, null)
            } catch (e: Exception) {
                onResult(null, e.message ?: "导入失败")
            }
        }
    }

    /**
     * 教务网页导入：WebView 抓到的 DOM 表格网格 JSON（[table][row][col]）→ 逐表解析取最优，
     * 先预览后写库（与文件导入同流程）。
     */
    fun previewWebTables(json: String, onResult: (XlsxImporter.Result?, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val maxPeriod = PeriodTable.MAX_PERIODS   // 按上限解析；超出当前节次表的课在导入确认时自动扩容（importParsed）
                val weeks = AppGraph.db.semesterDao().byIdOnce(ScheduleHelper.ensureDefaultSchedule())?.totalWeeks ?: 20
                // 整页表格 JSON 解析是 CPU 活，放 IO 线程
                val result = withContext(Dispatchers.IO) {
                    val tables = WebTableParser.parse(json) ?: throw Exception("页面数据读取失败，请重试")
                    if (tables.isEmpty()) throw Exception("未找到表格：先登录并进入「个人课表」页，再点导入")
                    val r = WebTableParser.parseAll(tables, maxPeriod, weeks)
                    AppLog.w(
                        "dndtimetable",
                        "webimport: ${tables.size} 张表 → ${r.courses.size} 门课；诊断=${r.warnings}"
                    )
                    r
                }
                onResult(result, null)
            } catch (e: Exception) {
                onResult(null, e.message ?: "解析失败")
            }
        }
    }

    /** 教务网页导入：上次使用的网址。 */
    val jiaowuUrl: StateFlow<String> = AppGraph.settings.jiaowuUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    fun setJiaowuUrl(v: String) = viewModelScope.launch { AppGraph.settings.setJiaowuUrl(v) }

    /** 自检/排障：立即结束当前静音会话并恢复（与下课还原同一路径）。 */
    fun endSessionNow() = viewModelScope.launch {
        SilentExecutor.end(getApplication())
        _dndOn.value = phoneQuiet()
        StatusNotification.update(getApplication())
        refresh()
    }

    /** 文件导入第二步：预览确认后写库（覆盖/新建由 UI 选定，见 importParsed）。 */
    fun commitFileImport(res: XlsxImporter.Result, name: String, intoNew: Boolean, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = try {
                withWarnings(importParsed(res.courses, res.notes, name, intoNew), res.warnings)
            } catch (e: Exception) {
                e.message ?: "导入失败"
            }
            refresh()
            onResult(result)
        }
    }

    /**
     * 从粘贴文字导入课表（AI 生成的标准格式，格式见 TextImporter）。
     * 与 xlsx 同一流程：先解析，识别不到课程或解析失败都不写库。
     */
    fun importFromText(text: String, name: String, intoNew: Boolean, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val result = try {
                val res = TextImporter.parse(text, PeriodTable.MAX_PERIODS)
                if (res.courses.isEmpty()) {
                    "未识别到课程：需「课程名|星期|开始节|结束节|老师|地点|周数」格式"
                } else {
                    withWarnings(importParsed(res.courses, emptyList(), name, intoNew, res.meta), res.warnings) +
                        if (res.badLines.isEmpty()) "" else "；${res.badLines.size} 行无法识别，已跳过"
                }
            } catch (e: Exception) {
                e.message ?: "导入失败"
            }
            refresh()
            onResult(result)
        }
    }

    /** 解析诊断（少了哪些课、为什么）附在结果后面，让用户看得见而不是被静默丢课。零课程时也要给原因。 */
    private fun withWarnings(msg: String, warnings: List<String>): String =
        if ((msg.startsWith("导入成功") || msg.startsWith("未识别到课程")) && warnings.isNotEmpty())
            msg + "；" + warnings.joinToString("；")
        else msg

    /**
     * 课表写入共用逻辑（xlsx / 文字导入）：
     *   intoNew=false → 覆盖当前课表：清空其课程后写入，名称改为 name；
     *   intoNew=true  → 新建课表：继承当前学期设置，导入后切换为活动课表。
     * [meta]（文字导入的分享头）带开学日期/总周数时一并写入学期设置（归一到周一）。
     * 返回成功信息或错误信息。
     */
    private suspend fun importParsed(
        courses: List<Course>, notes: List<String>, name: String, intoNew: Boolean,
        meta: TextImporter.ShareMeta? = null
    ): String {
        // 一门课都没解析出来时绝不写库：否则「覆盖当前课表」会先清空原课表再提示「导入成功：0 门课」
        if (courses.isEmpty()) {
            return "未识别到课程：请用 .xls/.xlsx/.csv 文件，或改用「文字导入课表」"
        }
        val displayName = name.ifBlank { "我的课表" }
        val metaStart = meta?.startEpochDay?.let { ScheduleEngine.anchorStart(LocalDate.ofEpochDay(it)).toEpochDay() }
        val metaWeeks = meta?.totalWeeks
        val dao = AppGraph.db.semesterDao()
        val cur = dao.byIdOnce(ScheduleHelper.ensureDefaultSchedule())
        val sid = if (intoNew) {
            // 新课表的节次表默认复制当前课表（每张课表独立；导入的学校作息不同再单独改）
            val inheritedPeriods = AppGraph.settings.getActivePeriods()
            val nid = dao.insert(
                Semester(
                    name = displayName,
                    startEpochDay = metaStart ?: cur?.startEpochDay ?: DevClock.today().toEpochDay(),
                    totalWeeks = metaWeeks ?: cur?.totalWeeks ?: 20
                )
            )
            AppGraph.settings.setPeriodsFor(nid, inheritedPeriods)
            AppGraph.settings.setActiveScheduleId(nid)
            nid
        } else {
            if (cur != null) {
                val updated = cur.copy(
                    name = displayName,
                    startEpochDay = metaStart ?: cur.startEpochDay,
                    totalWeeks = metaWeeks ?: cur.totalWeeks
                )
                if (updated != cur) dao.update(updated)
            }
            cur?.id ?: ScheduleHelper.ensureDefaultSchedule()
        }
        // 课表带的节次超出当前节次表（如 11-12 节）：导入确认时自动扩容，新节按规则顺延时间，免得先手动调节数再重导
        val need = courses.maxOf { it.endPeriod }
        val curPeriods = AppGraph.settings.getPeriodsFor(sid)
        val expanded = need > curPeriods.size
        if (expanded) AppGraph.settings.setPeriodsFor(sid, PeriodTable.extend(curPeriods, need, AppGraph.settings.getPeriodRule()))
        AppGraph.db.courseDao().deleteAllOf(sid)   // 覆盖语义：清空目标课表后写入，避免重复（一键导入=替换课表）
        AppGraph.db.courseDndRuleDao().deleteAllOf(sid)
        courses.forEach { AppGraph.db.courseDao().insert(withOnlineDefault(it).copy(id = 0, scheduleId = sid)) }
        notes.forEach { nm ->
            AppGraph.db.courseDao().insert(
                Course(name = nm, weekday = 1, startPeriod = 1, endPeriod = 1,
                    startWeek = 1, endWeek = 1, weekType = WeekType.ALL, enabled = false, scheduleId = sid)
            )
        }
        runCatching { ScheduleHelper.seedBuiltinHolidays(AppGraph.db.semesterDao().byIdOnce(sid)) }   // 导入课表时播种法定节假日（幂等，仅一次）
        return "导入成功：${courses.size} 门课" +
            (if (expanded) " · 节次表自动扩到 $need 节" else "") +
            if (notes.isEmpty()) "" else " · ${notes.size} 条备注"
    }
}
