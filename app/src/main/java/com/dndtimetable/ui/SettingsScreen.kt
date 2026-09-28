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

// ---------------- 设置 ----------------
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onSemesterSettings: () -> Unit,
    onSpecialDates: () -> Unit,
    onDndSettings: () -> Unit,
    onNotifications: () -> Unit,
    onAbout: () -> Unit,
    onGuide: () -> Unit,
    onBackup: () -> Unit,
    onDiagnose: () -> Unit,
    scrollState: ScrollState
) {
    val schedulingEnabled by vm.schedulingEnabled.collectAsState()
    val periods by vm.periods.collectAsState()
    val rule by vm.periodRule.collectAsState()
    val semesterForPeriods by vm.semester.collectAsState()
    val context = LocalContext.current
    var showRule by remember { mutableStateOf(false) }
    var showPeriods by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("设置", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("免打扰总开关", fontWeight = FontWeight.SemiBold)
                Text("关闭后，不再通知、改变系统状态", fontSize = 13.sp)
            }
            Switch(checked = schedulingEnabled, onCheckedChange = { vm.setSchedulingEnabled(it) })
        } }

        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("学期设置", fontWeight = FontWeight.SemiBold)
            val semester by vm.semester.collectAsState()
            val today = DevClock.today()
            val curWeek = semester?.let { ScheduleEngine.weekNumber(it, today) } ?: 0
            semester?.let {
                Text(
                    "开学日期 ${anchorText(LocalDate.ofEpochDay(it.startEpochDay))} · 共 ${it.totalWeeks} 周",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (curWeek > 0) "本周是第 $curWeek 周（${today.monthValue}月${today.dayOfMonth}日 周${ScheduleEngine.weekdayName(ScheduleEngine.weekdayOf(today))}）"
                else "今天不在学期范围内",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (curWeek > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onSemesterSettings, Modifier.weight(1f)) { Text("课表设置", maxLines = 1) }
                OutlinedButton(onSpecialDates, Modifier.weight(1f)) { Text("假期设置", maxLines = 1) }
            }
        } }

        // 节次时间表：可折叠，默认收起
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { showPeriods = !showPeriods }, verticalAlignment = Alignment.CenterVertically) {
                Text("节次时间表", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(if (showPeriods) "收起" else "展开", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            // 每张课表独立：明确提示当前编辑的是哪张（导入新课表会先复制当前课表的作息）
            Text(
                "当前课表：${semesterForPeriods?.name ?: "我的课表"}（每张课表独立，切换课表后在这里改）",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showPeriods) {
                PeriodRows(vm, periods)
                // 三个操作按钮同样三等分：窄屏也保持一行
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ showRule = true }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("设置规则", fontSize = 13.sp, maxLines = 1) }
                    OutlinedButton({ vm.resetPeriodsByRule() }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("按规则重排", fontSize = 13.sp, maxLines = 1) }
                    OutlinedButton({ vm.setPeriods(PeriodTable.DEFAULT) }, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("恢复默认", fontSize = 13.sp, maxLines = 1) }
                }
                // 节数放在整张表最下面：加节后不必再往上翻。默认 10 节（5 大节），
                // 有第 6 大节（11-12 节）的课表在这里加，导入时超节次范围的课才有位置
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text("节数", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "新增的节按规则顺延时间；减节数前需先改掉用到后面节次的课",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Stepper(
                    value = periods.size,
                    onChange = { n ->
                        vm.setPeriodCount(n) { msg ->
                            if (msg != null) android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    min = PeriodTable.MIN_PERIODS,
                    max = PeriodTable.MAX_PERIODS,
                    step = 2,
                    label = { "$it 节" }
                )
            }
        } }

        if (showRule) {
            AlertDialog(
                onDismissRequest = { showRule = false },
                title = { Text("节次规则") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "按规则重排时保持各大节开始时间；若节变长导致与下一大节重叠，将按大课间自动顺延。",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("节时长", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Stepper(rule.durMin, { vm.setPeriodRule(it, rule.breakMin, rule.bigBreakMin) }, min = 15, max = 120, step = 5, label = { "$it 分钟" })
                        Text("小课间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Stepper(rule.breakMin, { vm.setPeriodRule(rule.durMin, it, rule.bigBreakMin) }, min = 0, max = 45, step = 5, label = { "$it 分钟" })
                        Text("大课间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Stepper(rule.bigBreakMin, { vm.setPeriodRule(rule.durMin, rule.breakMin, it) }, min = 0, max = 120, step = 5, label = { "$it 分钟" })
                    }
                },
                confirmButton = { TextButton({ showRule = false }) { Text("完成") } }
            )
        }

        SettingEntry("免打扰设置", "免打扰方式、课后音量、开启/恢复时机", "›") { onDndSettings() }

        Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("桌面小组件", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            OutlinedButton({ requestPinWidget(context) }) { Text("添加到桌面") }
        } }

        SettingEntry("备份与恢复", "导出/导入全部课表与设置，换机迁移用", "›") { onBackup() }
        SettingEntry("使用帮助", "新手引导：如何授权、导入课表、设置学期", "›") { onGuide() }
        SettingEntry("通知管理", "上课提醒与课间消息的开关与文案", "›") { onNotifications() }
        SettingEntry("运行自检", "权限、调度、下次触发时间；可模拟上课/恢复", "›") { onDiagnose() }
        SettingEntry("关于 App", "版本、联系作者、分享、权限管理", "›") { onAbout() }
    }
}

