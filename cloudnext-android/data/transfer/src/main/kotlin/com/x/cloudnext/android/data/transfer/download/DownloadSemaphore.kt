package com.x.cloudnext.android.data.transfer.download

import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 下载并发上限。与 UploadSemaphore 各自独立（上传下载可同时并行），各 2 permits。
 * 超出的 Worker 在 acquire 处挂起，setForeground 已先生效保活。
 */
@Singleton
class DownloadSemaphore @Inject constructor() {
    val gate: Semaphore = Semaphore(permits = DEFAULT_PERMITS)

    private companion object {
        const val DEFAULT_PERMITS = 2
    }
}
