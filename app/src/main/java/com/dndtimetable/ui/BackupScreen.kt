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

// ---------------- 备份与恢复 ----------------
/**
 * 全量备份/恢复（JSON，v1）：换机迁移用。
 * 导入沿用「先预览后写库」原则：解析成功先弹确认（显示将覆盖的内容量），确认才在事务里覆盖。
 */
@Composable
fun BackupScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var msg by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BackupCodec.Snapshot?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) vm.exportBackup(uri) { msg = it }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            msg = null
            vm.previewBackup(context, uri) { snap, err -> if (snap != null) preview = snap else msg = err }
        }
    }
    val icsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri: Uri? ->
        if (uri != null) vm.exportIcs(uri) { msg = it }
    }
    var calendarChoices by remember { mutableStateOf<List<CalendarImport.Cal>?>(null) }
    val remindMin by vm.noticeLeadMin.collectAsState()
    fun proceedCalendarImport() {
        vm.loadCalendars { cals ->
            if (cals.isEmpty()) msg = "没有可写的日历：先在系统「日历」里新建本地日历"
            else calendarChoices = cals
        }
    }
    val calendarPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.READ_CALENDAR] == true && result[Manifest.permission.WRITE_CALENDAR] == true) proceedCalendarImport()
        else msg = "没有日历权限，可改用「导出 .ics」"
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader("备份与恢复", onBack)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("备份包含", fontWeight = FontWeight.SemiBold)
            Text("全部课表、课程、特殊日期（放假/调休）、单课免打扰、节次时间表；保存在所选位置，不上传。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ exportLauncher.launch("dndtimetable备份_${DevClock.today()}.json") }, Modifier.fillMaxWidth()) { Text("导出备份文件") }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("恢复", fontWeight = FontWeight.SemiBold)
            Text("选择备份文件；恢复会覆盖当前全部数据，不可撤销。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ importLauncher.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("导入备份文件") }
        } }
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("导入系统日历", fontWeight = FontWeight.SemiBold)
            Text(
                "一键写入系统日历（含放假跳过与调休补课的实际课程），不开 App 也能看课表" +
                    (if (remindMin > 0) "；每节提前 $remindMin 分钟提醒" else "") +
                    "。重复导入会替换上次的，可随时移除。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({
                    if (CalendarImport.hasPermission(context)) proceedCalendarImport()
                    else calendarPermLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                }, Modifier.weight(1f)) { Text("导入系统日历") }
                OutlinedButton({ vm.removeFromCalendar { msg = it } }, Modifier.weight(1f)) { Text("从日历移除") }
            }
            OutlinedButton({ icsLauncher.launch("dndtimetable课表_${DevClock.today()}.ics") }, Modifier.fillMaxWidth()) { Text("导出 .ics 文件（给其他设备/同学）") }
        } }
        msg?.let { Text(it, fontSize = 13.sp, color = if (it.startsWith("已")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
    }
    calendarChoices?.let { cals ->
        AlertDialog(
            onDismissRequest = { calendarChoices = null },
            title = { Text("写入哪个日历") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    cals.forEach { c ->
                        TextButton(
                            {
                                calendarChoices = null
                                vm.importToCalendar(c.id, remindMin) { msg = it }
                            },
                            Modifier.fillMaxWidth()
                        ) { Text(if (c.account.isBlank()) c.name else "${c.name}（${c.account}）", maxLines = 1) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ calendarChoices = null }) { Text("取消") } }
        )
    }
    preview?.let { snap ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("恢复备份") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("导入后当前全部数据将被替换（不可撤销）：", fontSize = 13.sp)
                Text(
                    "课表 ${snap.schedules.size} 个 · 课程 ${snap.courses.size} 门 · 特殊日期 ${snap.specials.size} 条 · 免打扰规则 ${snap.rules.size} 条",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            } },
            confirmButton = { TextButton({ preview = null; vm.applyBackup(snap) { msg = it } }) { Text("覆盖恢复") } },
            dismissButton = { TextButton({ preview = null }) { Text("取消") } }
        )
    }
}

