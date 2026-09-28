package com.dndtimetable.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/**
 * 按 ROM 族的保活引导（多机型适配 §11.2）。
 * 各厂商「自启动/省电」页组件路径可能随版本变化，一律 try 直达，失败回退应用详情页，绝不崩溃。
 */
enum class RomFamily(
    val label: String,
    val keepAliveHint: String,
    val autostart: ComponentName?,
    val battery: ComponentName?
) {
    HYPEROS(
        "HyperOS（小米/红米）",
        "允许自启动 + 省电策略设为「不限制」；最近任务下拉锁定本 App（上滑清理=强停，会清掉保活通知与闹钟）",
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")
    ),
    COLOROS(
        "ColorOS（OPPO/一加/真我）",
        "允许自启动 + 允许后台运行",
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.batterylimit.BatteryLimitActivity")
    ),
    ORIGINOS(
        "OriginOS（vivo/iQOO）",
        "允许后台高耗电",
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
    ),
    MAGICOS(
        "MagicOS（荣耀）",
        "允许自启动",
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.ChinaPowerReminderSettings"),
        ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.ChinaPowerReminderSettings")
    ),
    ONEUI("One UI（三星）", "电池「不受限制」", null, null),
    GENERIC("通用（其他/类原生）", "省电白名单 + 允许后台", null, null)
}

/** 品牌 → ROM 族归属。 */
fun detectRomFamily(): RomFamily {
    val m = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
    return when {
        "xiaomi" in m || "redmi" in m || "poco" in m -> RomFamily.HYPEROS
        "oppo" in m || "oneplus" in m || "realme" in m -> RomFamily.COLOROS
        "vivo" in m || "iqoo" in m -> RomFamily.ORIGINOS
        "honor" in m -> RomFamily.MAGICOS
        "samsung" in m -> RomFamily.ONEUI
        else -> RomFamily.GENERIC
    }
}

/**
 * 打开自启动设置：
 * 1. 优先直达应用详情页——该页自带「自启动」开关，一步到位；
 * 2. 详情页打不开时才进本 ROM 的自启动管理列表，先复制应用名，方便用户在列表页搜索。
 */
fun openAutoStartSettings(context: Context): Boolean {
    val detail = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(detail) }.isSuccess) return true
    copyAppNameForSearch(context)
    return openComponent(context, detectRomFamily().autostart)
}

/** 兜底的「自启动管理」列表页里定位本应用用：复制应用名到剪贴板并提示。 */
private fun copyAppNameForSearch(context: Context) {
    runCatching {
        val name = context.applicationInfo.loadLabel(context.packageManager).toString()
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("app_name", name))
        Toast.makeText(context, "已复制「$name」，可在列表页搜索", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 打开本应用的「省电/后台」设置，逐级降级（每步 try 失败自动下一步，绝不崩溃）：
 * 1. **每应用电量详情（省电策略）**：`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `package:包名`
 *    ——HyperOS 实测被 PowerDetailActivity（priority 999）拦截直达「电量详情」页（真机验证）；
 * 2. **应用的电池使用情况列表**：同一 action 不带 data，MIUI 落列表页（用户认可的兜底）；
 * 3. 本 ROM 的省电组件（ColorOS 等）；
 * 4. 应用详情页（通用兜底）。
 * 注意：无 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限时类原生会抛 SecurityException——正是我们期望的降级信号。
 */
fun openBatterySettings(context: Context): Boolean {
    val pkg = context.packageName
    val detail = Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
        .setData(Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(detail) }.isSuccess) return true
    val list = Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(list) }.isSuccess) return true
    return openComponent(context, detectRomFamily().battery)
}

private fun openComponent(context: Context, cp: ComponentName?): Boolean {
    if (cp != null && runCatching {
            context.startActivity(Intent().setComponent(cp).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    ) return true
    // 兜底：直接打开应用详情（各 ROM 通用）
    val detail = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { context.startActivity(detail) }.isSuccess
}
