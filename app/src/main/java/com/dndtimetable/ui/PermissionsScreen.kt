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

// ---------------- 权限管理 ----------------
/**
 * 授权向导：按序号逐项点「去开启」，直达本应用的系统权限页（非应用列表），
 * 返回 App 后自动重查状态（MainActivity.onResume → vm.refresh → homeStatus 重发）。
 * 步骤按系统版本动态增减：精确闹钟仅 Android 12+、通知权限仅 Android 13+。
 */
@Composable
fun PermissionsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val home by vm.homeStatus.collectAsState()
    val statusNotif by vm.statusNotif.collectAsState()
    val context = LocalContext.current
    val exactNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val exactGranted = AlarmScheduler.canScheduleExact(context)
    val notifNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notifGranted = !notifNeeded ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val coreDone = (if (home.dndGranted) 1 else 0) +
        (if (!exactNeeded || exactGranted) 1 else 0) +
        (if (!notifNeeded || notifGranted) 1 else 0)
    val coreTotal = 1 + (if (exactNeeded) 1 else 0) + (if (notifNeeded) 1 else 0)
    var step = 0

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader("权限管理", onBack)
        Text(
            "按序号逐项授权（已完成 $coreDone/$coreTotal）。点按钮会直达本应用的系统设置页，打开里面的开关即可，返回后状态自动更新。",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PermissionStep(
            index = ++step, title = "通知使用权", required = true, granted = home.dndGranted,
            desc = "自动免打扰/静音的核心权限。在打开的页面中找到「免打扰课表」，打开开关。",
            actionLabel = "去授权", onAction = { openDndSettings(context) }
        )
        if (exactNeeded) PermissionStep(
            index = ++step, title = "精确闹钟", required = false, granted = exactGranted,
            desc = "上下课准点触发（未开启可能延迟数分钟）。打开页面里的「允许设置闹钟和提醒」开关。",
            actionLabel = "去开启", onAction = { requestExactAlarm(context) }
        )
        if (notifNeeded) PermissionStep(
            index = ++step, title = "通知权限", required = false, granted = notifGranted,
            desc = "常驻状态通知与上课提醒。打开页面顶部的「允许通知」开关。",
            actionLabel = "去开启", onAction = { openAppNotificationSettings(context) }
        )
        val family = detectRomFamily()
        PermissionStep(
            index = ++step, title = "后台保活", required = false, granted = null,
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

/** 桌面快捷方式权限入口（权限管理页与引导权限页共用；跳系统设置，无法程序化检测状态）。 */
@Composable
fun WidgetPermCard() {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable { openWidgetPermSettings(context) }) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("桌面快捷方式权限", fontWeight = FontWeight.SemiBold)
            Text("部分桌面（澎湃/ColorOS 等）可能要求授权后才能添加小组件", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }
}

/** 权限步骤卡：序号 + 标题 + 已开启状态 + 操作说明 + 去开启按钮。granted=null 表示无法检测（如保活设置）。 */
@Composable
fun PermissionStep(
    index: Int, title: String, required: Boolean, granted: Boolean?,
    desc: String, actionLabel: String?, onAction: () -> Unit,
    extra: (@Composable () -> Unit)? = null
) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("$index", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp) }
            Spacer(Modifier.width(8.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            if (required && granted != true) {
                Text("必须", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
            }
            if (granted != null) Text(
                if (granted) "已开启" else "未开启",
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
        Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && granted != true) {
            // 动作按钮右对齐（与卡片右上状态位同侧）
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (required) Button(onAction) { Text(actionLabel) } else OutlinedButton(onAction) { Text(actionLabel) }
            }
        }
        extra?.invoke()
    } }
}

/** 二级页顶部：返回箭头 + 标题。 */
@Composable
fun AppHeader(title: String, onBack: () -> Unit) {    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

/** M3 风格日期选择（动态取色），替代系统旧版对话框。 */
@Composable
fun DatePickerField(label: String, value: LocalDate?, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton({ show = true }, modifier, contentPadding = PaddingValues(horizontal = 12.dp)) {
        // 机型适配：日期文字固定 12.sp + 单行，窄屏/大字体下按钮撑不开时截断也保持一行
        Text(value?.let { "${it.year}-${it.monthValue.toString().padStart(2, '0')}-${it.dayOfMonth.toString().padStart(2, '0')}" } ?: label, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    if (show) {
        val initial = (value ?: LocalDate.now()).toEpochDay() * 86400000L
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton({
                    (state.selectedDateMillis ?: initial)?.let { onChange(LocalDate.ofEpochDay(it / 86400000L)) }
                    show = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ show = false }) { Text("取消") } }
        ) { DatePicker(state) }
    }
}

/**
 * 免打扰权限页：优先直达本应用的「勿扰控制」详情页（AOSP 11+ / 澎湃等，一个开关），
 * 老 ROM 无此页则回退到「通知使用权」应用列表（在其中找「免打扰课表」）。
 */
fun openDndSettings(context: Context) {
    val detail = Intent("android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS")
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(detail) }.isSuccess) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 精确闹钟：带 package 才直达本应用页（不带则各 ROM 显示应用列表，用户找不到入口）。Android 12+ 才有此页。 */
fun requestExactAlarm(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 本应用通知设置页（顶部「允许通知」总开关）；无此页的旧 ROM 回退应用详情。 */
fun openAppNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { openAppDetails(context) }
}

/** 各 ROM 通用的兜底：应用详情页。 */
fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** 弹出系统"添加到主屏幕"确认页（MIUI 为启动器自带确认页），把小组件固定到桌面。 */
fun requestPinWidget(context: Context) {
    runCatching {
        val am = context.getSystemService(AppWidgetManager::class.java)
        val provider = ComponentName(context, CourseWidgetProvider::class.java)
        val callback = PendingIntent.getBroadcast(
            context, 7, Intent(context, com.dndtimetable.widget.WidgetPinReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ok = am != null && am.isRequestPinAppWidgetSupported && am.requestPinAppWidget(provider, null, callback)
        if (!ok) {
            AppLog.w("dndtimetable", "requestPinWidget: launcher 拒绝（常见于 MIUI 未授予「桌面快捷方式」权限）")
            android.widget.Toast.makeText(
                context,
                "桌面未响应：先授予「桌面快捷方式」再试",
                android.widget.Toast.LENGTH_LONG
            ).show()
            openWidgetPermSettings(context)
        }
    }
}

/** 小米/澎湃的固定小组件依赖「桌面快捷方式」权限；直达安全中心的本应用权限页，其他 ROM 跳应用详情。 */
fun openWidgetPermSettings(context: Context) {
    if (detectRomFamily() == RomFamily.HYPEROS) {
        val miui = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        ).putExtra("extra_pkgname", context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(miui) }.onFailure { openAppDetails(context) }
    } else {
        openAppDetails(context)
    }
}
