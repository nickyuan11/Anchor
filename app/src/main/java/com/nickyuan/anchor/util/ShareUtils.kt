package com.nickyuan.anchor.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object ShareUtils {

    /**
     * 调起系统分享面板分享一个文件（比如发到微信）。
     *
     * FileProvider.getUriForFile：把 App 私有文件转成 content:// 链接；
     * FLAG_GRANT_READ_URI_PERMISSION：临时允许接收方 App 读取这一个文件。
     */
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }
}
