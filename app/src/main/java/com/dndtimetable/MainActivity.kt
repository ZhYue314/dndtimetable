package com.dndtimetable

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.dndtimetable.notify.StatusNotification
import com.dndtimetable.ui.App
import com.dndtimetable.ui.MainViewModel
import com.dndtimetable.ui.theme.DndTimetableTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    // 其他 App「分享」进来的内容（文件/文字/图片）：交给课表页走导入流程
    private var sharedFile by mutableStateOf<Uri?>(null)
    private var sharedText by mutableStateOf<String?>(null)
    private var sharedImage by mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 结果忽略，功能可回退 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        StatusNotification.ensureChannel(this)
        requestNotificationPermission()
        handleShare(intent)
        setContent {
            DndTimetableTheme {
                App(
                    this, vm,
                    sharedFile = sharedFile,
                    sharedText = sharedText,
                    sharedImage = sharedImage,
                    onShareConsumed = { sharedFile = null; sharedText = null; sharedImage = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()   // 切回前台时刷新顶部卡片/状态（用户在快速设置改了静音/免打扰）
        // 部分 ROM（如澎湃）授予权限是异步落库，返回瞬间仍读到旧值：稍后再查一次
        window.decorView.postDelayed({ vm.refresh() }, 1000)
    }

    /** 接收 ACTION_SEND：文件（xls/xlsx/csv）、文字（课表文字）、图片（引导走 AI）。 */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        when {
            uri != null -> sharedFile = uri
            !text.isNullOrBlank() -> sharedText = text
            intent.type.orEmpty().startsWith("image/") -> sharedImage = true
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
