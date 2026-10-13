package com.x.cloudnext.android.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.x.cloudnext.android.database.CloudNextDatabase
import com.x.cloudnext.android.database.dao.DriveCacheDao
import com.x.cloudnext.android.database.dao.TransferTaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * v3 → v4：transfer_task 加 scope 列（M5b 工作空间上传支持）。
     * 已有任务默认 scope="drive"（个人盘），保留兼容。
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transfer_task ADD COLUMN scope TEXT NOT NULL DEFAULT 'drive'")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CloudNextDatabase =
        Room.databaseBuilder(context, CloudNextDatabase::class.java, "cloudnext.db")
            .addMigrations(MIGRATION_3_4)
            // 其它版本跳跃（如 v1→v3 跨多版本未提供迁移）兜底重建。
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideDriveCacheDao(db: CloudNextDatabase): DriveCacheDao = db.driveCacheDao()

    @Provides
    @Singleton
    fun provideTransferTaskDao(db: CloudNextDatabase): TransferTaskDao = db.transferTaskDao()
}
