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

// ---------------- 新手引导 ----------------
/** 分步引导页标题（免打扰方式走默认全开不入引导；桌面小组件与学期设置同页；内容少的页整体下移，操作区更靠近手指）。 */
val guideTitles = listOf("认识免打扰课表", "授予权限", "学期与小组件", "添加课表")

/** 首次启动自动展示（一页一步）；设置→「使用帮助」可再次查看。 */
@Composable
fun GuideScreen(
    vm: MainViewModel,
    page: Int,
    onPageChange: (Int) -> Unit,
    onDone: () -> Unit,
    onGotoCourses: () -> Unit,
    onAddCourses: () -> Unit
) {
    val home by vm.homeStatus.collectAsState()
    val courses by vm.courses.collectAsState()
    val context = LocalContext.current
    // 三项可检测权限（页 1 的「下一步」据此 gating；后台保活等不可检测项只显示不拦）。
    // 从系统设置返回时 MainActivity.onResume→vm.refresh() 会刷新 homeStatus 触发重组，状态自动更新。
    val exactNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val exactGranted = AlarmScheduler.canScheduleExact(context)
    val notifNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notifGranted = !notifNeeded ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val permsOk = home.dndGranted && (!exactNeeded || exactGranted) && (!notifNeeded || notifGranted)
    val safePage = page.coerceIn(0, guideTitles.lastIndex)   // 页数变化（合并页）后不越界
    // 学期页的保存函数（页 2 注册，点「下一步」时自动保存，忘记按「保存学期」也不丢日期）
    val semesterSave = remember { mutableStateOf<(() -> Boolean)?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("使用帮助", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${safePage + 1}/${guideTitles.size} · ${guideTitles[safePage]}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        // 进度条：已走过的步骤点亮
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(guideTitles.size) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).background(
                        if (i <= safePage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(2.dp)
                    )
                )
            }
        }
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = safePage,
                transitionSpec = {
                    val fwd = targetState > initialState
                    (slideInHorizontally { if (fwd) it else -it } togetherWith slideOutHorizontally { if (fwd) -it else it })
                },
                label = "guidePage",
                modifier = Modifier.fillMaxSize()
            ) { p ->
                when (p) {
                    0 -> GuideIntroPage()
                    1 -> GuidePermissionPage(vm, home.dndGranted, exactNeeded, exactGranted, notifNeeded, notifGranted)
                    2 -> GuideSemesterPage(vm, semesterSave)
                    else -> GuideCoursePage(courses.size, onGotoCourses)
                }
            }
        }
        // 底部操作：没有「跳过」——权限页必须三项可检测权限全授予才放行（「上一步」/系统返回不受限）；两键平分整行
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (safePage > 0) OutlinedButton({ onPageChange(safePage - 1) }, Modifier.weight(1f)) { Text("上一步", maxLines = 1) }
            else Spacer(Modifier.weight(1f))
            when {
                safePage < guideTitles.lastIndex -> Button(
                    onClick = {
                        // 学期页：没点「保存学期」也在这里兜底保存（总周数非法时不放行）
                        if (safePage != 2 || semesterSave.value?.invoke() != false) onPageChange(safePage + 1)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = safePage != 1 || permsOk
                ) { Text(if (safePage == 1 && !permsOk) "完成必授权限后继续" else "下一步", maxLines = 1) }
                courses.isNotEmpty() -> Button({ onDone() }, Modifier.weight(1f)) { Text("开始使用", maxLines = 1) }
                else -> Button({ onAddCourses() }, Modifier.weight(1f)) { Text("去添加课表", maxLines = 1) }
            }
        }
    }
}

/**
 * 引导页内容区：内容短的页整体垂直居中（操作区落在拇指更容易够到的位置），
 * 内容长的页（介绍/权限/添加课表）照常滚动。
 */
@Composable
fun GuidePage(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
    ) { content() }
}

