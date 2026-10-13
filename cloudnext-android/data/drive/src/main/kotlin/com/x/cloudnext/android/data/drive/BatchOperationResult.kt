package com.x.cloudnext.android.data.drive

/**
 * 批量操作（移动/批删/批改名）的领域结果。
 *
 * 用 totalSucceeded / totalFailed 在 UI 层做 Toast：
 *   - 全部成功："已移动 N 项"
 *   - 部分失败："成功 X / 失败 Y" + 可点查看 [failedItems]
 */
data class BatchOperationResult(
    val totalRequested: Int,
    val totalSucceeded: Int,
    val totalFailed: Int,
    val succeededIds: List<String> = emptyList(),
    /** itemId → 失败原因 */
    val failedItems: Map<String, String> = emptyMap(),
    val message: String? = null,
) {
    val isAllSuccess: Boolean get() = totalFailed == 0 && totalRequested > 0
    val isAllFail: Boolean get() = totalSucceeded == 0 && totalRequested > 0
}
