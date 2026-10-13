package com.x.cloudnext.android.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.x.cloudnext.android.database.dao.DriveCacheDao
import com.x.cloudnext.android.database.dao.TransferTaskDao
import com.x.cloudnext.android.database.entity.DriveCacheEntity
import com.x.cloudnext.android.database.entity.TransferTaskEntity

@Database(
    entities = [
        DriveCacheEntity::class,
        TransferTaskEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [],
)
abstract class CloudNextDatabase : RoomDatabase() {
    abstract fun driveCacheDao(): DriveCacheDao
    abstract fun transferTaskDao(): TransferTaskDao
}
