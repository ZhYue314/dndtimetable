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

// ---------------- 教务网页导入 ----------------
/** 教务系统基本按桌面网页设计：默认用桌面 UA，渲染不出课表时可切手机版。 */
const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

/**
 * 把当前页面（含同源 iframe）的每个 `<table>` 展开成 DOM 网格：
 * - 用 `table.rows/cells`（浏览器已解析 DOM）→ 天然支持**嵌套表格**与 rowspan/colspan；
 * - 单元格文本按块级子元素用空行分段（一格多课，GridParser 按空行拆课）；
 * - 返回三维数组 `[table][row][col]`（文本或 null），由 evaluateJavascript 直接序列化成 JSON。
 */
const val EXTRACT_GRID_JS = """(function(){
  function norm(t){
    t = (t || '').replace(/\u00a0/g, ' ');
    var lines = t.split('\n');
    for (var i = 0; i < lines.length; i++){ lines[i] = lines[i].replace(/[ \t]{2,}/g, ' ').replace(/^[ \t]+|[ \t]+$/g, ''); }
    return lines.join('\n').replace(/^\n+|\n+$/g, '');
  }
  function cellText(el){
    try {
      var blocks = el.querySelectorAll(':scope > div, :scope > p, :scope > table, :scope > ul, :scope > section');
      if (blocks.length > 1) {
        var parts = [];
        for (var i = 0; i < blocks.length; i++){
          var s = norm(blocks[i].innerText || blocks[i].textContent || '');
          if (s) parts.push(s);
        }
        if (parts.length > 1) return parts.join('\n\n');
        if (parts.length === 1) return parts[0];
      }
    } catch(e){}
    return norm(el.innerText || el.textContent || '');
  }
  function grid(table){
    var out = [], span = {}, rows = table.rows;
    for (var r = 0; r < rows.length; r++){
      var cells = rows[r].cells, line = [], col = 0;
      for (var c = 0; c < cells.length; c++){
        while (span[col] > 0){ line[col] = null; span[col]--; if (!span[col]) delete span[col]; col++; }
        var cell = cells[c];
        line[col] = cellText(cell);
        var rs = cell.rowSpan || 1, cs = cell.colSpan || 1;
        for (var k = 0; k < cs; k++){
          if (k > 0) line[col] = null;
          if (rs > 1) span[col] = rs - 1;
          if (k < cs - 1) col++;
        }
        col++;
      }
      while (span[col] > 0){ line[col] = null; span[col]--; if (!span[col]) delete span[col]; col++; }
      out.push(line);
    }
    return out;
  }
  function collect(doc, acc){
    try {
      var ts = doc.querySelectorAll('table');
      for (var i = 0; i < ts.length; i++){
        try { var g = grid(ts[i]); if (g.length > 1) acc.push(g); } catch(e){}
      }
    } catch(e){}
  }
  var acc = [];
  collect(document, acc);
  try {
    var fs = document.querySelectorAll('iframe,frame');
    for (var i = 0; i < fs.length; i++){ try { collect(fs[i].contentDocument, acc); } catch(e){} }
  } catch(e){}
  return acc;
})()"""

/**
 * 教务网页导入（通用表格提取，不依赖各校适配脚本）：
 * WebView 里登录 → 进入课表页 → 抓全部表格 HTML → 走与文件导入相同的解析/预览/写库流程。
 * 明文流量（校方多为 http）已在 Manifest 放行，仅用于此页浏览。
 */
