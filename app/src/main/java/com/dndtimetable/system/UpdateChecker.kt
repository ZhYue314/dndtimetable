package com.dndtimetable.system

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * 应用内更新：查最新 Release（tag 形如 v1.0），有新 APK 则下载到缓存后走系统安装。
 * 多源按序尝试（同调休表思路，先成功先用）：
 * 1. github.com 的 latest 下载直链——302 的 Location 里就带 tag 与下载地址，不占 API 限额、国内通常更稳；
 * 2. GitHub API JSON——兜底。
 * 全失败返回 null（调用方显示「暂无更新」，不打扰）。
 */
object UpdateChecker {
    private const val API = "https://api.github.com/repos/ZhYue314/dndtimetable/releases/latest"
    private const val LATEST_APK = "https://github.com/ZhYue314/dndtimetable/releases/latest/download/app-release.apk"

    data class Latest(val version: String, val apkUrl: String)

    fun fetchLatest(): Latest? = fromRedirect() ?: fromApi()

    /** 源 1：latest APK 直链不跟随重定向，取 302 的 Location（…/releases/download/<tag>/app-release.apk）。 */
    private fun fromRedirect(): Latest? = runCatching {
        val conn = URL(LATEST_APK).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 5_000
        conn.readTimeout = 5_000
        conn.setRequestProperty("User-Agent", "dndtimetable")
        val loc = try {
            if (conn.responseCode in 300..399) conn.getHeaderField("Location") else null
        } finally {
            conn.disconnect()
        }
        val tag = tagFromLocation(loc) ?: return@runCatching null
        Latest(tag.trimStart('v', 'V'), loc!!)
    }.getOrNull()

    /** 从 Location 提取 tag：…/releases/download/<tag>/xxx → <tag>；不含该路径返回 null。 */
    fun tagFromLocation(loc: String?): String? {
        val marker = "/releases/download/"
        val i = loc?.indexOf(marker) ?: return null
        if (i < 0) return null
        return loc.substring(i + marker.length).substringBefore('/').ifBlank { null }
    }

    /** 源 2：GitHub API——tag_name + assets 里的 .apk 直链。 */
    private fun fromApi(): Latest? {
        val body = HolidayFetcher.fetch(API) ?: return null
        return runCatching {
            val o = JSONObject(body)
            val ver = o.optString("tag_name").trim().trimStart('v', 'V')
            val arr = o.optJSONArray("assets") ?: return null
            val apk = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
            if (ver.isEmpty() || apk.isNullOrEmpty()) null else Latest(ver, apk)
        }.getOrNull()
    }

    /** 逐段数字比较（0.10 > 0.9），段缺失按 0。 */
    fun isNewer(latest: String, current: String): Boolean {
        fun parts(s: String) = s.split('.').map { it.toIntOrNull() ?: 0 }
        val l = parts(latest)
        val c = parts(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    fun download(url: String, dest: File): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            if (conn.responseCode != 200) return false
            dest.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (_: Exception) {
            dest.delete()
            false
        } finally {
            conn?.disconnect()
        }
    }
}
