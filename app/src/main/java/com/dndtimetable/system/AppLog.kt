package com.dndtimetable.system

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * 可导出的调试日志：logcat 照常输出；按档位（运行自检页「调试日志」）把同一条日志追加落盘，
 * 用户导出文件发给开发者即可定位问题。单线程异步写，写失败静默（日志不能反过来拖垮应用）。
 * ponytail: 单文件 256KB 滚动一次（old），导出 = old + 当前；再讲究就上日志库。
 */
object AppLog {

    enum class Level {
        /** 不落盘（logcat 不受影响）。 */
        OFF,
        /** 只落 E。 */
        ERR,
        /** D/W/E 全落。 */
        ALL
    }

    @Volatile
    var level: Level = Level.OFF
        private set

    private const val MAX_BYTES = 256_000L
    private var file: File? = null
    private var old: File? = null
    private val io = Executors.newSingleThreadExecutor()

    /** Application.onCreate 最先调用；此前的日志只进 logcat。 */
    fun init(context: Context) {
        file = File(context.filesDir, "applog.txt")
        old = File(context.filesDir, "applog.old.txt")
        level = runCatching {
            Level.valueOf(
                context.getSharedPreferences("applog", Context.MODE_PRIVATE)
                    .getString("level", Level.OFF.name) ?: Level.OFF.name
            )
        }.getOrDefault(Level.OFF)
    }

    fun setLevel(context: Context, l: Level) {
        level = l
        runCatching {
            context.getSharedPreferences("applog", Context.MODE_PRIVATE)
                .edit().putString("level", l.name).apply()
        }
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        rec('D', tag, msg)
    }

    fun w(tag: String, msg: String, tr: Throwable? = null) {
        Log.w(tag, msg, tr)
        rec('W', tag, if (tr == null) msg else "$msg ($tr)")
    }

    fun e(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(tag, msg, tr)
        rec('E', tag, if (tr == null) msg else "$msg ($tr)")
    }

    private fun rec(lv: Char, tag: String, msg: String) {
        val f = file ?: return
        val need = when (level) {
            Level.OFF -> false
            Level.ERR -> lv == 'E'
            Level.ALL -> true
        }
        if (!need) return
        io.execute {
            runCatching {
                if (f.length() > MAX_BYTES) old?.let { o -> o.delete(); f.renameTo(o) }
                val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                f.appendText("$ts $lv/$tag: $msg\n")
            }
        }
    }

    /** 导出内容（old + 当前）；无日志返回空串。 */
    fun exportText(): String = runCatching {
        listOfNotNull(old?.takeIf { it.exists() }, file?.takeIf { it.exists() })
            .joinToString("") { it.readText() }
    }.getOrDefault("")

    fun sizeBytes(): Long = runCatching {
        (old?.takeIf { it.exists() }?.length() ?: 0L) + (file?.takeIf { it.exists() }?.length() ?: 0L)
    }.getOrDefault(0L)

    fun clear() {
        io.execute { runCatching { old?.delete(); file?.delete() } }
    }
}
