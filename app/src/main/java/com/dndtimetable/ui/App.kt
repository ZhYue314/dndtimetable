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

sealed class Screen {
    object Home : Screen()
    object Courses : Screen()
    object Settings : Screen()
    object DndSettings : Screen()
    object Semester : Screen()
    object SpecialDates : Screen()
    object Permissions : Screen()
    object Notifications : Screen()
    object About : Screen()
    object Guide : Screen()
    object Backup : Screen()
    object Diagnose : Screen()
    object WebImport : Screen()
    data class CourseEdit(val course: Course?) : Screen()
}

val dayNames = listOf("一", "二", "三", "四", "五", "六", "日")
val weekTypeNames = mapOf(WeekType.ALL to "每周", WeekType.ODD to "单周", WeekType.EVEN to "双周")
/** 与 domain.CourseColors 同款色板（Compose Color 视图）。 */
val palette: List<Color> = com.dndtimetable.domain.CourseColors.palette.map { Color(it) }

/** 课程复制剪贴板（内存态，跨页面共享）。 */
var clipboardCourse by mutableStateOf<Course?>(null)

@Composable
fun App(
    activity: MainActivity,
    vm: MainViewModel,
    sharedFile: Uri? = null,
    sharedText: String? = null,
    sharedImage: Boolean = false,
    onShareConsumed: () -> Unit = {}
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var semesterFrom by remember { mutableStateOf<Screen>(Screen.Home) }   // 课表设置页返回目标
    var specialDatesFrom by remember { mutableStateOf<Screen>(Screen.Settings) }   // 假期设置页返回目标
    var coursesFrom by remember { mutableStateOf<Screen>(Screen.Home) }   // 课表页返回目标
    var permissionsFrom by remember { mutableStateOf<Screen>(Screen.About) }   // 权限页返回目标
    var guideFrom by remember { mutableStateOf<Screen>(Screen.Home) }   // 引导页返回目标
    // 「我的课表 → 添加」：跳到课表页并自动弹「添加内容」菜单（完整选项，含手动添加）
    var pendingAddMenu by remember { mutableStateOf(false) }
    var guidePage by remember { mutableStateOf(0) }   // 引导页当前步骤：去课表添加后返回时停在原步骤
    val settingsScroll = rememberScrollState()   // 设置页滚动位置：跨二级页返回时保持
    val guideSeen by vm.guideSeen.collectAsState()
    val appContext = LocalContext.current
    var crashText by remember { mutableStateOf<String?>(null) }
    // 上次崩溃留的日志：启动弹一次，复制或关闭即删除
    LaunchedEffect(Unit) { crashText = CrashLog.read(appContext) }
    // 首次启动（未看过引导）自动进入新手引导；已看过则保持当前页面
    LaunchedEffect(guideSeen) { if (!guideSeen) screen = Screen.Guide }
    // 过完引导 = 走到最后一页（第 4 页）：下次启动不再自动弹；中途返回/退出不标记（下次继续弹）。
    // 手动从设置进「使用帮助」也走同一标记，但它本来就是已完成引导才会到达的入口，无副作用。
    LaunchedEffect(guidePage) { if (guidePage == guideTitles.lastIndex) vm.setGuideSeen(true) }
    // 其他 App 分享进来：直接到课表页处理导入
    LaunchedEffect(sharedFile, sharedText, sharedImage) {
        if (sharedFile != null || sharedText != null || sharedImage) {
            coursesFrom = Screen.Home
            screen = Screen.Courses
        }
    }
    // 系统返回：侧滑返回 / 返回键 按页面层级返回
    when (val s = screen) {
        is Screen.CourseEdit -> androidx.activity.compose.BackHandler { screen = Screen.Courses }
        is Screen.Semester -> androidx.activity.compose.BackHandler { screen = semesterFrom }
        is Screen.SpecialDates -> androidx.activity.compose.BackHandler { screen = specialDatesFrom }
        is Screen.Permissions -> androidx.activity.compose.BackHandler { screen = permissionsFrom }
        is Screen.Notifications -> androidx.activity.compose.BackHandler { screen = Screen.Settings }
        is Screen.About -> androidx.activity.compose.BackHandler { screen = Screen.Settings }
        is Screen.Backup -> androidx.activity.compose.BackHandler { screen = Screen.Settings }
        is Screen.Diagnose -> androidx.activity.compose.BackHandler { screen = Screen.Settings }
        is Screen.WebImport -> androidx.activity.compose.BackHandler { screen = Screen.Courses }
        is Screen.Courses -> androidx.activity.compose.BackHandler { screen = coursesFrom }
        is Screen.Settings -> androidx.activity.compose.BackHandler { screen = Screen.Home }
    is Screen.DndSettings -> androidx.activity.compose.BackHandler { screen = Screen.Settings }
        is Screen.Guide -> androidx.activity.compose.BackHandler {
            screen = guideFrom   // 中途返回不标记完成：下次启动继续弹（走到最后一页才标记）
        }
        else -> {}
    }
    Scaffold(
        bottomBar = {
            if (screen is Screen.Home || screen is Screen.Courses || screen is Screen.Settings) {
                NavigationBar(
                    modifier = Modifier.height(56.dp),   // 默认 80dp+系统手势条内边距，太高把课表挤小
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    NavigationBarItem(selected = screen == Screen.Home, onClick = { screen = Screen.Home }, icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("首页") })
                    NavigationBarItem(selected = screen == Screen.Courses, onClick = { screen = Screen.Courses; coursesFrom = Screen.Home }, icon = { Icon(Icons.Rounded.DateRange, null) }, label = { Text("课表") })
                    NavigationBarItem(selected = screen == Screen.Settings, onClick = { screen = Screen.Settings }, icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("设置") })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = screen) {
                is Screen.Home -> HomeScreen(vm, onGotoSemester = { semesterFrom = Screen.Home; screen = Screen.Semester }, onGotoPermissions = { permissionsFrom = Screen.Home; screen = Screen.Permissions }, onGotoCourses = { coursesFrom = Screen.Home; pendingAddMenu = true; screen = Screen.Courses })
                is Screen.Courses -> CourseGridScreen(
                    vm,
                    onAdd = { screen = Screen.CourseEdit(it) },
                    onEdit = { screen = Screen.CourseEdit(it) },
                    onSemesterSettings = { semesterFrom = Screen.Courses; screen = Screen.Semester },
                    onWebImport = { screen = Screen.WebImport },
                    pendingAddMenu = pendingAddMenu,
                    onAddMenuConsumed = { pendingAddMenu = false },
                    sharedFile = sharedFile,
                    sharedText = sharedText,
                    sharedImage = sharedImage,
                    onSharedConsumed = onShareConsumed
                )
                is Screen.CourseEdit -> CourseEditScreen(vm, s.course, onBack = { screen = Screen.Courses })
                is Screen.Settings -> SettingsScreen(
                    vm,
                    onSemesterSettings = { semesterFrom = Screen.Settings; screen = Screen.Semester },
                    onSpecialDates = { specialDatesFrom = Screen.Settings; screen = Screen.SpecialDates },
                    onDndSettings = { screen = Screen.DndSettings },
                    onNotifications = { screen = Screen.Notifications },
                    onAbout = { screen = Screen.About },
                    onGuide = { guideFrom = Screen.Settings; guidePage = 0; screen = Screen.Guide },
                    onBackup = { screen = Screen.Backup },
                    onDiagnose = { screen = Screen.Diagnose },
                    scrollState = settingsScroll
                )
                is Screen.DndSettings -> DndSettingsScreen(vm, onBack = { screen = Screen.Settings })
                is Screen.Semester -> SemesterScreen(
                    vm,
                    onBack = { screen = semesterFrom },
                    onSpecialDates = { specialDatesFrom = Screen.Semester; screen = Screen.SpecialDates },
                    onAddSchedule = { coursesFrom = Screen.Semester; pendingAddMenu = true; screen = Screen.Courses }
                )
                is Screen.SpecialDates -> SpecialDatesScreen(vm, onBack = { screen = specialDatesFrom })
                is Screen.Notifications -> NotificationsScreen(vm, onBack = { screen = Screen.Settings })
                is Screen.About -> AboutScreen(vm, onBack = { screen = Screen.Settings }, onPermissions = { permissionsFrom = Screen.About; screen = Screen.Permissions })
                is Screen.Permissions -> PermissionsScreen(vm, onBack = { screen = permissionsFrom })
                is Screen.Backup -> BackupScreen(vm, onBack = { screen = Screen.Settings })
                is Screen.Diagnose -> DiagnoseScreen(vm, onBack = { screen = Screen.Settings })
                is Screen.WebImport -> WebImportScreen(vm, onBack = { screen = Screen.Courses })
                is Screen.Guide -> GuideScreen(
                    vm,
                    page = guidePage,
                    onPageChange = { guidePage = it },
                    onDone = { screen = guideFrom },
                    // 引导完成标记统一在「走到最后一页」时写（见上方 LaunchedEffect(guidePage)）
                    onGotoCourses = { coursesFrom = Screen.Guide; screen = Screen.Courses },
                    // 末页「去添加课表」（还没有课程时）：同上 + 自动弹「添加内容」菜单
                    onAddCourses = { coursesFrom = Screen.Guide; pendingAddMenu = true; screen = Screen.Courses }
                )
            }
        }
    }

    crashText?.let { text ->
        AlertDialog(
            onDismissRequest = { CrashLog.clear(appContext); crashText = null },
            title = { Text("上次运行异常退出") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("复制下面的信息发给作者可帮忙定位；关闭后不再提示。", fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(text.take(6000), fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton({
                    val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("dndtimetable crash", text))
                    CrashLog.clear(appContext); crashText = null
                }) { Text("复制并关闭") }
            },
            dismissButton = { TextButton({ CrashLog.clear(appContext); crashText = null }) { Text("关闭") } }
        )
    }
}
