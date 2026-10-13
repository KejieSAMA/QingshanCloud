package com.x.cloudnext.android.network.di

import com.x.cloudnext.android.network.BuildConfig
import com.x.cloudnext.android.network.api.AuthApi
import com.x.cloudnext.android.network.api.DriveApi
import com.x.cloudnext.android.network.api.LinkApi
import com.x.cloudnext.android.network.api.OrgApi
import com.x.cloudnext.android.network.api.SearchApi
import com.x.cloudnext.android.network.api.ShareApi
import com.x.cloudnext.android.network.api.TrashApi
import com.x.cloudnext.android.network.api.WorkspaceApi
import com.x.cloudnext.android.network.interceptor.AuthHeaderInterceptor
import com.x.cloudnext.android.network.interceptor.SessionExpiredInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authHeaderInterceptor: AuthHeaderInterceptor,
        sessionExpiredInterceptor: SessionExpiredInterceptor,
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authHeaderInterceptor)
        .addInterceptor(sessionExpiredInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideDriveApi(retrofit: Retrofit): DriveApi = retrofit.create(DriveApi::class.java)

    @Provides
    @Singleton
    fun provideTrashApi(retrofit: Retrofit): TrashApi = retrofit.create(TrashApi::class.java)

    @Provides
    @Singleton
    fun provideSearchApi(retrofit: Retrofit): SearchApi = retrofit.create(SearchApi::class.java)

    @Provides
    @Singleton
    fun provideShareApi(retrofit: Retrofit): ShareApi = retrofit.create(ShareApi::class.java)

    @Provides
    @Singleton
    fun provideOrgApi(retrofit: Retrofit): OrgApi = retrofit.create(OrgApi::class.java)

    @Provides
    @Singleton
    fun provideLinkApi(retrofit: Retrofit): LinkApi = retrofit.create(LinkApi::class.java)

    @Provides
    @Singleton
    fun provideWorkspaceApi(retrofit: Retrofit): WorkspaceApi = retrofit.create(WorkspaceApi::class.java)
}
