package com.x.cloudnext.android.datastore.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * TokenStore 和 SessionInfoStore 已经用 @Inject constructor + @Singleton，
 * 这里不需要额外 provider，留空模块占位以便后续接入 PreferencesRepository 等。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule
