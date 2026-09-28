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
import androidx.compose.material.icons.rounded.Forum
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

// ---------------- 关于 ----------------
@Composable
fun AboutScreen(vm: MainViewModel, onBack: () -> Unit, onPermissions: () -> Unit) {
    val context = LocalContext.current
    var devTaps by remember { mutableStateOf(0) }
    // 开发者模式已开启时直接显示卡片（进过一次就不用再点 5 次版本号）
    var showDev by remember { mutableStateOf(vm.devEnabled.value) }
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val shareScope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AppHeader("关于 App", onBack)

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // ponytail: mipmap 自适应图标 XML 无法被 painterResource 解析，用背景色 + 前景 PNG 拼一个
            Box(
                Modifier.padding(top = 8.dp).size(96.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colorResource(R.color.ic_launcher_background))
            ) {
                Image(painterResource(R.drawable.ic_launcher_fg), contentDescription = null, Modifier.fillMaxSize())
            }
            Text("免打扰课表", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text(
                "版本: $versionName", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { devTaps++; if (devTaps >= 5) { showDev = true; devTaps = 0 } }.padding(vertical = 4.dp)
            )
        }

        // 功能行：图标 + 标题 + 右侧值/箭头，单卡片分隔线
        Card(Modifier.fillMaxWidth()) {
            Column {
                val up by vm.updateState.collectAsState()
                AboutRow(Icons.Rounded.SystemUpdate, "检查更新", trailing = when (up.phase) {
                    UpdatePhase.CHECKING -> "检查中…"
                    UpdatePhase.READY -> "发现 v${up.version}"
                    UpdatePhase.DOWNLOADING -> "下载中…"
                    UpdatePhase.LATEST -> "已是最新"
                    UpdatePhase.FAILED -> "暂无更新"
                    UpdatePhase.IDLE -> "检查"
                }, onClick = {
                    when (up.phase) {
                        UpdatePhase.READY -> vm.downloadUpdate { f ->
                            if (f != null) runCatching {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            }
                        }
                        UpdatePhase.DOWNLOADING, UpdatePhase.CHECKING -> {}
                        else -> vm.checkUpdate()
                    }
                })
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                AboutRow(Icons.Rounded.Lock, "权限管理", onClick = onPermissions)
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                AboutRow(Icons.Rounded.Code, "本项目 GitHub 仓库", onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ZhYue314/dndtimetable"))) }
                })
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                AboutRow(Icons.Rounded.Forum, "联系作者", onClick = {
                    // 走 GitHub Issues：users.noreply.github.com 没有 MX 收不了信，mailto 填真实邮箱又会在 APK 里暴露
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ZhYue314/dndtimetable/issues"))) }
                })
                HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                AboutRow(Icons.Rounded.Share, "分享 App", onClick = {
                    val text = "推荐「免打扰课表」：上课自动静音/免打扰，按课表行事，下课自动恢复。"
                    // 文案先复制到剪贴板：微信/QQ 收文件不带文字，粘贴即可
                    runCatching {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("免打扰课表", text))
                    }
                    // 把安装包复制到缓存 share/ 目录，经 FileProvider 分享（失败则退回纯文字）。
                    // 2MB 拷贝放 IO 线程，拷完再拉起分享：主线程拷会掉几帧
                    shareScope.launch {
                        val apk = withContext(Dispatchers.IO) {
                            runCatching {
                                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                                val out = File(dir, "免打扰课表-$versionName.apk")
                                File(context.applicationInfo.sourceDir).copyTo(out, overwrite = true)
                                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
                            }.getOrNull()
                        }
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = if (apk != null) "application/vnd.android.package-archive" else "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            apk?.let {
                                putExtra(Intent.EXTRA_STREAM, it)
                                // 文字同时写进 ClipData（部分目标只读 clip，不读 EXTRA_TEXT）
                                clipData = ClipData.newPlainText("", text).apply { addItem(ClipData.Item(it)) }
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        }
                        runCatching { context.startActivity(Intent.createChooser(i, "分享 App")) }
                    }
                })
            }
        }
        if (showDev) DevModeCard(vm)

        // 特别鸣谢
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FavoriteBorder, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(" 特别鸣谢", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "衷心感谢为本应用软件的开发和维护做出贡献的每一位开发者。您的奉献是应用持续更新和完善的动力！",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun AboutRow(icon: ImageVector, title: String, trailing: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.padding(start = 16.dp).weight(1f), fontSize = 15.sp)
        if (trailing != null) Text(trailing, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Text("›", Modifier.padding(start = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