/** 第一页：项目介绍与功能（不可跳过）。 */
@Composable
fun GuideIntroPage() {
    GuidePage {
        Text("「免打扰课表」按你的课表自动把手机调成静音/免打扰，下课自动恢复；课表数据只保存在本机，不联网上传。", fontSize = 14.sp)
        GuideItem(Icons.Rounded.NotificationsOff, "自动免打扰", "上课自动静音、下课恢复；首页按钮或通知栏可随时手动暂停。")
        GuideItem(Icons.Rounded.DateRange, "课表管理", "周网格查看课程，支持 AI 生成、Excel/CSV、文字导入与手动录入。")
        GuideItem(Icons.Rounded.NotificationsActive, "上下课提醒", "课前提醒、课间休息通知、上课期间常驻状态通知。")
        GuideItem(Icons.Rounded.Home, "桌面小组件", "把小组件放到桌面，不用打开 App 也能看到当前/下一节课。")
        Text("接下来 3 步准备好课表，大约 2 分钟。", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

/** 第二页：权限获取（三项可检测权限未授完时无法点「下一步」；全部权限在同一页，不再跳「权限管理」）。 */
@Composable
fun GuidePermissionPage(
    vm: MainViewModel,
    dndGranted: Boolean,
    exactNeeded: Boolean, exactGranted: Boolean,
    notifNeeded: Boolean, notifGranted: Boolean
) {
    val context = LocalContext.current
    val family = detectRomFamily()
    val statusNotif by vm.statusNotif.collectAsState()
    GuidePage {
        Text("这几项是自动免打扰的基础：点按钮直达本应用的系统设置页，打开开关后返回，状态会自动更新；全部开启后才能点「下一步」。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PermissionStep(
            index = 1, title = "通知使用权", required = true, granted = dndGranted,
            desc = "自动免打扰/静音的核心权限。在打开的页面中找到「免打扰课表」，打开开关。",
            actionLabel = "去授权", onAction = { openDndSettings(context) }
        )
        if (exactNeeded) PermissionStep(
            index = 2, title = "精确闹钟", required = true, granted = exactGranted,
            desc = "上下课准点触发（未开启可能延迟数分钟）。打开页面里的「允许设置闹钟和提醒」开关。",
            actionLabel = "去开启", onAction = { requestExactAlarm(context) }
        )
        if (notifNeeded) PermissionStep(
            index = 3, title = "通知权限", required = true, granted = notifGranted,
            desc = "常驻状态通知与上课提醒。打开页面顶部的「允许通知」开关。",
            actionLabel = "去开启", onAction = { openAppNotificationSettings(context) }
        )
        PermissionStep(
            index = 4, title = "后台保活", required = false, granted = null,
            desc = "${family.keepAliveHint}；否则清理后台后闹钟可能不触发。",
            actionLabel = null, onAction = {},
            extra = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ openAutoStartSettings(context) }, Modifier.weight(1f)) { Text("设置自启动", maxLines = 1) }
                    OutlinedButton({ openBatterySettings(context) }, Modifier.weight(1f)) { Text("省电设置", maxLines = 1) }
                }
            }
        )
        Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("常驻状态通知", fontWeight = FontWeight.SemiBold)
                Text("在通知栏常驻显示当前状态与下次触发", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = statusNotif, onCheckedChange = { vm.setStatusNotif(it) })
        } }
        WidgetPermCard()
    }
}

