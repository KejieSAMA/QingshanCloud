package com.x.cloudnext.android.data.transfer.upload

import kotlinx.coroutines.sync.Semaphore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局上传并发控制。
 *
 * WorkManager 默认 4 个 worker 并发，多文件同时上传会抢带宽 / 后端连接池压力。
 * 这里加 Semaphore（permits=2），UploadWorker.doWork() 用 `gate.withPermit { ... }` 包裹
 * performUpload，超出的 worker 在 acquire 处挂起等待。
 *
 * 等待挂起期间 Worker 仍然占着 setForeground 通知（保活），但不抢网络。M6 可在 Settings 调 permits。
 */
@Singleton
class UploadSemaphore @Inject constructor() {
    val gate: Semaphore = Semaphore(permits = DEFAULT_PERMITS)

    private companion object {
        const val DEFAULT_PERMITS = 2
    }
}