/** 设置页入口行：点击进入对应新页面。 */
@Composable
fun SettingEntry(title: String, desc: String, trailing: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(trailing, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NotifyRow(checked: Boolean, onToggle: (Boolean) -> Unit, label: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/** M3 风格时间选择（动态取色），替代系统旧版对话框。 */
@Composable
fun TimePickerButton(text: String, initialMin: Int, modifier: Modifier = Modifier, onChange: (Int, Int) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton({ show = true }, modifier = modifier, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(text, fontSize = 13.sp, maxLines = 1) }
    if (show) {
        val state = rememberTimePickerState(initialHour = initialMin / 60, initialMinute = initialMin % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton({ onChange(state.hour, state.minute); show = false }) { Text("确定") } },
            dismissButton = { TextButton({ show = false }) { Text("取消") } },
            text = { TimePicker(state) },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        )
    }
}

/** 节次时间表逐行编辑（设置页与新手引导共用）：开始/结束时间 + 该节是否自动静音。 */
@Composable
fun PeriodRows(vm: MainViewModel, periods: List<Period>) {
    periods.forEachIndexed { i, per ->
        // 机型适配：节次标签固定，开始/结束/开关三等分——各行对齐成列，
        // 窄屏（360dp）也保持一行，不换行、不被挤出屏幕
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("第${i + 1}节", Modifier.widthIn(min = 40.dp), fontSize = 13.sp, maxLines = 1)
            TimePickerButton(PeriodTable.minuteToText(per.startMin), per.startMin, Modifier.weight(1f)) { h, m -> vm.updatePeriodStart(i, h, m) }
            Text("–")
            TimePickerButton(PeriodTable.minuteToText(per.endMin), per.endMin, Modifier.weight(1f)) { h, m -> vm.updatePeriod(i, per.startMin, h * 60 + m, per.auto) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Switch(checked = per.auto, onCheckedChange = { vm.updatePeriod(i, per.startMin, per.endMin, it) })
            }
        }
    }
}

/** 正数=提前，负数=延后；单位秒，步进 30 秒。 */
fun bufferText(v: Int, what: String): String = when {
    v == 0 -> "准时$what"
    else -> (if (v > 0) "提前 " else "延后 ") + "${kotlin.math.abs(v)} 秒" + what
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, min: Int, max: Int, step: Int = 30, label: (Int) -> String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        // 机型适配：中间标签不再固定 84dp——大字体/窄屏下「120 分钟」等会截断，改为占满剩余宽度并居中
        OutlinedButton({ if (value - step >= min) onChange(value - step) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("－") }
        Text(label(value), Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        OutlinedButton({ if (value + step <= max) onChange(value + step) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("＋") }
    }
}

/** 开学日期/周次的统一归一说明：第 1 周永远是周一..周日，避免周次与星期错位。 */
fun anchorText(d: LocalDate): String =
    "$d（周${ScheduleEngine.weekdayName(ScheduleEngine.weekdayOf(d))}）"