/** 第三页：学期开始日期 + 桌面小组件（小组件卡片跟在学期设置后面）。直接选开学日期 / 由「本周第几周」推算（保存时统一归一到周一）。 */
@Composable
fun GuideSemesterPage(vm: MainViewModel, saveHolder: MutableState<(() -> Boolean)?>) {
    val semester by vm.semester.collectAsState()
    val context = LocalContext.current
    var mode by remember { mutableStateOf(0) }   // 0=知道开学日期 1=只知道当前第几周
    var startDate by remember(semester?.id) { mutableStateOf(semester?.startEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()) }
    var weeks by remember(semester?.id) { mutableStateOf(semester?.totalWeeks?.toString() ?: "20") }
    var knownWeek by remember { mutableStateOf(1) }
    var msg by remember { mutableStateOf<String?>(null) }
    val derived = ScheduleEngine.semesterStartFromWeek(DevClock.today(), knownWeek)
    // 保存（返回 false=总周数非法，调用方不放行）；每次重组后重新注册，捕获的始终是当前输入
    fun save(): Boolean {
        val w = weeks.toIntOrNull()
        if (w == null || w < 1) { msg = "总周数无效"; return false }
        val anchor = ScheduleEngine.anchorStart(if (mode == 0) startDate else ScheduleEngine.semesterStartFromWeek(DevClock.today(), knownWeek))
        vm.saveSemester(anchor.toEpochDay(), w)   // 成功不提示：此处提示会让卡片变高，翻页跳动/卡顿
        return true
    }
    SideEffect { saveHolder.value = { save() } }
    GuidePage {
        // 与设置→「课表设置」同款卡片：无背景色时与小组件卡/页面底色连成一片，不好分辨
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("学期开始日期", fontWeight = FontWeight.SemiBold)
            Text("第 1 周的起始日，用于推算周次（单双周）与节假日。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            semester?.let {
                Text(
                    "当前学期：${LocalDate.ofEpochDay(it.startEpochDay)}（周一）起，共 ${it.totalWeeks} 周",
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = mode == 0, onClick = { mode = 0; msg = null }, shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)) { Text("知道开学日期", fontSize = 12.sp) }
                SegmentedButton(selected = mode == 1, onClick = { mode = 1; msg = null }, shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)) { Text("知道当前第几周", fontSize = 12.sp) }
            }
            if (mode == 0) {
                Text("选择第 1 周的日期，保存时归一到周一。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DatePickerField("选择开学日期", startDate, { startDate = it; msg = null }, Modifier.fillMaxWidth())
            } else {
                Text("按「本周一 = 第 1 天」推算开学日期。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Stepper(knownWeek, { knownWeek = it; msg = null }, min = 1, max = 30, step = 1, label = { "本周是第 $it 周" })
                Text("推算开学日期：$derived（周一）", fontWeight = FontWeight.SemiBold)
            }
            OutlinedTextField(value = weeks, onValueChange = { weeks = it.filter(Char::isDigit); msg = null }, label = { Text("总周数") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button({ save() }, Modifier.fillMaxWidth()) { Text("保存学期") }
            // 成功不提示（自动保存静默），仅总周数非法时报错
            msg?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("桌面小组件", fontWeight = FontWeight.SemiBold)
            Text("不用打开 App 就能看到当前课程和下一节课，可自由拉伸大小。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ requestPinWidget(context) }, Modifier.fillMaxWidth()) { Text("添加到桌面") }
            Text("部分桌面（澎湃/ColorOS 等）需先授予「桌面快捷方式」权限；桌面没反应时会自动跳到授权页，也可在设置→权限管理中打开。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}

/** 第四页：添加课表；已有课程则显示完成状态。 */
@Composable
fun GuideCoursePage(courseCount: Int, onGotoCourses: () -> Unit) {
    GuidePage {
        if (courseCount > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("已添加 $courseCount 门课程", fontWeight = FontWeight.SemiBold)
            }
            Text("可以继续在课表页添加/修改课程，也可以直接开始使用。建议回到首页确认「总开关」已打开。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onGotoCourses, Modifier.fillMaxWidth()) { Text("回到课表页") }
        } else {
            Text("最后一步：把课程加进课表。课表页右上「＋」有以下方式：", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            GuideItem(Icons.Rounded.Person, "手动添加", "课程少时逐门录入；第一次使用推荐先试这个。")
            GuideItem(Icons.Rounded.DateRange, "Excel/CSV 导入", "教务系统导出的课表文件（.xls/.xlsx/.csv）可直接导入。")
            GuideItem(Icons.Rounded.Place, "教务网页导入", "在 App 内登录教务系统，进入课表页面一键抓取导入。")
            GuideItem(Icons.Rounded.ContentCopy, "文字导入", "粘贴大模型生成的文字，或同学分享的课表文字。")
            GuideItem(Icons.Rounded.Add, "用 AI 生成", "复制提示词给豆包等大模型（附课表截图），把生成结果粘贴或选文件导回。")
        }
    }
}

/** 引导说明卡：图标 + 标题 + 说明（无操作）。 */
@Composable
fun GuideItem(icon: ImageVector, title: String, desc: String) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } }
}

