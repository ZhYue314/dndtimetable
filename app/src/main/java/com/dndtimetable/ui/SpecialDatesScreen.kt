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

// ---------------- 假期设置 ----------------
@Composable
fun SpecialDatesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val specials by vm.specials.collectAsState()
    var editing by remember { mutableStateOf<SpecialDate?>(null) }

    // 内置区只显示放假区间；调休补课日（联网播种的 + 用户手加的）都显示在「自添加日期」区
    val builtin = specials.filter { it.builtin && it.type == SpecialDateType.HOLIDAY }.sortedBy { it.epochDay }
    val custom = specials.filterNot { it.builtin && it.type == SpecialDateType.HOLIDAY }.sortedBy { it.epochDay }
    var showBuiltin by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader("假期设置", onBack)

        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("放假与调休怎么用", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(
                "放假、调休已自动同步，一般不用管。点「立即同步节假日」可手动刷新；" +
                    "学校自己的放假/调休在下方「自添加日期」添加；补课日点开填结束日期（补哪天的课），课表会自动同步，没填时头天会提醒。",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } }

        // 立即同步：启动时那次拉取失败（网络抖动/源不可达）会静默回退内置表 → 调休一条都播不出来，
        // 这里给个手动重试入口，主源不通会自动走 jsDelivr 镜像，结果直接回显。
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    if (syncing) return@OutlinedButton
                    syncing = true; syncMsg = null
                    vm.syncHolidays { syncMsg = it; syncing = false }
                },
                enabled = !syncing
            ) { Text(if (syncing) "同步中…" else "立即同步节假日", fontSize = 13.sp) }
            syncMsg?.let {
                Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp))
            }
        }

        AddSpecialCard(vm)

        // 自添加区在前（含联网播种的调休行），内置放假区间在后
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { showCustom = !showCustom }, verticalAlignment = Alignment.CenterVertically) {
                Text("自添加日期", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(if (showCustom) "收起" else "展开", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (showCustom) {
                if (custom.isEmpty()) Text("暂无。学校自己的调休/放假安排请在上方手动添加。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                custom.forEach { s ->
                    // 联网播种的调休行（builtin）：可开关、可点开补全结束日期，但删除会在下次同步后回来，故不给删除
                    SpecialDateRow(s, editing = { editing = s },
                        onToggle = if (s.builtin) ({ vm.setSpecialEnabled(s, it) }) else null,
                        onDelete = if (s.builtin) null else { { vm.deleteSpecial(s) } })
                }
            }
        } }

        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { showBuiltin = !showBuiltin }, verticalAlignment = Alignment.CenterVertically) {
                Text("内置节假日", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(if (showBuiltin) "收起" else "展开", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (showBuiltin) {
                if (builtin.isEmpty()) Text("暂无内置节假日。", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                builtin.forEach { s ->
                    SpecialDateRow(s, editing = { editing = s }, onToggle = { vm.setSpecialEnabled(s, it) }, onDelete = null)
                }
                Text("点击可改日期；开关控制该项是否生效（放假=不排课）。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
    }

    editing?.let { s ->
        var st by remember(s.id) { mutableStateOf(LocalDate.ofEpochDay(s.epochDay)) }
        var en by remember(s.id) { mutableStateOf(s.endEpochDay?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.ofEpochDay(s.epochDay)) }
        var src by remember(s.id) { mutableStateOf(s.sourceEpochDay?.let { LocalDate.ofEpochDay(it) }) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("编辑「${s.name ?: ""}」") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DatePickerField(if (s.type == SpecialDateType.MAKEUP) "开始日期（补课日）" else "开始日期", st, { st = it }, Modifier.fillMaxWidth())
                    if (s.type == SpecialDateType.MAKEUP) {
                        DatePickerField(
                            if (src == null) "结束日期（未设置，点此选择）" else "结束日期（被补课日期）",
                            src ?: st, { src = it }, Modifier.fillMaxWidth()
                        )
                        Text(
                            src?.let {
                                "即 ${st.monthValue}/${st.dayOfMonth} 补 ${it.monthValue}/${it.dayOfMonth} 的课" +
                                    "（按周${ScheduleEngine.weekdayName(ScheduleEngine.weekdayOf(it))}课表），课表自动同步"
                            } ?: "未设置：按当天星期排课，设置后同步被补日的课表",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        DatePickerField("结束日期", en, { en = it }, Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val e = if (s.type == SpecialDateType.MAKEUP) st else if (en >= st) en else st
                    vm.updateSpecial(
                        s.copy(
                            epochDay = st.toEpochDay(), endEpochDay = e.toEpochDay(),
                            // 结束日期 = 被补课日期（留空保持 null，继续走「设置结束日期」提醒）
                            sourceEpochDay = if (s.type == SpecialDateType.MAKEUP) src?.toEpochDay() else s.sourceEpochDay,
                            edited = true
                        )
                    )
                    editing = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton({ editing = null }) { Text("取消") } }
        )
    }
}

@Composable
fun SpecialDateRow(s: SpecialDate, editing: () -> Unit, onToggle: ((Boolean) -> Unit)?, onDelete: (() -> Unit)?) {
    val startD = LocalDate.ofEpochDay(s.epochDay)
    val endD = s.endEpochDay?.let { LocalDate.ofEpochDay(it) }
    val srcD = s.sourceEpochDay?.let { LocalDate.ofEpochDay(it) }
    // 非今年的日期带上年份（如明年的元旦 2027.1.1），避免与今年同月日混淆
    val curYear = LocalDate.now().year
    fun y(d: LocalDate) = if (d.year != curYear) "${d.year}." else ""
    val rangeText = when {
        s.type == SpecialDateType.MAKEUP && srcD == null && s.courseId == null ->
            "${y(startD)}${startD.monthValue}.${startD.dayOfMonth} 补课（未设置结束日期）"
        srcD != null ->
            "${y(startD)}${startD.monthValue}.${startD.dayOfMonth} 补 ${y(srcD)}${srcD.monthValue}.${srcD.dayOfMonth}（周${ScheduleEngine.weekdayName(ScheduleEngine.weekdayOf(srcD))}）的课"
        endD != null && endD != startD ->
            "${y(startD)}${startD.monthValue}.${startD.dayOfMonth} ~ ${if (endD.year != startD.year) y(endD) else ""}${endD.monthValue}.${endD.dayOfMonth}"
        else -> startD.toString()
    }
    Card(Modifier.fillMaxWidth().clickable { editing() }) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            s.name ?: if (s.type == SpecialDateType.HOLIDAY) "放假" else "补课",
            fontWeight = FontWeight.SemiBold,
            color = if (s.type == SpecialDateType.HOLIDAY) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(rangeText, Modifier.weight(1f), fontSize = 14.sp, maxLines = 2)
        when {
            onToggle != null -> Switch(checked = s.enabled, onCheckedChange = onToggle)
            onDelete != null -> TextButton(onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
        }
    } }
}

