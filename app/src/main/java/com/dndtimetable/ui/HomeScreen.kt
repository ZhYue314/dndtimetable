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

// ---------------- 首页 ----------------
@Composable
fun HomeScreen(vm: MainViewModel, onGotoSemester: () -> Unit, onGotoPermissions: () -> Unit, onGotoCourses: () -> Unit) {
    val home by vm.homeStatus.collectAsState()
    val dndOn by vm.dndOn.collectAsState()
    val periods by vm.periods.collectAsState()
    val courses by vm.courses.collectAsState()
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var nowMs by remember { mutableStateOf(DevClock.nowMs()) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("免打扰课表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            // 数据未就绪时整页留白，避免"下次：—/课程列表空/未添加课表"等空状态整块闪入再被真实数据覆盖
            if (home.loaded) {
                banner(!home.dndGranted, "未授予通知使用权，无法自动免打扰", onGotoPermissions, "去授权")
                banner(!AlarmScheduler.canScheduleExact(context), "未开启精确闹钟，上下课可能延迟数分钟", onGotoPermissions, "去开启")
                banner(!home.hasSemester, "请先设置学期开始日期与总周数", onGotoSemester, "设置学期")

            val heroBg = if (dndOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            val heroOn = if (dndOn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            val heroDim = if (dndOn) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = heroBg)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (dndOn) Icons.Rounded.NotificationsOff else Icons.Rounded.Notifications, null, Modifier.size(22.dp), tint = if (dndOn) heroOn else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(if (dndOn) "静音中" else "正常模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = heroOn)
                        Spacer(Modifier.weight(1f))
                        Text("下次：${home.nextText ?: "—"}", fontSize = 12.sp, color = heroDim, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text("当前课程", color = heroDim, fontSize = 13.sp)
                    Text(home.currentCourse ?: "无课中", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = heroOn)
                }
            }

            val byCourse = home.todayCourses.map { c -> c to isCourseNow(c, periods, nowMs) }
            val upcoming = byCourse.filter { it.second != CourseWhen.PAST }.sortedBy { it.first.startPeriod }
            val past = byCourse.filter { it.second == CourseWhen.PAST }.sortedBy { it.first.startPeriod }
            val allDone = home.todayCourses.isNotEmpty() && past.size == home.todayCourses.size

            val todayBlock: @Composable () -> Unit = {
                Text("今日课程（${home.todayCourses.size}）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (home.todayCourses.isEmpty()) {
                    Text("今天没有课。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    (upcoming + past).forEach { (c, when_) -> CourseCard(c, when_, periods) }
                }
            }
            val tomorrowBlock: @Composable () -> Unit = {
                Text("明日课程（${home.tomorrowCourses.size}）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (home.tomorrowCourses.isEmpty()) {
                    Text("明天没有课。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    home.tomorrowCourses.forEach { c -> CourseCard(c, null, periods) }
                }
            }
            if (courses.none { it.enabled }) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("还没有课程", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("添加或导入课表后，才会按上课时间自动免打扰。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        // 单个按钮靠右（项目书 §5.9）
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Button(onGotoCourses) { Text("去添加课表") }
                        }
                    }
                }
            } else {
                if (allDone) { tomorrowBlock(); todayBlock() } else { todayBlock(); tomorrowBlock() }
            }
            }
        }

        FloatingActionButton(onClick = { vm.toggleDndNow(); nowMs = DevClock.nowMs() }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Icon(if (dndOn) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, contentDescription = null)
        }
    }
}

/** 同课同色；不同课尽量不同色；相邻（同天上下相触或相邻天同节次）绝不相同。（与小组件共用 domain.CourseColors） */
fun assignColors(courses: List<Course>): Map<String, Color> =
    com.dndtimetable.domain.CourseColors.assign(courses).mapValues { Color(it.value) }

enum class CourseWhen { UPCOMING, CURRENT, PAST }

fun isCourseNow(c: Course, periods: List<Period>, nowMs: Long): CourseWhen {
    val zone = ZoneId.systemDefault()
    val today = DevClock.today(zone)
    val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
    val s = dayStart + (periods.getOrNull(c.startPeriod - 1)?.startMin ?: 0) * 60000L
    val e = dayStart + (periods.getOrNull(c.endPeriod - 1)?.endMin ?: 0) * 60000L
    return when {
        nowMs >= e -> CourseWhen.PAST
        nowMs >= s -> CourseWhen.CURRENT
        else -> CourseWhen.UPCOMING
    }
}

/** when_=null 表示非当日（如明日课程），不显示上课中/已上完状态。 */
@Composable
fun CourseCard(c: Course, when_: CourseWhen?, periods: List<Period>) {
    val gray = when_ == CourseWhen.PAST
    val live = when_ == CourseWhen.CURRENT
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                live -> MaterialTheme.colorScheme.primaryContainer
                gray -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontWeight = FontWeight.SemiBold, color = if (gray) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (live) {
                    Icon(Icons.Rounded.NotificationsOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(4.dp))
                    Text("上课中", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                } else if (gray) Text("已上完", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${courseTimeText(c, periods)} · 第${c.startWeek}~${c.endWeek}周 · ${weekTypeNames[c.weekType]}",
                color = if (live) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
            if (c.teacher != null || c.location != null) {
                Text(
                    "${c.teacher ?: ""}${if (c.teacher != null && c.location != null) " · " else ""}${c.location ?: ""}",
                    color = if (live) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun banner(show: Boolean, text: String, action: () -> Unit, label: String) {
    if (!show) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(text, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            TextButton(action) { Text(label) }
        }
    }
}

