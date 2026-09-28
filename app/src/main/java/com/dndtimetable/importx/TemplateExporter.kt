package com.dndtimetable.importx

import android.content.Context
import android.net.Uri

/** 把 assets 中的「课表模板.xlsx」另存到用户选择的位置（系统「另存为」，无需 FileProvider）。 */
object TemplateExporter {
    const val FILE_NAME = "课表模板.xlsx"
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val ASSET_NAME = "课表模板.xlsx"

    /** 写入用户通过系统「另存为」选择的 Uri（ActivityResultContracts.CreateDocument 返回）。 */
    fun writeTo(context: Context, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            context.assets.open(ASSET_NAME).use { it.copyTo(out) }
        } ?: throw Exception("无法写入所选位置")
    }
}
