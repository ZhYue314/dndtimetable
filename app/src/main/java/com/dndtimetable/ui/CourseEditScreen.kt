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

// ---------------- 课程编辑 ----------------
@Composable
fun CourseEditScreen(vm: MainViewModel, course: Course?, onBack: () -> Unit) {
    val isAdd = course == null || course.id == 0L   // 空位加号会传预填模板（id=0）
    var name by remember { mutableStateOf(course?.name ?: "") }
    var weekday by remember { mutableStateOf(course?.weekday ?: 1) }
    var startPeriod by remember { mutableStateOf(course?.startPeriod ?: 1) }
    var endPeriod by remember { mutableStateOf(course?.endPeriod ?: 2) }
    var startWeek by remember { mutableStateOf(course?.startWeek?.toString() ?: "1") }
    var endWeek by remember { mutableStateOf(course?.endWeek?.toString() ?: "16") }
    var weekType by remember { mutableStateOf(course?.weekType ?: WeekType.ALL) }
    var teacher by remember { mutableStateOf(course?.teacher ?: "") }
    var location by remember { mutableStateOf(course?.location ?: "") }
    var enabled by remember { mutableStateOf(course?.enabled ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (isAdd) "添加课程" else "编辑课程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("教师（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Text("星期", fontWeight = FontWeight.SemiBold)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            dayNames.forEachIndexed { i, w -> SegmentedButton(selected = weekday == i + 1, onClick = { weekday = i + 1 }, shape = SegmentedButtonDefaults.itemShape(index = i, count = dayNames.size)) { Text(w) } }
        }

        Text("节次（第几节到第几节）", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ if (startPeriod > 1) startPeriod -= 1 }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("－") }
            Text("第 $startPeriod 节", Modifier.widthIn(min = 96.dp), fontWeight = FontWeight.Bold, maxLines = 1)
            OutlinedButton({ startPeriod += 1 }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("＋") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ if (endPeriod > startPeriod) endPeriod -= 1 }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("－") }
            Text("第 $endPeriod 节", Modifier.widthIn(min = 96.dp), fontWeight = FontWeight.Bold, maxLines = 1)
            OutlinedButton({ endPeriod += 1 }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("＋") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = startWeek, onValueChange = { startWeek = it.filter(Char::isDigit) }, label = { Text("起始周") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            OutlinedTextField(value = endWeek, onValueChange = { endWeek = it.filter(Char::isDigit) }, label = { Text("结束周") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
        }

        Text("周类型", fontWeight = FontWeight.SemiBold)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(WeekType.ALL to "每周", WeekType.ODD to "单周", WeekType.EVEN to "双周").forEachIndexed { i, (t, label) -> SegmentedButton(selected = weekType == t, onClick = { weekType = t }, shape = SegmentedButtonDefaults.itemShape(index = i, count = 3)) { Text(label) } }
        }

        Row(verticalAlignment = Alignment.CenterVertically) { Text("启用", Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = { enabled = it }) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button({
                if (name.isBlank()) { error = "请填写课程名"; return@Button }
                val sw = startWeek.toIntOrNull(); val ew = endWeek.toIntOrNull()
                if (sw == null || ew == null || ew < sw) { error = "周数无效"; return@Button }
                val c = Course(id = course?.id ?: 0, name = name.trim(), weekday = weekday, startPeriod = startPeriod, endPeriod = endPeriod, startWeek = sw, endWeek = ew, weekType = weekType, teacher = teacher.trim().ifEmpty { null }, location = location.trim().ifEmpty { null }, enabled = enabled)
                if (isAdd) {
                    // 新建：网络课/MOOC 自动默认「不自动免打扰」，其余跟随全局（详见 MainViewModel.withOnlineDefault）
                    vm.addCourse(c)
                } else {
                    // 编辑：保留用户已有的免打扰策略；原来跟随全局的再按课程名重新判定一次
                    val policy = course!!.dndPolicy.let {
                        if (it == DndPolicy.INHERIT) ScheduleEngine.defaultPolicyFor(c.name, c.teacher, c.location) else it
                    }
                    vm.updateCourse(c.copy(dndPolicy = policy, dndCode = course.dndCode))
                }
                onBack()
            }, Modifier.weight(1f)) { Text("保存") }
            if (!isAdd) { OutlinedButton({ vm.deleteCourse(course!!); onBack() }, Modifier.weight(1f)) { Text("删除") } }
        }
    }
}

