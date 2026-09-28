package com.dndtimetable.notify

import com.dndtimetable.system.AppLog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dndtimetable.MainActivity
import com.dndtimetable.R
import com.dndtimetable.data.prefs.PeriodTable
import com.dndtimetable.di.AppGraph
import com.dndtimetable.system.DevClock
import com.dndtimetable.domain.ScheduleEngine
import com.dndtimetable.scheduling.StatusSource
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 常驻状态通知：所有阶段（上课前/上课中/小课间/大课间）共用一条卡（不可划掉）；无独立提醒通道。
 * 渠道 id 跟项目名走（`dndtimetable_*`）；历史 `autodnd_*` 渠道在 [migrateLegacyChannels] 里
 * 把用户设置（重要性/声音/折叠）克隆到新 id 后删除——渠道创建后设置不可程序化改，只能换 id 迁移。
 */
object StatusNotification {
    /** 旧渠道（IMPORTANCE_LOW）：保留仅用于清理/兼容旧通知。 */
    const val CHANNEL_ID = "dndtimetable_status"

    /** 过渡渠道（DEFAULT + 无音频）：实测仍被澎湃OS当静默通知折叠，保留仅兼容已安装版本。 */
    const val CHANNEL_ID_V2 = "dndtimetable_status_v2"

    /**
     * 现行渠道：IMPORTANCE_DEFAULT + **静音音频**（无声但系统视作"有声音"，isNoisy=true）。
     * 实测澎湃OS 折叠「更多通知」的主因是折叠态自定义视图（见 buildCardNotification），
     * 这里同时排除"静默通知"这一可能因素，保证常驻卡直接显示在通知页且不打扰。
     * （渠道创建后重要性/声音不可程序化修改，故换新 id。）
     */
    const val CHANNEL_ID_V3 = "dndtimetable_status_v3"
    const val NOTIF_ID = 100

    /** 项目改名前的渠道 id（`autodnd_*`）→ 现行 id；[migrateLegacyChannels] 一次性搬设置并删旧。 */
    private val LEGACY_CHANNEL_IDS = listOf(
        "autodnd_status" to CHANNEL_ID,
        "autodnd_status_v2" to CHANNEL_ID_V2,
        "autodnd_status_v3" to CHANNEL_ID_V3,
        "autodnd_remind" to REMIND_CHANNEL_ID,
    )

    /**
     * 渠道 id 改名迁移（`autodnd_*` → `dndtimetable_*`，项目改名 M24）。
     * 渠道一旦创建，重要性/声音/振动等用户设置**不能程序化修改**，只能换 id；为不丢用户调过的设置，
     * 把老渠道整条克隆（含用户改动）到新 id 后再删老渠道——既不重置设置，也不留僵尸渠道。
     * 已经迁过（新 id 存在）时只做清理；任何一步失败都只记日志，绝不影响通知本身。
     */
    private fun migrateLegacyChannels(nm: NotificationManager) {
        LEGACY_CHANNEL_IDS.forEach { (oldId, newId) ->
            runCatching {
                val old = nm.getNotificationChannel(oldId) ?: return@runCatching
                if (nm.getNotificationChannel(newId) == null) {
                    nm.createNotificationChannel(cloneChannel(old, newId))
                }
                nm.deleteNotificationChannel(oldId)
            }.onFailure { AppLog.w("dndtimetable", "渠道迁移失败 $oldId → $newId", it) }
        }
    }

