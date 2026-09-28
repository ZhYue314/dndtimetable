package com.dndtimetable.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** 用户在系统确认页点击「添加」后回调：提示成功并立即刷新内容。 */
class WidgetPinReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Toast.makeText(context, "小组件已添加到桌面", Toast.LENGTH_SHORT).show()
        CourseWidgetProvider.updateAll(context)
    }
}
