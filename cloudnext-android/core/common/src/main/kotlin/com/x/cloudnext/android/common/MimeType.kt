package com.x.cloudnext.android.common

/**
 * 把后端返回的 fileType 字符串归一到 CnFileIconColors 使用的 7 个分类键。
 * 后端给的 fileType 列表见 cloudnext-server DriveFileType。
 */
object FileKind {
    const val FOLDER = "folder"
    const val WORD = "word"
    const val EXCEL = "excel"
    const val PPT = "ppt"
    const val PDF = "pdf"
    const val VIDEO = "video"
    const val IMAGE = "image"
    const val AUDIO = "audio"
    const val ARCHIVE = "archive"
    const val CODE = "code"
    const val TEXT = "text"
    const val FILE = "file"
}

private val EXTENSION_KIND_MAP = mapOf(
    "doc" to FileKind.WORD, "docx" to FileKind.WORD, "rtf" to FileKind.WORD,
    "xls" to FileKind.EXCEL, "xlsx" to FileKind.EXCEL, "csv" to FileKind.EXCEL,
    "ppt" to FileKind.PPT, "pptx" to FileKind.PPT, "key" to FileKind.PPT,
    "pdf" to FileKind.PDF,
    "mp4" to FileKind.VIDEO, "mov" to FileKind.VIDEO, "webm" to FileKind.VIDEO,
    "mkv" to FileKind.VIDEO, "avi" to FileKind.VIDEO,
    "jpg" to FileKind.IMAGE, "jpeg" to FileKind.IMAGE, "png" to FileKind.IMAGE,
    "gif" to FileKind.IMAGE, "bmp" to FileKind.IMAGE, "webp" to FileKind.IMAGE,
    "svg" to FileKind.IMAGE, "heic" to FileKind.IMAGE,
    "mp3" to FileKind.AUDIO, "wav" to FileKind.AUDIO, "flac" to FileKind.AUDIO,
    "m4a" to FileKind.AUDIO, "ogg" to FileKind.AUDIO,
    "zip" to FileKind.ARCHIVE, "rar" to FileKind.ARCHIVE, "7z" to FileKind.ARCHIVE,
    "tar" to FileKind.ARCHIVE, "gz" to FileKind.ARCHIVE,
    "js" to FileKind.CODE, "ts" to FileKind.CODE, "kt" to FileKind.CODE,
    "kts" to FileKind.CODE, "java" to FileKind.CODE, "py" to FileKind.CODE,
    "go" to FileKind.CODE, "rs" to FileKind.CODE,
    "c" to FileKind.CODE, "cpp" to FileKind.CODE, "cc" to FileKind.CODE,
    "h" to FileKind.CODE, "hpp" to FileKind.CODE, "cs" to FileKind.CODE,
    "swift" to FileKind.CODE, "php" to FileKind.CODE, "rb" to FileKind.CODE,
    "scala" to FileKind.CODE, "groovy" to FileKind.CODE,
    "vue" to FileKind.CODE, "html" to FileKind.CODE, "htm" to FileKind.CODE,
    "css" to FileKind.CODE, "scss" to FileKind.CODE, "less" to FileKind.CODE,
    "json" to FileKind.CODE, "xml" to FileKind.CODE,
    "yml" to FileKind.CODE, "yaml" to FileKind.CODE,
    "sh" to FileKind.CODE, "bash" to FileKind.CODE, "zsh" to FileKind.CODE,
    "ps1" to FileKind.CODE, "bat" to FileKind.CODE, "cmd" to FileKind.CODE,
    "sql" to FileKind.CODE, "gradle" to FileKind.CODE,
    "txt" to FileKind.TEXT, "md" to FileKind.TEXT, "markdown" to FileKind.TEXT,
    "log" to FileKind.TEXT, "tsv" to FileKind.TEXT,
    "toml" to FileKind.TEXT, "ini" to FileKind.TEXT, "conf" to FileKind.TEXT,
    "properties" to FileKind.TEXT, "env" to FileKind.TEXT,
    "gitignore" to FileKind.TEXT, "gitattributes" to FileKind.TEXT,
    "editorconfig" to FileKind.TEXT,
)

/**
 * 从文件名扩展名推断 file kind。后端通常会同时返回 fileType，但本地排序/上传时仍需。
 */
fun guessFileKind(fileName: String?): String {
    if (fileName.isNullOrBlank()) return FileKind.FILE
    val dot = fileName.lastIndexOf('.')
    if (dot < 0 || dot == fileName.length - 1) return FileKind.FILE
    val ext = fileName.substring(dot + 1).lowercase()
    return EXTENSION_KIND_MAP[ext] ?: FileKind.FILE
}
