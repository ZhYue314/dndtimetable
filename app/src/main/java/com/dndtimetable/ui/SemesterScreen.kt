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

// ---------------- 课表设置 ----------------
@Composable
fun SemesterScreen(vm: MainViewModel, onBack: () -> Unit, onSpecialDates: () -> Unit, onAddSchedule: () -> Unit) {
    val semester by vm.semester.collectAsState()
    val schedules by vm.schedules.collectAsState()
    val activeScheduleId by vm.activeScheduleId.collectAsState()
    val context = LocalContext.current
    var startDate by remember(semester?.id) { mutableStateOf(semester?.startEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()) }
    var weeks by remember(semester?.id) { mutableStateOf(semester?.totalWeeks?.toString() ?: "20") }
    var mode by remember { mutableStateOf(0) }   // 0=知道开学日期 1=只知道当前第几周（与引导页同布局）
    val curWeek = semester?.let { ScheduleEngine.weekNumber(it, DevClock.today()) } ?: 0
    var knownWeek by remember { mutableStateOf(if (curWeek > 0) curWeek else 1) }
    val derived = ScheduleEngine.semesterStartFromWeek(DevClock.today(), knownWeek)
    var msg by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Semester?>(null) }
    // 分享文字（行内「分享」按钮触发）
    var showShare by remember { mutableStateOf(false) }
    var shareText by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppHeader("课表设置", onBack)
            Spacer(Modifier.weight(1f))
            TextButton(onSpecialDates, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("假期设置", fontSize = 13.sp, maxLines = 1) }
        }

        // 与新手引导「设置学期」同一布局：知道开学日期 / 知道当前第几周 两种入口合一
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("学期开始日期", fontWeight = FontWeight.SemiBold)
            Text("第 1 周的起始日，用于推算周次与节假日；保存时自动归一到周一。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            semester?.let { Text("当前：${anchorText(LocalDate.ofEpochDay(it.startEpochDay))} 起共 ${it.totalWeeks} 周", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
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
            Button({
                val w = weeks.toIntOrNull()
                if (w == null || w < 1) { msg = "总周数无效"; return@Button }
                val anchor = ScheduleEngine.anchorStart(if (mode == 0) startDate else derived)
                vm.saveSemester(anchor.toEpochDay(), w)
                msg = "已保存：$anchor 起共 $w 周"
            }, Modifier.fillMaxWidth()) { Text("保存学期") }
            msg?.let { m -> Text(m, fontSize = 13.sp, color = if (m.startsWith("已保存")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        } }

        // 课表列表：单选切换 + 分享/改名/删除
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("我的课表", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                TextButton(onAddSchedule) { Text("添加", fontSize = 13.sp) }
            }
            schedules.forEach { sc ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sc.id == activeScheduleId, onClick = { vm.switchSchedule(sc.id) })
                    Column(Modifier.weight(1f)) {
                        Text(sc.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("${LocalDate.ofEpochDay(sc.startEpochDay)} · ${sc.totalWeeks} 周", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton({
                        showShare = true
                        shareText = null
                        vm.buildShareText(sc.id) { shareText = it }
                    }) { Text("分享", fontSize = 13.sp) }
                    TextButton({ renaming = sc }) { Text("改名", fontSize = 13.sp) }
                    TextButton(
                        enabled = schedules.size > 1 && sc.id != activeScheduleId,
                        onClick = { vm.deleteSchedule(sc.id) }
                    ) { Text("删除", fontSize = 13.sp, color = if (schedules.size > 1 && sc.id != activeScheduleId) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
                }
            }
            if (schedules.size <= 1) Text("至少保留一份课表；切换后调度/通知/小组件都按所选课表计算。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }

    // 重命名课表
    renaming?.let { sc ->
        var name by remember(sc.id) { mutableStateOf(sc.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名课表") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton({
                    if (name.isNotBlank()) vm.renameSchedule(sc.id, name.trim())
                    renaming = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton({ renaming = null }) { Text("取消") } }
        )
    }

        // 新建课表（可复制当前）已移除：新课表统一由「＋」→「文字/Excel 导入」的「新建课表」创建

    // 分享文字：展示 + 复制 + 系统分享（对方走「文字导入课表」或直接分享进本 App）
    if (showShare) {
        AlertDialog(
            onDismissRequest = { showShare = false },
            title = { Text("分享课表文字") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val share = shareText
                    if (share == null) {
                        Text("生成中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        OutlinedTextField(
                            value = share, onValueChange = {}, readOnly = true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 200.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                        Text("复制或分享给同学；对方在课表页右上「＋」→「文字导入课表」，开学日期与总周数会一并带过去。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                shareText?.let { share ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton({
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            cm?.setPrimaryClip(ClipData.newPlainText("dndtimetable 课表文字", share))
                            android.widget.Toast.makeText(context, "课表文字已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }) { Text("复制文字") }
                        TextButton({
                            runCatching {
                                val i = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, share)
                                }
                                context.startActivity(Intent.createChooser(i, "分享课表文字"))
                            }
                        }) { Text("分享") }
                    }
                }
            },
            dismissButton = { TextButton({ showShare = false }) { Text("关闭") } }
        )
    }
}

@Composable
fun AddSpecialCard(vm: MainViewModel) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var type by remember { mutableStateOf(SpecialDateType.HOLIDAY) }
    val isMakeup = type == SpecialDateType.MAKEUP
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("添加特殊日期", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatePickerField(date.toString(), date, { date = it }, Modifier.weight(1f))
            Text("~")
            DatePickerField(if (isMakeup) "结束(被补课日,可选)" else "结束(可选)", endDate, { endDate = it }, Modifier.weight(1f))
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(SpecialDateType.HOLIDAY to "放假", SpecialDateType.MAKEUP to "补课").forEachIndexed { i, (t, label) ->
                SegmentedButton(selected = type == t, onClick = { type = t }, shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)) { Text(label) }
            }
        }
        if (isMakeup) {
            Text(
                "开始=补课日，结束=被补的那天（如 9/20 补 10/6 的课）；结束日期可先不填，补课日前一天会提醒。",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton({
            val ep = date.toEpochDay()
            val epe = endDate?.toEpochDay()?.takeIf { if (isMakeup) true else it >= ep }   // 被补日可早于补课日（10/10 补 10/7）
            vm.addSpecial(SpecialDate(
                epochDay = ep,
                endEpochDay = if (isMakeup) ep else epe,
                type = type,
                sourceEpochDay = if (isMakeup) epe else null
            ))
        }, Modifier.fillMaxWidth()) { Text("添加") }
    } }
}

