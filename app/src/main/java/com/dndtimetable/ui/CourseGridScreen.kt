@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.dndtimetable.ui

import com.dndtimetable.system.AppLog

import android.Manifest
import android.appwidget.AppWidgetManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.dndtimetable.MainActivity
import com.dndtimetable.R
import com.dndtimetable.data.BackupCodec
import com.dndtimetable.data.db.Course
import com.dndtimetable.data.db.DndPolicy
import com.dndtimetable.data.db.Semester
import com.dndtimetable.data.db.SpecialDate
import com.dndtimetable.data.db.SpecialDateType
import com.dndtimetable.data.db.WeekType
import com.dndtimetable.data.prefs.Period
import com.dndtimetable.data.prefs.PeriodRule
import com.dndtimetable.data.prefs.PeriodTable
import com.dndtimetable.data.prefs.DndConfig
import com.dndtimetable.data.prefs.DndConfigCode
import com.dndtimetable.data.prefs.SettingsStore
import com.dndtimetable.domain.ScheduleEngine
import com.dndtimetable.domain.courseTimeText
import com.dndtimetable.importx.ImportPrompts
import com.dndtimetable.importx.TemplateExporter
import com.dndtimetable.importx.TextImporter
import com.dndtimetable.importx.XlsxImporter
import com.dndtimetable.scheduling.AlarmScheduler
import com.dndtimetable.system.CalendarImport
import com.dndtimetable.system.CrashLog
import com.dndtimetable.system.DevClock
import com.dndtimetable.system.RomFamily
import com.dndtimetable.system.detectRomFamily
import com.dndtimetable.system.openAutoStartSettings
import com.dndtimetable.system.openBatterySettings
import com.dndtimetable.widget.CourseWidgetProvider
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------- 课表（周网格） ----------------
/**
 * 「添加内容」弹窗（课表页「＋」与设置页「我的课表 → 添加」共用）。
 */
