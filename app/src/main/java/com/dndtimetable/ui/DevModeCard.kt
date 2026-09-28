@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dndtimetable.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.media.AudioManager
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dndtimetable.system.AppLog
import com.dndtimetable.system.DevClock
import com.dndtimetable.system.DndController
import com.dndtimetable.system.DndStateStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * 开发者模式：时间模拟 / 会话模拟 / 系统调试 + 状态速览。
 * 按钮全部走生产链路（SilentExecutor / StatusNotification.update / scheduleNext / 划后台自愈），
 * 保证「测到的就是线上跑的」。
 */
@Composable
fun DevModeCard(vm: MainViewModel) {
    val enabled by vm.devEnabled.collectAsState()
    val offsetMin by vm.devOffsetMin.collectAsState()
    val forceZero by vm.forceZeroVolume.collectAsState()
    val context = LocalContext.current
    val fmt = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm:ss") }
    var tick by remember { mutableStateOf(0) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("开发者模式", fontWeight = FontWeight.SemiBold)
                    Text("模拟时间 / 会话 / 系统事件，全部走真实链路", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { vm.setDevEnabled(it) })
            }

            if (enabled) {
                fun t(msg: String) =
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

                // 状态速览每秒刷新（tick 参与 remember 键，驱动重组）
                LaunchedEffect(enabled) {
                    while (enabled) { delay(1000); tick++ }
                }

                DevSection("时间模拟", bordered = true) {
                    // 三列滚轮（天/小时/分钟），样式同选择器：列降序排布 → 上滑为负、下滑为正；滑停即写入偏移
                    val dayValues = remember { (7 downTo -7).toList() }
                    val hourValues = remember { (23 downTo -23).toList() }
                    val minValues = remember { (45 downTo -45).toList() }   // 分钟步长 1
                    var d by remember { mutableStateOf(0) }
                    var h by remember { mutableStateOf(0) }
                    var m by remember { mutableStateOf(0) }
                    fun commit(nd: Int, nh: Int, nm: Int) {
                        d = nd; h = nh; m = nm
                        val t = (nd * 1440 + nh * 60 + nm).coerceIn(-10080, 10080)
                        if (t != offsetMin) vm.setDevOffsetMin(t)
                    }
                    // 外部偏移（初始加载/越界回写）与滚轮不一致时同步滚轮
                    LaunchedEffect(offsetMin) {
                        if (offsetMin != d * 1440 + h * 60 + m) {
                            val r = offsetMin % 1440
                            d = dayValues.minByOrNull { kotlin.math.abs(it - offsetMin / 1440) } ?: 0
                            h = hourValues.minByOrNull { kotlin.math.abs(it - r / 60) } ?: 0
                            m = minValues.minByOrNull { kotlin.math.abs(it - r % 60) } ?: 0
                        }
                    }
                    val itemH = 40.dp
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            listOf("天", "小时", "分钟").forEach {
                                Text(it, Modifier.weight(1f), fontSize = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // 三行显示（中间行=当前值）
                        Box(Modifier.fillMaxWidth().height(itemH * 3)) {
                            Row(Modifier.fillMaxWidth().height(itemH * 3)) {
                                WheelColumn(dayValues, d, { x -> commit(x, h, m) }, Modifier.weight(1f))
                                WheelColumn(hourValues, h, { x -> commit(d, x, m) }, Modifier.weight(1f))
                                WheelColumn(minValues, m, { x -> commit(d, h, x) }, Modifier.weight(1f))
                            }
                            // 居中选择带（上下两条线）
                            Column(Modifier.matchParentSize()) {
                                Spacer(Modifier.height(itemH))
                                HorizontalDivider()
                                Spacer(Modifier.height(itemH - 1.dp))
                                HorizontalDivider()
                            }
                        }
                        // 模拟出的具体时间（DevClock 已含偏移，每秒随 tick 刷新）
                        Text(
                            remember(tick) {
                                DevClock.now().atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("M月d日H时m分"))
                            },
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp
                        )
                    }
                }

                DevSection("模拟上课（真实静音/通知链路）") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ vm.devStartCurrent(); t("已按当前配置上课") }) { Text("按勾选上课") }
                        OutlinedButton({ vm.devStartAs(silent = true, media = false, filter = false); t("已模拟静音上课") }) { Text("静音") }
                        OutlinedButton({ vm.devStartAs(silent = true, media = true, filter = false); t("已模拟静音+屏蔽媒体") }) { Text("静音+媒体") }
                        OutlinedButton({ vm.devStartAs(silent = false, media = false, filter = true); t("已模拟免打扰上课") }) { Text("免打扰") }
                        OutlinedButton({ vm.devStartAs(silent = true, media = true, filter = true); t("已模拟全静音上课") }) { Text("全静音") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ vm.devShowSmallBreak(); t("已模拟小课间（已解除静音）") }) { Text("小课间") }
                        OutlinedButton({ vm.devShowBigBreak(); t("已模拟大课间（已解除静音）") }) { Text("大课间") }
                        OutlinedButton({ vm.devTestReminder(); t("已显示上课提醒") }) { Text("上课提醒") }
                        OutlinedButton({ vm.endSessionNow(); t("已结束会话并还原") }) { Text("结束会话") }
                    }
                }

                DevSection("系统调试（复用生产入口）") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 重排调度 = 打开 App/改时/开机同一条 scheduleNext：自愈→闹钟→状态通知→小组件
                        OutlinedButton({ vm.refresh(); t("已重排调度") }) { Text("重排调度") }
                        OutlinedButton({ vm.devRefreshNotif(); t("已刷新状态通知") }) { Text("刷新通知") }
                        OutlinedButton({ vm.devRefreshWidget(); t("已刷新小组件") }) { Text("刷新小组件") }
                        OutlinedButton({
                            vm.devSimulateSwipe { ok -> t(if (ok) "已触发划后台自愈" else "无活动会话，未触发") }
                        }) { Text("模拟划后台") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({
                            vm.devMediaSelfCheck { msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }) { Text("媒体屏蔽自检") }
                        OutlinedButton({ vm.devHealOrphans(); t("已执行孤儿自愈") }) { Text("孤儿自愈") }
                    }
                }

                // 兼容模式：部分 ROM 的静音标记"谎报成功"（读回 true 但实际仍有声）；
                // 自检确认有声音后打开此开关 → 课时直接降媒体音量屏蔽（课后还原，有归零守卫保护）
                DevSection("兼容模式") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("强制降音量屏蔽", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "静音标记在本机无效时开启：课时把媒体音量降到 0，课后自动还原",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = forceZero,
                            onCheckedChange = { vm.setForceZeroVolume(it); t(if (it) "已启用兼容模式" else "已关闭兼容模式") }
                        )
                    }
                }

                DevSection("状态速览") {
                    val dnd = remember(tick) { DndController(context) }
                    val store = remember { DndStateStore(context) }
                    val status = remember(tick) {
                        val am = context.getSystemService(AlarmManager::class.java)
                        val nextAlarm = am.nextAlarmClock?.triggerTime
                        val filterText = when (dnd.currentFilter()) {
                            NotificationManager.INTERRUPTION_FILTER_ALL -> "关闭"
                            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "优先"
                            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "仅闹钟"
                            NotificationManager.INTERRUPTION_FILTER_NONE -> "静默"
                            else -> "未知"
                        }
                        val ringerText = when (dnd.currentRingerMode()) {
                            AudioManager.RINGER_MODE_SILENT -> "静音"
                            AudioManager.RINGER_MODE_VIBRATE -> "震动"
                            else -> "响铃"
                        }
                        val sessionText = when {
                            store.loadFilter() == null -> "无"
                            store.isSessionStale() -> "残留(昨天)"
                            else -> "进行中"
                        }
                        val suppress = store.loadManualSuppressUntil()
                        val suppressText = if (suppress > System.currentTimeMillis())
                            suppress.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(fmt) }
                        else "无"
                        val logText = when (AppLog.level) {
                            AppLog.Level.OFF -> "关闭"
                            AppLog.Level.ERR -> "仅错误"
                            AppLog.Level.ALL -> "详细"
                        }
                        "模拟现在：${DevClock.now().atZone(ZoneId.systemDefault()).format(fmt)} ｜ 日志：$logText\n" +
                            "DND：$filterText ｜ 铃声：$ringerText ｜ 下一闹钟：" +
                            (nextAlarm?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(fmt) } ?: "无") +
                            "\n会话：$sessionText ｜ 手动抑制至：$suppressText ｜ 课表 id：${vm.activeScheduleId.value}\n" +
                            "媒体屏蔽：${if (store.loadAppliedMedia()) "已应用" else "否"}" +
                            " ｜ 手动解除：${if (store.loadMediaManualOff()) "是" else "否"}" +
                            " ｜ 归零守卫：${if (store.loadZeroGuardActive()) "开" else "无"}" +
                            " ｜ 策略守卫：${if (store.loadPolicyGuard() != null) "有" else "无"}"
                    }
                    Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 开发者卡片内的分组：主色小标题 + 内容（弱于页面级标题，强于说明文字）；bordered=true 整组套描边框。 */
