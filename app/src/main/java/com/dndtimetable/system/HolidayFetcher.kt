package com.dndtimetable.system

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

/**
 * 拉取自托管节假日 JSON（单源 5s 超时；任何失败返回 null，由调用方回退缓存/内置表）。
 * [fetchFirst] 按序试多个源——GitHub raw 在部分网络下不稳/超时，用 jsDelivr 镜像兜底；
 * 每次成败都打日志，方便排「为什么调休没同步上」。
 */
object HolidayFetcher {

    /** 按序尝试 [urls]，返回第一个 HTTP 200 且非空的内容；全失败返回 null。 */
    fun fetchFirst(urls: List<String>): String? {
        for (url in urls) {
            val body = fetch(url)
            if (body != null) {
                Log.w("dndtimetable", "拉取成功：$url")
                return body
            }
            Log.w("dndtimetable", "拉取失败（超时/非200/空）：$url")
        }
        return null
    }

    fun fetch(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            // GitHub API 强制要求 User-Agent，raw 链接带上也无害
            conn.setRequestProperty("User-Agent", "dndtimetable")
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.ifBlank { null }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
