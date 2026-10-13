package com.x.cloudnext.android.feature.preview

import com.x.cloudnext.android.common.FileKind
import com.x.cloudnext.android.common.guessFileKind

/**
 * 根据文件名（推断 fileType）决定该走哪种原生预览路径。null = 不支持预览。
 *
 * UI 拿到这个枚举后映射到具体 CnRoute（routes 在 :app 模块定义，避免 :feature:preview
 * 反向依赖 :app）。
 */
enum class PreviewTarget { Image, Pdf, Video, Office, Text }

/**
 * 根据文件名（推断 fileType）决定该走哪种原生预览路径。null = 不支持预览。
 *
 * **注意**：后端 cloudnext-server `DriveService.resolveFileType` 只识别 5 类
 * （word/excel/ppt/pdf/video），其它（包括所有图片 / 文本 / 配置文件）全部打成
 * `"file"`。所以客户端**不能盲信** `fileType`：当后端给的 fileType 是 `"file"` /
 * `"folder"` / null / 空，必须按扩展名重新推断。
 *
 * UI 拿到这个枚举后映射到具体 CnRoute（routes 在 :app 模块定义，避免 :feature:preview
 * 反向依赖 :app）。
 */
fun resolvePreviewTarget(fileName: String, fileType: String? = null): PreviewTarget? {
    val backendKind = fileType?.trim()?.lowercase()
    val kind = if (backendKind.isNullOrBlank() ||
        backendKind == FileKind.FILE ||
        backendKind == FileKind.FOLDER
    ) {
        // 后端不知道是啥（"file"）或没给 —— 用扩展名推断
        guessFileKind(fileName)
    } else {
        backendKind
    }
    return when (kind) {
        FileKind.IMAGE -> PreviewTarget.Image
        FileKind.PDF -> PreviewTarget.Pdf
        FileKind.VIDEO -> PreviewTarget.Video
        FileKind.WORD, FileKind.EXCEL, FileKind.PPT -> PreviewTarget.Office
        // 纯文本（txt/md/log/toml/ini/conf 等）+ 源代码（kt/java/py/json/yaml/sh 等）
        // 都走原生 TextPreviewScreen，UTF-8 优先 GBK 兜底
        FileKind.TEXT, FileKind.CODE -> PreviewTarget.Text
        else -> null
    }
}
