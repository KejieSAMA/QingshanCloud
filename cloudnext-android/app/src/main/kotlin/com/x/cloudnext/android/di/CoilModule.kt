package com.x.cloudnext.android.di

import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Coil 全局 ImageLoader：注入 :core:network 的共享 OkHttpClient，让图片请求自动带
 * X-CloudNext-Token + 走 SessionExpiredInterceptor 处理 401。
 *
 * 用法：在 CloudNextApplication 实现 [ImageLoaderFactory] 并返回这个全局实例；
 * AsyncImage / ZoomableAsyncImage 自动使用。
 */
@Module
@InstallIn(SingletonComponent::class)
object CoilModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(okHttpClient)
        .crossfade(true)
        .build()
}
