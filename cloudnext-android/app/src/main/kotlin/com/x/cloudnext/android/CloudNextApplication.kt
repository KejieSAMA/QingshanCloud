package com.x.cloudnext.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * App 入口。
 *
 * - @HiltAndroidApp 生成 ApplicationComponent；其他 @AndroidEntryPoint 才能 inject
 * - 实现 Configuration.Provider 提供 HiltWorkerFactory，让 WorkManager 能注入 @HiltWorker
 * - 实现 ImageLoaderFactory 让 Coil 全局用共享 OkHttp（自动带 X-CloudNext-Token）
 * - Timber 在 debug build 打印日志
 */
@HiltAndroidApp
class CloudNextApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: ImageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    override fun newImageLoader(): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            // 全局开启 WebView Chrome DevTools 远程调试。电脑 Chrome 访问
            // chrome://inspect/#devices 可看到本进程的 WebView，点 inspect 进入完整调试。
            // 仅 debug 构建启用，release 自动关闭，无安全风险。
            android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