    /**
     * 把老渠道的用户设置（重要性/声音/振动/角标/灯光/描述）搬到新 id。
     * `NotificationChannel.cloneWithId` 是隐藏 API，SDK 里拿不到，只能手抄；
     * 需要额外权限的两项（免打扰放行、锁屏可见性）读回可能抛，失败就用默认值——它们极少被用户改。
     */
    private fun cloneChannel(src: NotificationChannel, newId: String): NotificationChannel =
        NotificationChannel(newId, src.name, src.importance).apply {
            description = src.description
            setSound(src.sound, src.audioAttributes)
            enableVibration(src.shouldVibrate())
            vibrationPattern = src.vibrationPattern
            setShowBadge(src.canShowBadge())
            enableLights(src.shouldShowLights())
            lightColor = src.lightColor
            runCatching { setBypassDnd(src.canBypassDnd()) }
            runCatching { setLockscreenVisibility(src.lockscreenVisibility) }   // NO_OVERRIDE(-1000) 会被拒，失败即用默认
        }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        migrateLegacyChannels(nm)
        val attrs = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID_V3, "免打扰课表状态", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(
                    android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.notif_silent}"),
                    attrs
                )
                enableVibration(false)
                setShowBadge(false)
                description = "上课中/课间的常驻状态与快捷操作（不发声）"
            }
        )
        // 旧渠道保留（历史通知仍可显示），但不再用于新通知
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "免打扰课表状态（旧）", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID_V2, "免打扰课表状态（过渡）", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
    }

    fun cancel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // 会话激活期间前台保活服务持有同 ID 通知（NOTIF_ID）：不能撤——撤了前台服务通知属违规
        // （系统会警告并可能杀服务）；状态由服务/update 接管，下课 end() 停服务时通知一并消失。
        val keepaliveOwns = runCatching {
            val store = com.dndtimetable.system.DndStateStore(context)
            store.loadFilter() != null && !store.isSessionStale()
        }.getOrDefault(false)
        if (keepaliveOwns) return
        runCatching { nm.cancel(NOTIF_ID) }
        runCatching { nm.cancel(101) }   // 旧版提醒 ID 残留清理
    }

    /** 一次性提醒渠道（默认声音）：调休补课日头天 20:00 用；与无声常驻卡渠道分开。 */
    const val REMIND_CHANNEL_ID = "dndtimetable_remind"
    /** 一次性提醒通知 ID：与常驻卡（100）分离，状态卡刷新不会顶掉它。 */
    const val REMIND_NOTIF_ID = 102

    /** 发一条可划掉的一次性通知（调休补课提醒）。 */
    fun showOnce(context: Context, title: String, text: String) {
        if (text.isBlank()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(REMIND_CHANNEL_ID, "调休与提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "调休补课日等一次性提醒（有声）"
            }
        )
        runCatching {
            nm.notify(
                REMIND_NOTIF_ID,
                Notification.Builder(context, REMIND_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_dnd)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(contentIntent(context))
                    .build()
            )
        }
    }

    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun fill(
        tpl: String, course: String?, room: String?, start: String?
    ): String = tpl.replace("{课程}", course ?: "—").replace("{教室}", room ?: "—").replace("{时间}", start ?: "—")

    /**
     * 按当前阶段刷新常驻状态卡（阶段由 [com.dndtimetable.domain.NoticePhase] 与时间严格对应）：
     * - PRE：会话首节课前（标题「快要上课啦」+ 提醒文案）；
     * - SMALL_BREAK / BIG_BREAK：课间（小/大课间文案，下一节课信息）；
     * - IN_CLASS：上课中（当前课程 + 关闭时间）；
     * - HIDDEN：撤掉。
     * 各类消息由「通知管理」对应开关决定是否显示。
     */
    suspend fun update(context: Context) {
        try {
            if (!AppGraph.settings.statusNotif.first()) { cancel(context); return }
            val store = com.dndtimetable.system.DndStateStore(context)
            // 用户本节课主动关闭（首页按钮）后：不显示"静音中"这类会误导的内容，
            // 改为提示"已手动关闭本节自动静音"（下一节恢复自动）
            val sessionActive = store.loadFilter() != null && !store.isSessionStale()
            val suppressed = !sessionActive &&
                com.dndtimetable.system.isManualSuppressed(store.loadManualSuppressUntil(), System.currentTimeMillis())
            if (suppressed) {
                ensureChannel(context)
                // 手动关闭后的反向入口：卡上「重新开启」按钮（展开可见，折叠态正文带提示）；
                // 正文刻意短，保证折叠态 + 「（展开可重新开启）」提示不被截断
                showCard(context, "已手动关闭", "本节不再自动静音", CardAction.REENABLE)
                return
            }
            val s = StatusSource.load(context)
            val showWhen = when (s.phase) {
                com.dndtimetable.domain.NoticePhase.HIDDEN -> false
                com.dndtimetable.domain.NoticePhase.PRE -> AppGraph.settings.notifyReminder.first()
                com.dndtimetable.domain.NoticePhase.SMALL_BREAK -> AppGraph.settings.notifySmallBreak.first()
                com.dndtimetable.domain.NoticePhase.BIG_BREAK -> AppGraph.settings.notifyBigBreak.first()
                com.dndtimetable.domain.NoticePhase.IN_CLASS -> AppGraph.settings.notifyInClass.first()
            }
            if (!showWhen) { cancel(context); return }
            ensureChannel(context)
            val (title, text) = when (s.phase) {
                com.dndtimetable.domain.NoticePhase.PRE ->
                    AppGraph.settings.titlePre.first() to
                        fill(AppGraph.settings.tplReminder.first(), s.nextCourseName, s.nextCourseRoom, s.nextStartClock)
                com.dndtimetable.domain.NoticePhase.SMALL_BREAK ->
                    AppGraph.settings.titleBreak.first() to
                        fill(AppGraph.settings.tplSmallBreak.first(), s.nextCourseName, s.nextCourseRoom, s.nextStartClock)
                com.dndtimetable.domain.NoticePhase.BIG_BREAK ->
                    AppGraph.settings.titleBreak.first() to
                        fill(AppGraph.settings.tplBigBreak.first(), s.nextCourseName, s.nextCourseRoom, s.nextStartClock)
                else -> AppGraph.settings.titleInClass.first() to
                    fill(AppGraph.settings.tplInClass.first(), s.currentCourse, null, s.nextClock)
            }
            // 会话中媒体屏蔽可双向切换：生效中→「解除屏蔽媒体音」；已手动解除→「屏蔽媒体音」
            // （课中按音量键会被回弹以防误触，这里是有意解除/恢复的入口）
            showCard(context, title, text, currentMediaAction(context))
        } catch (_: Exception) {
        }
    }

    /** 上课前提醒不再单发：与上课中/课间共用同一条常驻卡（含课前阶段内容），不可被划掉；由 update() 统一刷新。 */

    /**
     * 当前阶段的卡内容（标题/正文），供常驻状态卡与前台服务通知共用同一样式与文案
     * （服务模式下两者同为 NOTIF_ID 一条通知，update() 刷新即服务通知刷新）。
     */
    suspend fun currentCardContent(context: Context): Pair<String, String> {
        val s = StatusSource.load(context)
        return when (s.phase) {
            com.dndtimetable.domain.NoticePhase.PRE ->
                AppGraph.settings.titlePre.first() to
                    fill(AppGraph.settings.tplReminder.first(), s.nextCourseName, s.nextCourseRoom, s.nextStartClock)
            com.dndtimetable.domain.NoticePhase.SMALL_BREAK ->
                AppGraph.settings.titleBreak.first() to
                    fill(AppGraph.settings.tplSmallBreak.first(), s.nextCourseName, s.nextCourseRoom, s.nextStartClock)
            com.dndtimetable.domain.NoticePhase.BIG_BREAK ->
                AppGraph.settings.titleBreak.first() to
                    fill(AppGraph.settings.tplBigBreak.first(), s.nextCourseName, s.nextCourseRoom, s.nextStartClock)
            else -> AppGraph.settings.titleInClass.first() to
                fill(AppGraph.settings.tplInClass.first(), s.currentCourse, null, s.nextClock)
        }
    }

    /** 常驻卡展开态整行按钮的动作（同一时刻最多一个）。 */
    enum class CardAction { UNMUTE_MEDIA, MUTE_MEDIA, REENABLE }

    /**
     * 会话中媒体屏蔽按钮的当前动作：
     * - 已手动解除 → 「屏蔽媒体音」（本节内可再开启）；
     * - 屏蔽生效中 → 「解除屏蔽媒体音」；
     * - 其余（未应用媒体屏蔽/非会话）→ 无按钮。
     */
    fun currentMediaAction(context: Context): CardAction? {
        val store = com.dndtimetable.system.DndStateStore(context)
        if (store.loadFilter() == null || store.isSessionStale()) return null
        return when {
            store.loadMediaManualOff() -> CardAction.MUTE_MEDIA
            store.loadAppliedMedia() -> CardAction.UNMUTE_MEDIA
            else -> null
        }
    }

    /**
     * 常驻卡构造（前台服务 / 状态卡 / 开发者模拟共用）。
     * 实测：**只要设置折叠态自定义视图（setCustomContentView），澎湃OS 就把整条通知折叠进「更多通知」**
     * （与重要度/有无声音无关）；只设展开态自定义视图（setCustomBigContentView）则直接显示在通知页。
     * 所以折叠态用系统标准布局（标题/正文/展开箭头），展开态用自定义布局画整行按钮
     * （解除屏蔽媒体音 / 屏蔽媒体音 / 重新开启），同时保留系统 action（兼容各 ROM 展开样式）。
     */
    fun buildCardNotification(
        context: Context, title: String, text: String,
        action: CardAction?
    ): Notification {
        val label = when (action) {
            CardAction.UNMUTE_MEDIA -> "解除屏蔽媒体音"
            CardAction.MUTE_MEDIA -> "屏蔽媒体音"
            CardAction.REENABLE -> "重新开启"
            null -> null
        }
        // 按钮在展开视图：折叠态给可发现性提示（所有构建路径统一加，避免开发者模拟漏掉）；
        // 自定义展开视图里按钮就在下方，不再重复提示。
        val hint = when (action) {
            CardAction.UNMUTE_MEDIA -> "（展开可解除）"
            CardAction.MUTE_MEDIA -> "（展开可屏蔽）"
            CardAction.REENABLE -> "（展开可重新开启）"
            null -> null
        }
        val intent = when (action) {
            CardAction.UNMUTE_MEDIA -> unmuteMediaIntent(context)
            CardAction.MUTE_MEDIA -> muteMediaIntent(context)
            CardAction.REENABLE -> reenableIntent(context)
            null -> null
        }
        val hintText = if (hint != null && !text.contains(hint)) "$text$hint" else text
        val custom = android.widget.RemoteViews(context.packageName, R.layout.notification_status)
        custom.setTextViewText(R.id.notif_title, title)
        custom.setTextViewText(R.id.notif_text, text)
        custom.setViewVisibility(R.id.notif_action, if (action != null) android.view.View.VISIBLE else android.view.View.GONE)
        if (label != null && intent != null) {
            custom.setTextViewText(R.id.notif_action, label)
            custom.setOnClickPendingIntent(R.id.notif_action, intent)
        }

        val builder = Notification.Builder(context, CHANNEL_ID_V3)
            .setSmallIcon(R.drawable.ic_stat_dnd)
            .setContentTitle(title)
            .setContentText(hintText)
            .setStyle(Notification.BigTextStyle().bigText(hintText))
            .setCustomBigContentView(custom)
            .setOngoing(true)
            .setOnlyAlertOnce(true)   // 刷新卡面/阶段变化不重复提示（静音音频也不该被触发）
            .setContentIntent(contentIntent(context))
        if (label != null && intent != null) {
            builder.addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_stat_dnd),
                    label,
                    intent
                ).build()
            )
        }
        return builder.build()
    }

    /** 常驻卡（无声、DEFAULT 渠道）：与提醒通知分开 ID，互不覆盖。 */
    private fun showCard(context: Context, title: String, text: String, action: CardAction? = null) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildCardNotification(context, title, text, action))
        }
    }

    /**
     * 媒体屏蔽按钮点击后按当前状态重建卡面（按钮与折叠态提示一起切换）。
     * 不复用 update()：它按真实时钟判阶段，开发者模拟/时钟错位时会跳过刷新，用户会以为按钮没生效。
     * 标题/正文从正在显示的通知里读 → 进程重启后依然可用。
     */
    fun refreshMediaAction(context: Context) {
        runCatching {
            val nm = context.getSystemService(NotificationManager::class.java)
            val cur = nm.activeNotifications.firstOrNull { it.id == NOTIF_ID }?.notification ?: return
            val title = cur.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
            val text = cur.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return
            val base = text
                .removeSuffix("（展开可解除）")
                .removeSuffix("（展开可屏蔽）")
                .removeSuffix("（展开可重新开启）")
                .removeSuffix("（媒体屏蔽已解除）")   // 兼容旧版本已显示的文案
            showCard(context, title, base, currentMediaAction(context))
        }
    }

    /** 「解除屏蔽媒体音」按钮的 PendingIntent（广播，非前台启动，无需 Activity）。 */
    private fun unmuteMediaIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 3,
        Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_UNMUTE_MEDIA),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** 「屏蔽媒体音」按钮的 PendingIntent：本节内重新开启媒体屏蔽。 */
    private fun muteMediaIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 5,
        Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_MUTE_MEDIA),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** 「重新开启」按钮的 PendingIntent：清除手动关闭抑制并恢复本节自动静音。 */
    private fun reenableIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 4,
        Intent(context, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_REENABLE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    suspend fun refresh(context: Context) {
        if (AppGraph.settings.statusNotif.first()) update(context) else cancel(context)
    }

    /**
     * 开发者：直接显示「上课提醒」通知。找得到真实下一节就用真实内容，找不到用测试内容；
     * 不做任何窗口/开关判断——按下就显示。
     */
    suspend fun devShowReminder(context: Context) {
        ensureChannel(context)
        var course = "测试课程"
        var room = "测试教室"
        var time = "10:00"
        try {
            val sid = AppGraph.settings.getActiveScheduleId()
            val semester = AppGraph.db.semesterDao().byIdOnce(sid)
            val courses = AppGraph.db.courseDao().allCoursesOnce(sid)
            val specials = AppGraph.db.specialDateDao().allOnce(sid)
            val periods = AppGraph.settings.getActivePeriods()
            val zone = ZoneId.systemDefault()
            val now = DevClock.now()
            val today = now.atZone(zone).toLocalDate()
            for (d in 0..7) {
                val date = today.plusDays(d.toLong())
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val found = ScheduleEngine.activeDayCourses(semester, courses, specials, date)
                    .sortedBy { it.startPeriod }
                    .firstOrNull { c -> d > 0 || dayStart + (periods.getOrNull(c.startPeriod - 1)?.startMin ?: 0) * 60_000L > now.toEpochMilli() }
                if (found != null) {
                    course = found.name
                    room = found.location ?: "—"
                    val sm = periods.getOrNull(found.startPeriod - 1)?.startMin ?: 0
                    time = if (d == 0) PeriodTable.minuteToText(sm) else "${date.monthValue}.${date.dayOfMonth} ${PeriodTable.minuteToText(sm)}"
                    break
                }
            }
        } catch (_: Exception) {
        }
        val title = AppGraph.settings.titlePre.first()
        val text = fill(AppGraph.settings.tplReminder.first(), course, room, time)
        // 与上课中/课间同一通道：常驻卡（不可划掉）
        showCard(context, title, text)
        AppLog.w("dndtimetable", "提醒已发出（状态卡通道）: $title / $text")
    }

    /**
     * 开发者：直接显示状态通知（绕过课窗判断，标题与文字自定义）。
     * 走与常驻卡同一构建 → 课中模拟时同样带媒体屏蔽按钮（便于验证按钮效果）。
     */
    fun devShowStatus(context: Context, title: String, text: String) {
        ensureChannel(context)
        val action = currentMediaAction(context)
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .notify(NOTIF_ID, buildCardNotification(context, title, text, action))
        }.onSuccess { AppLog.w("dndtimetable", "状态通知已显示（开发者模拟，按钮=$action）: $title / $text") }
    }
}
