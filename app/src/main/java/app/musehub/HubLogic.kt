package app.musehub

/** Pure logic, no Android dependencies, covered by unit tests. */

fun allowedNextStatuses(status: String): List<String> = when (status) {
    "pending" -> listOf("in_progress")
    "in_progress" -> listOf("review")
    "review" -> listOf("done", "rejected")
    "rejected" -> listOf("in_progress")
    else -> emptyList()
}

fun statusLabel(status: String): String = when (status) {
    "pending" -> "待认领"
    "in_progress" -> "进行中"
    "review" -> "待验收"
    "done" -> "完成"
    "rejected" -> "驳回"
    else -> status
}

private val SENSITIVE_PATTERNS = listOf(
    "password", "passwd", "id_rsa", "id_ed25519", ".pem", ".key",
    ".p12", "credentials", "secret", "private_key"
)

fun isSensitiveFilename(name: String): Boolean {
    val low = name.lowercase()
    return SENSITIVE_PATTERNS.any { low.contains(it) }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

fun shortTime(iso: String): String {
    // 2026-11-25T00:46:00Z -> 11-25 00:46
    if (iso.length >= 16) return iso.substring(5, 10) + " " + iso.substring(11, 16)
    return iso
}

/** V2 task category. Hub statuses only, no invented states:
 *  pending -> 待处理；in_progress / review -> 进行中；done -> 已完成；rejected -> 失败/需关注 */
fun taskCategory(status: String): String = when (status) {
    "pending" -> "待处理"
    "in_progress", "review" -> "进行中"
    "done" -> "已完成"
    "rejected" -> "失败"
    else -> status
}

fun fileCategory(filename: String): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "ppt", "pptx", "csv" -> "文档"
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> "图片"
        else -> "其他"
    }
}

/** Quote preview for a reply: first line, truncated with an ellipsis. */
fun quoteSnippet(text: String, max: Int = 100): String {
    val oneLine = text.replace("\n", " ").trim()
    return if (oneLine.length <= max) oneLine else oneLine.take(max) + "…"
}

fun voiceFilename(): String {
    val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
        .format(java.util.Date())
    return "voice-$ts.m4a"
}