@Composable
private fun DevSection(title: String, bordered: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        if (bordered) Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(12.dp)
        else Modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

/** 滚轮中心最居中的可见项下标（无可见项返回 null）。 */
private fun centeredIndex(state: LazyListState): Int? {
    val info = state.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return null
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.index
}

/**
 * 单列滚轮：values 降序排布（列表下方=负）→ 上滑为负、下滑为正。
 * 靠 rememberSnapFlingBehavior 吸附到整项；滑停后把居中值回报给 onChange。
 */
@Composable
private fun WheelColumn(values: List<Int>, current: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val itemH = 40.dp
    val nearest = { v: Int -> values.minByOrNull { kotlin.math.abs(it - v) } ?: 0 }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(current).coerceAtLeast(0))
    val cb = rememberUpdatedState(onChange)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to centeredIndex(state) }
            .distinctUntilChanged()
            .filter { !it.first && it.second != null }
            .collect { (_, i) -> cb.value(values[i!!]) }
    }
    // 外部 current 变化（初始加载/同步回写）时滚到对应位置
    LaunchedEffect(current) {
        val target = values.indexOf(nearest(current)).coerceAtLeast(0)   // 下标，不是值
        if (centeredIndex(state) != target) state.animateScrollToItem(target)
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        modifier = modifier.fillMaxWidth().height(itemH * 3),
        contentPadding = PaddingValues(vertical = itemH),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(values.size) { i ->
            Text(
                "${values[i]}",
                Modifier.fillMaxWidth().height(itemH),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
