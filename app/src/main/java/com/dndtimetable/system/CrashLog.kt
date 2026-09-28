package com.dndtimetable.system

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地崩溃日志（最近一次）：崩溃时写入 filesDir，下次启动首页弹一次，可复制给作者。
 * 只留最近一次、不上传、用户关闭即删除。包住所有 IO，绝不在崩溃处理里再抛异常。
 */
object CrashLog {
    private const val FILE = "crash_last.txt"

    fun install(app: Application) {
        val def = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                File(app.filesDir, FILE).writeText("$time  thread=${t.name}\n$sw")
            }
            def?.uncaughtException(t, e)
        }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
