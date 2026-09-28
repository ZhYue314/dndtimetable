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

// ---------------- 运行自检 ----------------
/**
 * 排障页：权限/调度状态一眼可见，媒体能力与模拟上下课可现场验证——
 * 面向「静音没生效，不知道卡在哪一步」的用户（不必进开发者模式）。
 */
@Composable
fun DiagnoseScreen(vm: MainViewModel, onBack: () -> Unit) {
    val home by vm.homeStatus.collectAsState()
    val schedulingEnabled by vm.schedulingEnabled.collectAsState()
    val semester by vm.semester.collectAsState()
    val context = LocalContext.current
    var mediaResult by remember { mutableStateOf<String?>(null) }
    // 调试日志（AppLog）：档位持久化在 AppLog 自身，这里只做 UI 状态
    var logLevel by remember { mutableStateOf(AppLog.level) }
    var logSize by remember { mutableStateOf(AppLog.sizeBytes()) }
    var logMsg by remember { mutableStateOf<String?>(null) }
    val logLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) vm.exportLog(uri) { logMsg = it; logSize = AppLog.sizeBytes() }
    }
    val exactNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val exactGranted = AlarmScheduler.canScheduleExact(context)
    val notifNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notifGranted = !notifNeeded ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val family = detectRomFamily()
    var step = 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader("运行自检", onBack)
        Text("静音没生效时按顺序检查：权限 → 调度 → 手机实测。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PermissionStep(
            index = ++step, title = "通知使用权", required = true, granted = home.dndGranted,
            desc = "没有它不能自动免打扰/静音。", actionLabel = "去授权", onAction = { openDndSettings(context) }
        )
        if (exactNeeded) PermissionStep(
            index = ++step, title = "精确闹钟", required = true, granted = exactGranted,
            desc = "没有它上下课可能延迟数分钟。", actionLabel = "去开启", onAction = { requestExactAlarm(context) }
        )
        if (notifNeeded) PermissionStep(
            index = ++step, title = "通知权限", required = false, granted = notifGranted,
            desc = "没有它看不到状态通知与提醒（不影响静音）。", actionLabel = "去开启", onAction = { openAppNotificationSettings(context) }
        )
        PermissionStep(
            index = ++step, title = "后台保活", required = false, granted = null,
            desc = "${family.keepAliveHint}；系统无法检测，清后台后闹钟不触发多半是这项没开。",
            actionLabel = null, onAction = {},
            extra = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ openAutoStartSettings(context) }, Modifier.weight(1f)) { Text("设置自启动", maxLines = 1) }
                    OutlinedButton({ openBatterySettings(context) }, Modifier.weight(1f)) { Text("省电设置", maxLines = 1) }
                }
            }
        )
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("调度状态", fontWeight = FontWeight.SemiBold)
            Text(
                "总开关：${if (schedulingEnabled) "已开启" else "已关闭（不会自动静音）"}",
                fontSize = 13.sp,
                color = if (schedulingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text("下次事件：${home.nextText ?: "—"}", fontSize = 13.sp)
            val sem = semester
            if (sem != null) {
                val w = ScheduleEngine.weekNumber(sem, DevClock.today())
                Text("学期：第 ${if (w > 0) w else "—"} 周 / 共 ${sem.totalWeeks} 周", fontSize = 13.sp)
            } else {
                Text("尚未设置学期", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("手机实测", fontWeight = FontWeight.SemiBold)
            Text("「模拟上课」会按「设置 → 免打扰方式」立刻静音这台手机；点「结束模拟」恢复。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ vm.devStartCurrent() }, Modifier.weight(1f)) { Text("模拟上课") }
                OutlinedButton({ vm.endSessionNow() }, Modifier.weight(1f)) { Text("结束模拟") }
            }
            OutlinedButton({ vm.devMediaSelfCheck { mediaResult = it } }, Modifier.fillMaxWidth()) { Text("媒体屏蔽自检") }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("调试日志", fontWeight = FontWeight.SemiBold)
            Text("用于开发排障：开启后日志写入本机文件，导出发给作者即可定位问题。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(AppLog.Level.OFF to "关闭", AppLog.Level.ERR to "仅错误", AppLog.Level.ALL to "详细").forEachIndexed { i, (lv, label) ->
                    SegmentedButton(
                        selected = logLevel == lv,
                        onClick = { logLevel = lv; AppLog.setLevel(context, lv) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
            Text(
                if (logSize == 0L) "暂无日志" else "日志文件：${logSize / 1024} KB",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({
                    if (AppLog.sizeBytes() == 0L) logMsg = "暂无日志"
                    else logLauncher.launch("dndtimetable-log.txt")
                }, Modifier.weight(1f)) { Text("导出日志") }
                OutlinedButton({
                    AppLog.clear(); logSize = 0; logMsg = "日志已清空"
                }, Modifier.weight(1f)) { Text("清空日志") }
            }
            logMsg?.let {
                Text(it, fontSize = 12.sp, color = if (it.startsWith("日志已")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        } }
    }
    mediaResult?.let {
        AlertDialog(
            onDismissRequest = { mediaResult = null },
            title = { Text("媒体屏蔽自检结果") },
            text = { Text(it, fontSize = 12.sp) },
            confirmButton = { TextButton({ mediaResult = null }) { Text("知道了") } }
        )
    }
}