@Composable
fun WebImportScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val savedUrl by vm.jiaowuUrl.collectAsState()
    var url by remember { mutableStateOf(savedUrl) }
    var webRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<XlsxImporter.Result?>(null) }
    var lastGrid by remember { mutableStateOf("") }
    var desktopUa by remember { mutableStateOf(true) }
    var importName by remember { mutableStateOf("") }
    var intoNew by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { vm.nextDefaultScheduleName { importName = it } }
    LaunchedEffect(savedUrl) { if (url.isBlank() && savedUrl.isNotBlank()) url = savedUrl }

    fun extract() {
        val wv = webRef
        if (wv == null) { msg = "页面还没打开"; return }
        msg = null
        wv.evaluateJavascript(EXTRACT_GRID_JS) { raw ->
            if (raw.isNullOrBlank() || raw == "null" || raw == "[]") {
                msg = "未找到表格：先登录并进入「个人课表」页，再点导入"
            } else {
                lastGrid = raw
                vm.previewWebTables(raw) { res, err -> if (res == null) msg = err ?: "解析失败" else preview = res }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppHeader("教务网页导入", onBack)
        Text("登录教务系统并进入课表页，再点「导入当前页面课表」；仅本机解析，不上传。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(url, { url = it }, label = { Text("教务系统网址（http/https 均可）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({
                val u = url.trim()
                if (u.isNotBlank()) {
                    vm.setJiaowuUrl(u)
                    webRef?.loadUrl(u)
                }
            }, Modifier.weight(1f)) { Text("打开") }
            OutlinedButton({ extract() }, Modifier.weight(1f)) { Text("导入当前页面课表") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("页面空白/布局错乱时：", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({
                desktopUa = !desktopUa
                webRef?.settings?.userAgentString = if (desktopUa) DESKTOP_UA else null
                webRef?.reload()
            }) { Text(if (desktopUa) "切换到手机版" else "切换到桌面版", fontSize = 12.sp) }
        }
        msg?.let { Text(it, fontSize = 12.sp, color = if (it.startsWith("导入成功")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        // 左：排障复制；右：导入成功后去课表（各靠一边，只出现一个时也保持在自己那侧）
        if (lastGrid.isNotBlank() || msg?.startsWith("导入成功") == true) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 各校页面结构差异大：抓取结果可一键复制，缺课时粘给作者就能按真实结构适配
                if (lastGrid.isNotBlank()) OutlinedButton({
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("dndtimetable webimport", lastGrid))
                    msg = "已复制页面表格文本，发给作者可定位缺课"
                }) { Text("复制抓取数据（排障）", fontSize = 13.sp) }
                Spacer(Modifier.weight(1f))
                if (msg?.startsWith("导入成功") == true) OutlinedButton({ onBack() }) { Text("去课表页查看", fontSize = 13.sp) }
            }
        }
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    // HyperOS 4/Android 17 + WebView 151：硬件层会让整个应用窗口变白（连 Compose 文字一起消失），
                    // 软件层渲染可正常显示；教务登录页简单，性能足够。见项目书 §9.3-C
                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.userAgentString = DESKTOP_UA
                    webViewClient = android.webkit.WebViewClient()
                    webRef = this
                }
            },
            // 离开本页即清网页缓存（教务门户图片能攒十几 MB）；登录态在 Cookie/LocalStorage，不清
            onRelease = { wv -> wv.clearCache(true); wv.destroy() },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }

    preview?.let { res ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("导入预览") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("先给课表设置名称", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(importName, { importName = it }, label = { Text("课表名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = intoNew, onClick = { intoNew = true })
                        Text("新建课表（当前课表不变）", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !intoNew, onClick = { intoNew = false })
                        Text("覆盖当前课表", fontSize = 13.sp)
                    }
                    Text(
                        "共 ${res.courses.size} 门课" + if (res.notes.isEmpty()) "" else " + ${res.notes.size} 条无课表课程",
                        fontWeight = FontWeight.SemiBold
                    )
                    res.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    if (res.courses.isEmpty()) {
                        Text("没有解析出课程，请换页面或改用文件导入。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        res.courses.take(8).forEach { c ->
                            val wt = when (c.weekType) { WeekType.ODD -> "单"; WeekType.EVEN -> "双"; else -> "" }
                            Text(
                                "周${"一二三四五六日".getOrElse(c.weekday - 1) { '?' }} 第${c.startPeriod}-${c.endPeriod}节 " +
                                    "${c.name} ${c.startWeek}-${c.endWeek}周$wt",
                                fontSize = 12.sp
                            )
                        }
                        if (res.courses.size > 8) {
                            Text("…（其余 ${res.courses.size - 8} 门）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = res.courses.isNotEmpty() && importName.isNotBlank(),
                    onClick = {
                        val r = res
                        preview = null
                        vm.commitFileImport(r, importName.trim(), intoNew) { m -> msg = m }
                    }
                ) { Text("导入") }
            },
            dismissButton = { TextButton({ preview = null }) { Text("取消") } }
        )
    }
}