@Composable
fun AddContentDialog(
    onDismiss: () -> Unit,
    onManual: () -> Unit,
    onTextImport: () -> Unit,
    onExcelImport: () -> Unit,
    onWebImport: () -> Unit,
    onAiPrompt: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加内容") },
        text = { Column {
            Text("任选一种方式添加课程", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            TextButton({ onDismiss(); onManual() }) { Text("手动添加课程") }
            TextButton({ onDismiss(); onTextImport() }) { Text("文字导入课表") }
            TextButton({ onDismiss(); onExcelImport() }) { Text("Excel/CSV 导入课表") }
            TextButton({ onDismiss(); onWebImport() }) { Text("从教务网页导入") }
            TextButton({ onDismiss(); onAiPrompt() }) { Text("用 AI 生成课表") }
        } },
        confirmButton = {},
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

@Composable
fun CourseGridScreen(
    vm: MainViewModel,
    onAdd: (Course?) -> Unit,
    onEdit: (Course) -> Unit,
    onSemesterSettings: () -> Unit,
    onWebImport: () -> Unit,
    pendingAddMenu: Boolean = false,
    onAddMenuConsumed: () -> Unit = {},
    sharedFile: Uri? = null,
    sharedText: String? = null,
    sharedImage: Boolean = false,
    onSharedConsumed: () -> Unit = {}
) {
    val courses by vm.courses.collectAsState()
    val semester by vm.semester.collectAsState()
    val periods by vm.periods.collectAsState()
    val dndRules by vm.dndRules.collectAsState()
    val specials by vm.specials.collectAsState()
    val weekendDnd by vm.weekendDnd.collectAsState()
    val context = LocalContext.current
    var importMsg by remember { mutableStateOf<String?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }   // 导入前设置对话框（名称 + 覆盖/新建）
    var showImportText by remember { mutableStateOf(false) }   // 粘贴文字导入对话框（AI 生成 / 同学分享）
    var showAiPrompt by remember { mutableStateOf(false) }   // AI 提示词对话框
    var importOk by remember { mutableStateOf(false) }   // 本次导入是否成功：成功后关闭对话框回课表，取消则回「添加内容」菜单
    var importName by remember { mutableStateOf("") }
    var importIntoNew by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Pair<String, Boolean>?>(null) }   // 待选文件时的 (名称, 是否新建)
    var sharedPendingUri by remember { mutableStateOf<Uri?>(null) }   // 分享进来待导入的文件
    var pendingFile by remember { mutableStateOf<Triple<Uri, String, Boolean>?>(null) }   // 待预览的文件 (uri, 名称, 是否新建)
    var filePreview by remember { mutableStateOf<XlsxImporter.Result?>(null) }
    var filePreviewErr by remember { mutableStateOf<String?>(null) }
    var initialText by remember { mutableStateOf("") }   // 分享进来的文字（预填导入框）
    var selected by remember { mutableStateOf<Course?>(null) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }   // 点开格子那一周的日期（「今日」= 那节课的那天）
    var dndTarget by remember { mutableStateOf<Course?>(null) }   // 正在设置免打扰的课程
    var dndDate by remember { mutableStateOf<LocalDate?>(null) }
    var emptySel by remember { mutableStateOf<Pair<Int, Int>?>(null) }   // 空位弹层 (weekday, startPeriod)
    var pasteAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }   // 待粘贴的空位 (weekday, startPeriod)
    var deleting by remember { mutableStateOf<Course?>(null) }   // 待删除的课程（先问删哪些周）
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val pending = pendingImport
        pendingImport = null
        if (uri != null && pending != null) {
            pendingFile = Triple(uri, pending.first, pending.second)
            filePreview = null
            filePreviewErr = null
            vm.previewFileImport(context, uri) { res, err -> filePreview = res; filePreviewErr = err }
        }
    }

    // 设置页「我的课表 → 添加」跳过来：自动弹「添加内容」菜单（完整选项）
    LaunchedEffect(pendingAddMenu) {
        if (pendingAddMenu) { showAddMenu = true; onAddMenuConsumed() }
    }

    // 分享进入：文件 → 导入前设置；文字 → 文字导入；图片 → 引导走 AI
    LaunchedEffect(sharedFile) {
        val f = sharedFile ?: return@LaunchedEffect
        importName = ""
        importIntoNew = true
        vm.nextDefaultScheduleName { importName = it }
        sharedPendingUri = f
        showImport = true
        onSharedConsumed()
    }
    LaunchedEffect(sharedText) {
        val t = sharedText ?: return@LaunchedEffect
        initialText = t
        showImportText = true
        onSharedConsumed()
    }
    LaunchedEffect(sharedImage) {
        if (!sharedImage) return@LaunchedEffect
        android.widget.Toast.makeText(context, "图片先发给大模型转成文字，再用「文字导入课表」粘贴", android.widget.Toast.LENGTH_LONG).show()
        showAiPrompt = true
        onSharedConsumed()
    }
    val colorMap = remember(courses) { assignColors(courses.filter { it.enabled }) }
    fun colorOf(c: Course): Color = colorMap[c.name] ?: palette[Math.floorMod(c.name.hashCode(), palette.size)]

    // 当前周 + 左右滑动查看其它周（限制在学期周范围内，第1周不能再往前翻）
    val baseWeek = if (semester != null) ScheduleEngine.weekNumber(semester!!, DevClock.today()) else 0
    val maxWeek = semester?.totalWeeks ?: 0
    var weekOffset by remember { mutableStateOf(0) }
    fun clampWeek(off: Int): Int =
        if (semester == null) 0 else off.coerceIn(1 - baseWeek, maxOf(1 - baseWeek, maxWeek - baseWeek))
    val week = baseWeek + weekOffset
    LaunchedEffect(week) { emptySel = null }

    // 不整页滚动：网格区按剩余高度自适应行高（一屏显示全部节次），仅极矮屏时网格内部滚动。
    // 左右留白从 16dp 收到 8dp：窄屏把宽度让给课程格（更多课程名字符）
    Column(
        Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = 4.dp)
            .pointerInput(Unit) {
                var accum = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accum = 0f },
                    onDragEnd = {
                        when {
                            accum < -100f -> weekOffset = clampWeek(weekOffset + 1)   // 左滑 → 下一周
                            accum > 100f -> weekOffset = clampWeek(weekOffset - 1)    // 右滑 → 上一周
                        }
                    }
                ) { change, amount -> change.consume(); accum += amount }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val today = DevClock.today()
        // 标题与日期并成一块（图标行高内）：原先分成两行 + 行距，白占 ~30dp，课表区让不出来
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("第${if (week > 0) week else "—"}周", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "${today.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.CHINA)} ${today} · 左右滑动切换周",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { showAddMenu = true }) { Icon(Icons.Rounded.Add, contentDescription = null) }
            IconButton(onClick = onSemesterSettings) { Icon(Icons.Rounded.Settings, contentDescription = null) }
        }
        importMsg?.let { Text(it, fontSize = 13.sp, color = if (it.startsWith("导入成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }

        // 学期数据异步加载（null→有值会使 week 0→1），加载完成前不启用切换动画，避免首次进入闪一次侧滑
        val gridContent: @Composable (Int) -> Unit = { wk ->
            val dates = semester?.let { sm ->
                // 与周次同一锚点（第 N 周 = 该周周一..周日）：表头日期不会与星期列/课程错位
                val ws = ScheduleEngine.weekAnchor(sm).plusDays(((wk - 1) * 7).toLong())
                List(7) { ws.plusDays(it.toLong()) }
            }
            // 按「日期」取课（网格=模板视图，放假日照常显示当天名义课）：
            // 调休日=顶上来那天的课（如 9/20 显示 10/6 的课），并记下每门课显示在第几列
            // ——调休课要画在当天那一列，而不是它原本的星期列。
            val showCourses: List<Pair<Course, Int>> = if (dates == null) {
                courses.filter { it.enabled }.map { it to it.weekday }
            } else {
                dates.flatMap { d ->
                    ScheduleEngine.gridDayCourses(semester, courses, specials, d)
                        .map { it to d.dayOfWeek.value }
                }
            }
            // 本格那天整门都不自动免打扰的课：格子上标「不静音」（日期取显示列那一天，调休日按当天判定）
            val mutedIds = showCourses.filter { (c, col) ->
                if (c.dndPolicy == DndPolicy.OFF) return@filter true
                val day = dates?.getOrNull(col - 1) ?: today
                // 周末默认不自动免打扰 → 同样标「不静音」，让显示与排程一致（补课/调休日不算周末）
                if (!ScheduleEngine.dndAppliesOn(specials, day, weekendDnd)) return@filter true
                (c.startPeriod..c.endPeriod).all { ScheduleEngine.dndSuppressed(dndRules, c.id, day.toEpochDay(), it) }
            }.map { it.first.id }.toSet()
            // 表头日期角标：休=法定节假日，班=调休补课日（按引擎同款判定，与首页/小组件口径一致）
            val marks = dates?.map { d ->
                val ep = d.toEpochDay()
                when {
                    ScheduleEngine.isHoliday(specials, ep) -> "休"
                    ScheduleEngine.makeupFor(specials, ep) != null -> "班"
                    else -> null
                }
            }
            WeekGrid(
                periods, showCourses, { colorOf(it) },
                onTap = { c, col ->
                    selected = c
                    // 「今日」= 点开的那一周里这节课的那天（不是日历今天；调休课即补课当天）
                    selectedDate = dates?.getOrNull(col - 1)
                },
                today = today,                 dates = dates, marks = marks ?: emptyList(),
                modifier = Modifier.fillMaxSize(),
                emptySel = emptySel, copied = clipboardCourse,
                muted = mutedIds,
                onCellTap = { dow, p -> emptySel = if (emptySel == dow to p) null else dow to p },
                onAddAt = { dow, p ->
                    emptySel = null
                    // 默认套一个大节（两小节）：点奇数节 → n..n+1；点偶数节 → n-1..n；末节落单则向前收
                    val s = if (p % 2 == 0 || p == periods.size) (p - 1).coerceAtLeast(1) else p
                    val e = if (p % 2 == 0) p else (p + 1).coerceAtMost(periods.size)
                    onAdd(Course(id = 0, name = "", weekday = dow, startPeriod = s, endPeriod = e, startWeek = 1, endWeek = 16, weekType = WeekType.ALL, teacher = null, location = null, enabled = true))
                },
                onPasteAt = { dow, p ->
                    // 粘贴前先问贴到哪些周（此前是直接按来源课自己的周范围复制）
                    if (clipboardCourse != null) pasteAt = dow to p
                    emptySel = null
                }
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (semester == null) {
                gridContent(0)
            } else {
                AnimatedContent(
                    targetState = week,
                    transitionSpec = {
                        val fwd = targetState > initialState
                        (slideInHorizontally { if (fwd) it else -it } togetherWith slideOutHorizontally { if (fwd) -it else it })
                    },
                    label = "weekGrid",
                    modifier = Modifier.fillMaxSize()
                ) { wk -> gridContent(wk) }
            }
        }
    }

    if (showAddMenu) {
        AddContentDialog(
            onDismiss = { showAddMenu = false },
            onManual = { onAdd(null) },
            onTextImport = { showImportText = true },
            onExcelImport = {
                importName = ""
                importIntoNew = true   // 默认新建课表（避免误覆盖）
                vm.nextDefaultScheduleName { importName = it }
                showImport = true
            },
            onWebImport = { onWebImport() },
            onAiPrompt = { showAiPrompt = true }
        )
    }

    // AI 生成课表：文字版 / Excel 版（复制提示词、保存模板、去导入）
    if (showAiPrompt) {
        AiPromptDialog(
            onClose = { showAiPrompt = false; showAddMenu = true },
            onTextImport = { showAiPrompt = false; showImportText = true },
            onExcelImport = {
                showAiPrompt = false
                importName = ""
                importIntoNew = true
                vm.nextDefaultScheduleName { importName = it }
                showImport = true
            }
        )
    }

    // 文字导入：粘贴 AI 生成的标准格式 → 实时解析预览 → 设置名称 + 覆盖/新建
    if (showImportText) {
        ImportTextDialog(
            vm,
            maxPeriod = periods.size,
            initialText = initialText,
            onDismiss = { showImportText = false; initialText = ""; if (!importOk) showAddMenu = true; importOk = false },
            onResult = { importMsg = it; importOk = it?.startsWith("导入成功") == true }
        )
    }

    // 导入前设置：课表名称 + 覆盖当前课表 / 新建课表
    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false; showAddMenu = true },
            title = { Text("导入课表") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("先给课表设置名称", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = importName, onValueChange = { importName = it }, label = { Text("课表名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("导入方式", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !importIntoNew, onClick = { importIntoNew = false })
                        Column(Modifier.weight(1f)) {
                            Text("覆盖当前课表", fontWeight = FontWeight.Medium)
                            Text("「${semester?.name ?: "我的课表"}」的 ${courses.size} 门课将被清空，名称用上面输入的", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = importIntoNew, onClick = { importIntoNew = true })
                        Column(Modifier.weight(1f)) {
                            Text("新建课表", fontWeight = FontWeight.Medium)
                            Text("导入到新课表并自动切换，当前课表保持不变", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        if (sharedPendingUri != null) "将导入分享进来的文件。" else "下一步选择 .xls/.xlsx/.csv 文件（教务系统导出或大模型生成均可）。",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importName.isNotBlank(),
                    onClick = {
                        val su = sharedPendingUri
                        if (su != null) {
                            showImport = false
                            pendingFile = Triple(su, importName.trim(), importIntoNew)
                            filePreview = null
                            filePreviewErr = null
                            vm.previewFileImport(context, su) { res, err -> filePreview = res; filePreviewErr = err }
                            sharedPendingUri = null
                        } else {
                            pendingImport = importName.trim() to importIntoNew
                            showImport = false
                            importLauncher.launch("*/*")
                        }
                    }
                ) { Text(if (sharedPendingUri != null) "导入" else "选择文件") }
            },
            dismissButton = { TextButton({ showImport = false; sharedPendingUri = null; showAddMenu = true }) { Text("取消") } }
        )
    }

    // 文件解析预览：确认门数/诊断后再写库（覆盖当前课表尤其需要）
    if (pendingFile != null && (filePreview != null || filePreviewErr != null)) {
        val res = filePreview
        AlertDialog(
            onDismissRequest = { pendingFile = null; filePreview = null; filePreviewErr = null },
            title = { Text("解析预览") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (filePreviewErr != null) {
                        Text(filePreviewErr!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    } else if (res != null) {
                        Text(
                            "共 ${res.courses.size} 门课" + if (res.notes.isEmpty()) "" else " + ${res.notes.size} 条无课表课程",
                            fontWeight = FontWeight.SemiBold
                        )
                        res.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        if (res.courses.isEmpty()) {
                            Text("没有解析出课程，请换文件或改用「文字导入课表」。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("免打扰时间按「节次时间表」换算，导入后请核对上课时间。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        res.courses.take(8).forEach { c ->
                            val wt = when (c.weekType) { WeekType.ODD -> "单"; WeekType.EVEN -> "双"; else -> "" }
                            Text(
                                "周${"一二三四五六日".getOrElse(c.weekday - 1) { '?' }} 第${c.startPeriod}-${c.endPeriod}节 " +
                                    "${c.name} ${c.startWeek}-${c.endWeek}周$wt",
                                fontSize = 12.sp
                            )
                        }
                        if (res.courses.size > 8) {
                            Text("…（其余 ${res.courses.size - 8} 门）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = res != null && res.courses.isNotEmpty(),
                    onClick = {
                        val f = pendingFile
                        val r = res
                        if (f != null && r != null && r.courses.isNotEmpty()) {
                            pendingFile = null
                            filePreview = null
                            vm.commitFileImport(r, f.second, f.third) { msg -> importMsg = msg }
                        }
                    }
                ) { Text("导入") }
            },
            dismissButton = { TextButton({ pendingFile = null; filePreview = null; filePreviewErr = null }) { Text("取消") } }
        )
    }

    selected?.let { c ->
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = rememberModalBottomSheetState()) {
            CourseDetailSheet(c, periods,
                onEdit = { onEdit(c) },
                onDelete = { deleting = c; selected = null },
                onCopy = { clipboardCourse = c; selected = null },
                onDnd = { dndTarget = c; dndDate = selectedDate; selected = null })
        }
    }

    // 单门课的自动免打扰设置：学期 / 星期 / 今日三级
    dndTarget?.let { c ->
        CourseDndDialog(
            vm, c, semester, periods, dndDate,
            onClose = { dndTarget = null }
        )
    }

    // 粘贴课程：先问贴到哪些周（仅当周 / 指定周数 / 整个学期），再复制来源课
    val pasteSrc = clipboardCourse
    val pa = pasteAt
    if (pasteSrc != null && pa != null) {
        PasteCourseDialog(
            src = pasteSrc, weekday = pa.first, startPeriod = pa.second,
            curWeek = week, maxPeriod = periods.size, totalWeeks = semester?.totalWeeks ?: 0,
            onDismiss = { pasteAt = null },
            onPaste = { sw, ew, wt ->
                val end = (pa.second + (pasteSrc.endPeriod - pasteSrc.startPeriod)).coerceAtMost(periods.size)
                vm.addCourse(pasteSrc.copy(id = 0, weekday = pa.first, startPeriod = pa.second, endPeriod = end, startWeek = sw, endWeek = ew, weekType = wt, enabled = true))
                pasteAt = null
            }
        )
    }

    // 删除课程：同样先问删哪些周（整门 / 拆段），与粘贴的周范围选择一致
    deleting?.let { c ->
        DeleteCourseDialog(
            src = c, curWeek = week, totalWeeks = semester?.totalWeeks ?: 0,
            onDismiss = { deleting = null },
            onDelete = { rest ->
                when {
                    rest.isEmpty() -> vm.deleteCourse(c)                      // 整门删掉（连带清单节课免打扰规则）
                    else -> {
                        vm.updateCourse(rest[0])                             // 原地更新留下的那一段
                        rest.getOrNull(1)?.let { vm.addCourse(it) }          // 另一段新建
                    }
                }
                deleting = null
            }
        )
    }
}

/**
 * 粘贴课程的周范围询问：仅当周 / 指定周数 / 整个学期。
 * 默认「仅当周」= 当前正在看的周（强制每周，避免来源课的单双周把当周藏掉）；
 * 另两种保留来源课的单双周属性。
 */
@Composable
fun PasteCourseDialog(
    src: Course, weekday: Int, startPeriod: Int, curWeek: Int, maxPeriod: Int, totalWeeks: Int,
    onDismiss: () -> Unit, onPaste: (Int, Int, WeekType) -> Unit
) {
    var mode by remember { mutableStateOf(if (totalWeeks >= 1) 0 else 1) }   // 0=仅当周 1=指定周数 2=整个学期
    var sw by remember { mutableStateOf(src.startWeek.toString()) }
    var ew by remember { mutableStateOf(src.endWeek.toString()) }
    var err by remember { mutableStateOf<String?>(null) }
    val endPeriod = (startPeriod + (src.endPeriod - src.startPeriod)).coerceAtMost(maxPeriod)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("粘贴课程") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "「${src.name}」→ 周${ScheduleEngine.weekdayName(weekday)} 第$startPeriod~${endPeriod}节",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text("粘贴到哪些周？", fontSize = 13.sp)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = mode == 0, enabled = totalWeeks >= 1, onClick = { mode = 0 }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)) { Text("仅当周", fontSize = 12.sp) }
                    SegmentedButton(selected = mode == 1, onClick = { mode = 1 }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)) { Text("指定周数", fontSize = 12.sp) }
                    SegmentedButton(selected = mode == 2, enabled = totalWeeks >= 1, onClick = { mode = 2 }, shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)) { Text("整个学期", fontSize = 12.sp) }
                }
                when (mode) {
                    0 -> Text("只在第 $curWeek 周出现。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    2 -> Text("第 1~$totalWeeks 周都出现。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = sw, onValueChange = { sw = it.filter(Char::isDigit) },
                            label = { Text("起始周") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ew, onValueChange = { ew = it.filter(Char::isDigit) },
                            label = { Text("结束周") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when (mode) {
                    0 -> onPaste(curWeek, curWeek, WeekType.ALL)
                    2 -> onPaste(1, totalWeeks, src.weekType)
                    else -> {
                        val s = sw.toIntOrNull(); val e = ew.toIntOrNull()
                        if (s == null || e == null || s < 1 || e < s) { err = "周数无效"; return@TextButton }
                        onPaste(s, e, src.weekType)
                    }
                }
            }) { Text("粘贴") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

/**
 * 删除课程的周范围询问：仅当周 / 指定周数 / 整个学期（与 [PasteCourseDialog] 同一套选择项）。
 * 「整个学期」= 这门课的全部周（整行删掉）；「指定周数」预填这门课自己的周范围。
 * 删掉一部分时由 [ScheduleEngine.minusWeeks] 把原课拆成前后两段，删满则整行删除；
 * 范围里没删到任何一周（周次不相交、或单双周对不上）时给红字提示，不静默什么都不做。
 */
@Composable
fun DeleteCourseDialog(
    src: Course, curWeek: Int, totalWeeks: Int,
    onDismiss: () -> Unit, onDelete: (List<Course>) -> Unit
) {
    var mode by remember { mutableStateOf(if (totalWeeks >= 1) 0 else 1) }   // 0=仅当周 1=指定周数 2=整个学期
    var sw by remember { mutableStateOf(src.startWeek.toString()) }
    var ew by remember { mutableStateOf(src.endWeek.toString()) }
    var err by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除课程") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "「${src.name}」→ 周${ScheduleEngine.weekdayName(src.weekday)} 第${src.startPeriod}~${src.endPeriod}节 · 第${src.startWeek}~${src.endWeek}周",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text("删除哪些周？", fontSize = 13.sp)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = mode == 0, enabled = totalWeeks >= 1, onClick = { mode = 0 }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)) { Text("仅当周", fontSize = 12.sp) }
                    SegmentedButton(selected = mode == 1, onClick = { mode = 1 }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)) { Text("指定周数", fontSize = 12.sp) }
                    SegmentedButton(selected = mode == 2, enabled = totalWeeks >= 1, onClick = { mode = 2 }, shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)) { Text("整个学期", fontSize = 12.sp) }
                }
                when (mode) {
                    0 -> Text("只删第 $curWeek 周这一次，前后周次保留。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    2 -> Text("这门课的第 1~$totalWeeks 周全部删除。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = sw, onValueChange = { sw = it.filter(Char::isDigit) },
                            label = { Text("起始周") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = ew, onValueChange = { ew = it.filter(Char::isDigit) },
                            label = { Text("结束周") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val (s, e) = when (mode) {
                    0 -> curWeek to curWeek
                    2 -> 1 to totalWeeks
                    else -> {
                        val a = sw.toIntOrNull() ?: -1
                        val b = ew.toIntOrNull() ?: -1
                        if (a < 1 || b < a) { err = "周数无效"; return@TextButton }
                        a to b
                    }
                }
                val rest = ScheduleEngine.minusWeeks(src, s, e)
                // 原样返回 = 一周都没删掉（周次不相交 / 单双周对不上，如调休日点到来源周的课）
                if (rest.size == 1 && rest[0] == src) { err = "第 $s~$e 周这门课没有课（周次或单双周对不上）"; return@TextButton }
                onDelete(rest)
            }) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

/**
 * 单门课的自动免打扰设置（需求：每节课都能单独设置是否开启免打扰）：
 * - 学期：整门课关闭/恢复自动免打扰；
 * - 星期：逐周切换（某周不上/不需静音）；
 * - 今日：点开的那节课所在那一天（[targetDate]），按大节（两小节并一开关）切换。
 * 三级都是**抑制**语义：关掉之后该范围内不自动静音、也不发上下课通知，课表照常显示。
 */
@Composable
fun CourseDndDialog(
    vm: MainViewModel,
    courseArg: Course,
    semester: Semester?,
    periods: List<Period>,
    targetDate: LocalDate?,
    onClose: () -> Unit
) {
    val rules by vm.dndRules.collectAsState()
    val custom by vm.dndConfig.collectAsState()
    val allCourses by vm.courses.collectAsState()
    // 取最新的课程对象：否则改完策略后对话框仍拿旧快照，开关看起来"点了没反应"
    val course = allCourses.firstOrNull { it.id == courseArg.id } ?: courseArg
    var level by remember { mutableStateOf(0) }   // 0=学期 1=星期 2=今日
    val today = DevClock.today()
    val totalWeeks = semester?.totalWeeks ?: 0
    val curWeek = semester?.let { ScheduleEngine.weekNumber(it, today) } ?: 0

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("免打扰设置") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(course.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("学期", "星期", "今日").forEachIndexed { i, label ->
                        SegmentedButton(selected = level == i, onClick = { level = i }, shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)) { Text(label, fontSize = 12.sp) }
                    }
                }
                when (level) {
                    0 -> {
                        Text("整门课是否在上课时自动免打扰。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("自动免打扰", fontSize = 14.sp)
                                Text("关闭后这门课不静音（课表与通知照常）", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = course.dndPolicy != DndPolicy.OFF,
                                onCheckedChange = { on ->
                                    // 打开 = 跟随全局设置；要单独指定方式就点下面的按钮
                                    vm.setCourseDndPolicy(course, if (on) DndPolicy.INHERIT else DndPolicy.OFF)
                                }
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Text("这门课单独用哪种方式（不改全局设置）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        MultiChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            val cur = DndConfigCode.decode(course.dndCode) ?: DndConfig()
                            SegmentedButton(
                                checked = course.dndPolicy == DndPolicy.CUSTOM && cur.silent,
                                onCheckedChange = { vm.setCourseDndPolicy(course, DndPolicy.CUSTOM, cur.copy(silent = it)) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                            ) { Text("静音", fontSize = 11.sp) }
                            SegmentedButton(
                                checked = course.dndPolicy == DndPolicy.CUSTOM && cur.media,
                                onCheckedChange = { vm.setCourseDndPolicy(course, DndPolicy.CUSTOM, cur.copy(media = it)) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                            ) { Text("屏蔽媒体音", fontSize = 11.sp) }
                            SegmentedButton(
                                checked = course.dndPolicy == DndPolicy.CUSTOM && cur.filter,
                                onCheckedChange = { vm.setCourseDndPolicy(course, DndPolicy.CUSTOM, cur.copy(filter = it)) },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                            ) { Text("免打扰", fontSize = 11.sp) }
                        }
                        Text(
                            if (course.dndPolicy == DndPolicy.CUSTOM) "已单独指定：${DndConfigCode.label(DndConfigCode.decode(course.dndCode) ?: DndConfig())}"
                            else "当前跟随设置（${DndConfigCode.label(custom)}）",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    1 -> {
                        if (semester == null || totalWeeks < 1) {
                            Text("尚未设置学期，无法按周切换。", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("关闭某周后，那一周这门课不自动免打扰（如这周不上课/在家上网课）。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // 已过去的周不再显示（没必要关），从本周起列出
                            val from = curWeek.coerceIn(1, totalWeeks)
                            if (from > 1) Text("只显示第 $from 周起；更早的周已过去。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            (from..totalWeeks).forEach { wk ->
                                val range = ScheduleEngine.weekRange(semester, wk) ?: return@forEach
                                // 该周是否已整体关闭：找一条恰好覆盖整周（且不限节次）的规则
                                val off = rules.any {
                                    it.courseId == course.id && it.enabled && it.periodStart == null &&
                                        it.periodEnd == null && it.dateStart == range.start && it.dateEnd == range.endInclusive
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "第 $wk 周" + if (wk == curWeek) "（本周）" else "",
                                        Modifier.weight(1f), fontSize = 13.sp,
                                        fontWeight = if (wk == curWeek) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Text(if (off) "不静音" else "自动", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Switch(
                                        checked = !off,
                                        onCheckedChange = { on ->
                                            vm.setCourseDndRange(course, range.start, range.endInclusive, null, null, !on)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // 「今日」= 点开的那节课的那天（点格子时传入的日期），不是日历今天
                        val day = targetDate ?: today
                        val ep = day.toEpochDay()
                        val dayLabel = if (day == today) "今天"
                        else "${day.monthValue}月${day.dayOfMonth}日 周${ScheduleEngine.weekdayName(ScheduleEngine.weekdayOf(day))}"
                        Text("$dayLabel 这节课是否自动免打扰（两小节并为一个开关）。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        // 大节 = 相邻两小节（1-2、3-4…）：末节若落单则单独成组
                        (course.startPeriod..course.endPeriod).chunked(2).forEach { group ->
                            val p0 = group.first()
                            val p1 = group.last()
                            val suppressed = group.all { ScheduleEngine.dndSuppressed(rules, course.id, ep, it) }
                            val timeText = run {
                                val s = periods.getOrNull(p0 - 1)?.startMin
                                val e = periods.getOrNull(p1 - 1)?.endMin
                                if (s != null && e != null) "${PeriodTable.minuteToText(s)}–${PeriodTable.minuteToText(e)}" else ""
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (p0 == p1) "第$p0 节 $timeText" else "第$p0–$p1 节 $timeText",
                                        fontSize = 13.sp, fontWeight = FontWeight.Medium
                                    )
                                    Text(if (suppressed) "不静音" else "自动", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = !suppressed,
                                    onCheckedChange = { on -> vm.setCourseDndRange(course, ep, ep, p0, p1, !on) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClose) { Text("完成") } }
    )
}

/**
 * AI 生成课表（文字版 / Excel 版）：复制提示词给豆包等大模型（附上课表截图），
 * 文字版把生成结果粘贴导入，Excel 版保存模板后让大模型按模板生成 .xlsx 再选文件导入。
 */
@Composable
fun AiPromptDialog(
    onClose: () -> Unit,
    onTextImport: () -> Unit,
    onExcelImport: () -> Unit
) {
    val context = LocalContext.current
    var excel by remember { mutableStateOf(false) }   // false=文字版 true=Excel 版
    fun copy(label: String, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText(label, text))
                android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
    }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(TemplateExporter.MIME)
    ) { uri ->
        if (uri != null) {
            runCatching { TemplateExporter.writeTo(context, uri) }
                .onSuccess { android.widget.Toast.makeText(context, "模板已保存", android.widget.Toast.LENGTH_SHORT).show() }
                .onFailure { android.widget.Toast.makeText(context, "保存失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("用 AI 生成课表") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "把课表（截图/文字/教务系统导出）发给豆包等大模型，转成 App 能识别的标准格式。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !excel, onClick = { excel = false }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) { Text("文字版") }
                    SegmentedButton(selected = excel, onClick = { excel = true }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) { Text("Excel 版") }
                }
                if (!excel) {
                    Text(
                        "1. 复制下面的提示词\n2. 打开大模型，发送提示词 + 你的课表（有教务系统导出文件就发文件，截图也行；文件识别更准）\n" +
                            "3. 复制它生成的结果，回来点「去粘贴导入」",
                        fontSize = 12.sp
                    )
                    OutlinedButton({ copy("dndtimetable 提示词", ImportPrompts.TEXT) }, Modifier.fillMaxWidth()) {
                        Text("复制文字版提示词")
                    }
                    Button({ onTextImport() }, Modifier.fillMaxWidth()) { Text("去粘贴导入") }
                } else {
                    Text(
                        "1. 保存标准模板 .xlsx 到手机\n2. 复制提示词，把提示词、模板、课表（图片/Excel）一起发给大模型；不传模板就用「直接生成」版\n" +
                            "3. 回来点「去导入 Excel 文件」选择生成的文件",
                        fontSize = 12.sp
                    )
                    OutlinedButton({ copy("dndtimetable Excel 提示词", ImportPrompts.EXCEL) }, Modifier.fillMaxWidth()) {
                        Text("复制直接生成提示词")
                    }
                    OutlinedButton({ copy("dndtimetable 模板提示词", ImportPrompts.EXCEL_TEMPLATE) }, Modifier.fillMaxWidth()) {
                        Text("复制模板版提示词（配模板用）")
                    }
                    OutlinedButton({ saveLauncher.launch(TemplateExporter.FILE_NAME) }, Modifier.fillMaxWidth()) {
                        Text("保存 Excel 模板")
                    }
                    Button({ onExcelImport() }, Modifier.fillMaxWidth()) { Text("去导入 Excel 文件") }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClose) { Text("关闭") } }
    )
}

/**
 * 文字导入对话框：与 Excel 导入同一界面（名称 + 覆盖/新建），
 * 粘贴 AI 生成或同学分享的标准格式文字，实时解析预览（课程明细 + 无法识别行 + 分享头元数据），确认后导入。
 */
@Composable
fun ImportTextDialog(
    vm: MainViewModel,
    maxPeriod: Int,
    initialText: String = "",
    onDismiss: () -> Unit,
    onResult: (String?) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    var importName by remember { mutableStateOf("") }
    var importIntoNew by remember { mutableStateOf(true) }   // 默认新建课表
    val parsed = remember(text, maxPeriod) { TextImporter.parse(text, maxPeriod) }
    // 分享头带课表名则直接用它；否则退回默认名（用户已手动改过则不动）
    LaunchedEffect(parsed.meta?.name) {
        val metaName = parsed.meta?.name
        if (!metaName.isNullOrBlank()) importName = metaName
        else vm.nextDefaultScheduleName { if (importName.isBlank()) importName = it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文字导入课表") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("先给课表设置名称", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(value = importName, onValueChange = { importName = it }, label = { Text("课表名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("导入方式", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !importIntoNew, onClick = { importIntoNew = false })
                    Column(Modifier.weight(1f)) {
                        Text("覆盖当前课表", fontWeight = FontWeight.Medium)
                        Text("当前课表的课程将被清空，名称改为上面输入的名字", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = importIntoNew, onClick = { importIntoNew = true })
                    Column(Modifier.weight(1f)) {
                        Text("新建课表", fontWeight = FontWeight.Medium)
                        Text("导入到新课表并自动切换，当前课表保持不变", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("粘贴课表文字（大模型生成 / 同学分享）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 140.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
                parsed.meta?.let { m ->
                    val bits = listOfNotNull(
                        m.name,
                        m.startEpochDay?.let { "开学 ${LocalDate.ofEpochDay(it)}" },
                        m.totalWeeks?.let { "共 ${it} 周" }
                    )
                    if (bits.isNotEmpty()) Text(
                        "分享信息：" + bits.joinToString(" · ") + "（将写入学期设置）",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary
                    )
                }
                if (text.isNotBlank()) {
                    val badCount = parsed.badLines.size
                    Text(
                        "识别到 ${parsed.courses.size} 门课" + if (badCount > 0) "，$badCount 行无法识别" else "",
                        fontSize = 13.sp,
                        color = if (parsed.courses.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    if (parsed.courses.isNotEmpty()) {
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 150.dp)) {
                            items(parsed.courses) { c ->
                                val wt = when (c.weekType) {
                                    WeekType.ODD -> "单"
                                    WeekType.EVEN -> "双"
                                    WeekType.ALL -> ""
                                }
                                Text(
                                    "${dayNames[c.weekday - 1]} ${c.startPeriod}-${c.endPeriod}节 · ${c.startWeek}-${c.endWeek}$wt 周 · ${c.name}",
                                    fontSize = 11.sp, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    parsed.badLines.take(3).forEach {
                        Text(
                            "无法识别：$it", fontSize = 11.sp, color = MaterialTheme.colorScheme.error,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = importName.isNotBlank() && parsed.courses.isNotEmpty(),
                onClick = {
                    vm.importFromText(text, importName, importIntoNew) { msg ->
                        onResult(msg)
                        if (msg?.startsWith("导入成功") == true) onDismiss()
                    }
                }
            ) { Text("导入") }
        },
        dismissButton = { TextButton(onDismiss) { Text("取消") } }
    )
}

@Composable
fun WeekGrid(
    // 课程 + 显示列（1..7）：通常列=课程自己的星期；调休补课日显示来源日的课时列=当天
    periods: List<Period>, courses: List<Pair<Course, Int>>, colorOf: (Course) -> Color, onTap: (Course, Int) -> Unit,
    today: LocalDate, dates: List<LocalDate>?,
    marks: List<String?> = emptyList(),   // 与 dates 对齐的「休/班」角标（null=普通日）
    modifier: Modifier = Modifier,
    emptySel: Pair<Int, Int>? = null,
    copied: Course? = null,
    muted: Set<Long> = emptySet(),
    onCellTap: (Int, Int) -> Unit = { _, _ -> },
    onAddAt: (Int, Int) -> Unit = { _, _ -> },
    onPasteAt: (Int, Int) -> Unit = { _, _ -> }
) {
    // 机型适配：行高=可用高度/一屏节次数——首屏固定容纳 1-10 节，节数再多（11+）也不压扁色块，
    // 下滑查看余下节次；节数不足 10 时仍按实际节数铺满一屏。低过可读下限才回落到下限（整屏内部滚动）。
    // 下限随 fontScale 抬高（不同 ROM 默认字体缩放不同，大字体需要更高行），上限避免平板行高过大。
    val fontScale = LocalDensity.current.fontScale
    val timeW = 38.dp   // 时间列够放「08:00」即可：窄屏把宽度让给课程格
    val minRowH = (44.dp * fontScale).coerceAtMost(72.dp)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(timeW), contentAlignment = Alignment.Center) {
                dates?.let { Text("${it[0].monthValue}月", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            dayNames.forEachIndexed { i, d ->
                val date = dates?.getOrNull(i)
                val isToday = date != null && date == today
                val mark = marks.getOrNull(i)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (date != null || mark != null) {
                        // 休/班 跟日期同一行（写在日期后面）：单独一行会把表头顶高、挤占课表区
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            if (date != null) {
                                Box(
                                    Modifier.size(24.dp)
                                        .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("${date.dayOfMonth}", fontSize = 12.sp, color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (mark != null) Text(
                                mark, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = if (mark == "休") MaterialTheme.colorScheme.error else Color(0xFFEF6C00),
                                modifier = Modifier.padding(start = 1.dp)
                            )
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val dayW = (maxWidth - timeW) / 7
            // 课程名字号按格宽分档：窄屏（360dp/格 <46dp）缩小一档，宽屏放大一档，
            // 避免「格子挤、字太大」或「格子大、字太小」的机型差异；
            // 门槛刻意晚一档：同样宽度下小字号能多显示几个字（用户要求"尽量多显示课程名"）。
            val nameSize = when {
                dayW < 46.dp -> 11.sp
                dayW < 52.dp -> 12.sp
                dayW < 64.dp -> 13.sp
                else -> 14.sp
            }
            // 窄格地点用小一号：房间号（如 S50607）在整行内不被拦腰折断
            val locSize = if (dayW < 50.dp) 9.sp else 10.sp
            val rowH = (maxHeight / minOf(periods.size, 10).coerceAtLeast(1)).coerceIn(minRowH, 96.dp)
            val totalH = rowH * periods.size
            val scrollState = rememberScrollState()
            val scrollMod = if (totalH > maxHeight) Modifier.verticalScroll(scrollState) else Modifier
            val density = LocalDensity.current
            val cellTap by rememberUpdatedState(onCellTap)
            Box(Modifier.fillMaxWidth().then(scrollMod)) {
                Box(
                    Modifier.fillMaxWidth().height(totalH)
                        .pointerInput(Unit) {
                            detectTapGestures { off ->
                                val x = with(density) { off.x.toDp() }
                                val y = with(density) { off.y.toDp() }
                                val col = ((x - timeW) / dayW).toInt()
                                val row = (y / rowH).toInt()
                                if (col in 0..6 && row in periods.indices) cellTap(col + 1, row + 1)
                            }
                        }
                ) {
                    // 左侧时间：节号 / 开始(加粗) / 结束 三行，行内居中。
                    // 开始加深与结束区分——上一行结束(14:45)与下一行开始(14:55)字形相近，
                    // 同灰度时看着像"时间叠在一起"；加节号行隔断 + 字重差异后各归各row。
                    periods.forEachIndexed { i, per ->
                        Column(
                            Modifier.offset(x = 0.dp, y = rowH * i).width(timeW).height(rowH).padding(top = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("${i + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(PeriodTable.minuteToText(per.startMin), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(PeriodTable.minuteToText(per.endMin), fontSize = 9.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                        }
                    }
                    // 课程块（跨小节显示）：内容按块高算满——课程名优先占行，其余给「不静音/地点/教师」，
                    // 放得下就分行显示、放不下合并成一行（借鉴 shiguangschedule：显式 lineHeight + 按块高定行数）。
                    // 内容短时垂直居中，不留上下空档。
                    courses.forEach { (c, wd) ->
                        val col = wd - 1
                        val rowIdx = c.startPeriod - 1
                        val span = c.endPeriod - c.startPeriod + 1
                        val locText = c.location?.trim()?.substringBefore(' ')
                        val metaItems = listOfNotNull(
                            if (c.id in muted) "不静音" else null,
                            locText,
                            c.teacher?.trim()?.takeIf { it.isNotEmpty() }
                        )
                        val nameLineH = nameSize.value * fontScale * 1.3f   // 与下方 lineHeight 同系数的 dp 行高
                        val metaLineH = locSize.value * fontScale * 1.4f
                        val usable = rowH.value * span - 6f                 // 扣上下内边距与余量
                        // 放得下 2 行课程名 + 全部信息行 → 分行细读；否则合并成一行，把高度让给课程名
                        val splitMeta = metaItems.size >= 2 && usable - 2f * nameLineH >= metaItems.size * metaLineH
                        val metaRows = when {
                            metaItems.isEmpty() -> emptyList()
                            splitMeta -> metaItems
                            else -> listOf(metaItems.joinToString(" "))
                        }
                        val nameLines = ((usable - metaRows.size * metaLineH) / nameLineH).toInt().coerceAtLeast(1)
                        Box(
                            Modifier.offset(x = timeW + dayW * col, y = rowH * rowIdx).size(dayW, rowH * span)
                                .padding(1.dp)   // 色块间 1px 间隔
                                .clip(MaterialTheme.shapes.small)
                                .background(colorOf(c).copy(alpha = 0.92f))
                                .clickable { onTap(c, wd) }.padding(horizontal = 3.dp, vertical = 2.dp)   // 横向收窄让位给课程名
                        ) {
                            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                                Text(
                                    c.name, fontSize = nameSize, color = Color.White, fontWeight = FontWeight.SemiBold,
                                    lineHeight = (nameSize.value * 1.3f).sp,
                                    maxLines = nameLines,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                metaRows.forEach { m ->
                                    Text(
                                        m, fontSize = locSize, color = Color.White.copy(alpha = 0.85f),
                                        lineHeight = (locSize.value * 1.4f).sp,
                                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    // 空位弹层：与课表色块完全同款（整格大小、同圆角、同 1px 间隔）
                    val sel = emptySel
                    if (sel != null && courses.none { (c, wd) -> wd == sel.first && c.startPeriod <= sel.second && sel.second <= c.endPeriod }) {
                        val col = sel.first - 1
                        val rowIdx = sel.second - 1
                        Box(
                            Modifier.offset(x = timeW + dayW * col, y = rowH * rowIdx).size(dayW, rowH)
                                .padding(1.dp)
                                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                                .clickable { onAddAt(sel.first, sel.second) },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp)) }
                        if (copied != null) {
                            // 粘贴块放下一格；最后一行则放上一格
                            val prow = if (rowIdx + 1 < periods.size) rowIdx + 1 else rowIdx - 1
                            if (prow >= 0) {
                                Box(
                                    Modifier.offset(x = timeW + dayW * col, y = rowH * prow).size(dayW, rowH)
                                        .padding(1.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                                        .clickable { onPasteAt(sel.first, sel.second) },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.ContentCopy, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CourseDetailSheet(
    c: Course, periods: List<Period>,
    onEdit: () -> Unit, onDelete: () -> Unit, onCopy: () -> Unit,
    onDnd: () -> Unit
) {
    val muted = c.dndPolicy == DndPolicy.OFF
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onCopy) { Text("复制") }
            TextButton(onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
            TextButton(onEdit) { Text("编辑") }
        }
        HorizontalDivider()
        InfoRow(Icons.Rounded.DateRange, "第${c.startWeek}~${c.endWeek}周 · ${weekTypeNames[c.weekType]}")
        InfoRow(Icons.Rounded.NotificationsActive, "第${c.startPeriod}~${c.endPeriod}节  ${courseTimeText(c, periods)}")
        c.teacher?.let { InfoRow(Icons.Rounded.Person, it) }
        c.location?.let { InfoRow(Icons.Rounded.Place, it) }
        HorizontalDivider()
        // 单门课免打扰设置：整体策略 + 本节课/本周课/本学期课三级开关
        Row(Modifier.fillMaxWidth().clickable { onDnd() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (muted) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                null, Modifier.width(30.dp),
                tint = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                Text("自动免打扰", fontWeight = FontWeight.Medium)
                Text(dndSummary(c), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("设置 ›", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** 课程详情里的免打扰状态一句话。 */
fun dndSummary(c: Course): String = when (c.dndPolicy) {
    DndPolicy.OFF -> "已关闭（本节课不上课时静音）"
    DndPolicy.CUSTOM -> "单独方式：" + DndConfigCode.label(DndConfigCode.decode(c.dndCode) ?: DndConfig())
    DndPolicy.INHERIT -> "跟随设置里的免打扰方式"
}

@Composable
fun InfoRow(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.width(30.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, fontSize = 15.sp)
    }
}

