package com.x.cloudnext.android.di

import com.x.cloudnext.android.R
import com.x.cloudnext.android.data.transfer.download.DownloadWorkerConfig
import com.x.cloudnext.android.data.transfer.upload.UploadWorkerConfig
import com.x.cloudnext.android.network.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 :app 的 BuildConfig + R.drawable 注入到 :data:transfer 的 Worker。
 * 避免 :data:transfer 反向依赖 :app（保持单向依赖图）。
 *
 * 注意：BuildConfig.API_BASE_URL 取自 :core:network 模块。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppTransferModule {

    @Provides
    @Singleton
    fun provideUploadWorkerConfig(): UploadWorkerConfig = UploadWorkerConfig(
        baseUrl = BuildConfig.API_BASE_URL,
        smallIconRes = R.mipmap.ic_launcher,
    )

    @Provides
    @Singleton
    fun provideDownloadWorkerConfig(): DownloadWorkerConfig = DownloadWorkerConfig(
        baseUrl = BuildConfig.API_BASE_URL,
        smallIconRes = R.mipmap.ic_launcher,
    )
}
