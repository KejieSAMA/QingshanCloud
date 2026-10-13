package com.x.cloudnext.android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.x.cloudnext.android.database.entity.DriveCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveCacheDao {

    /**
     * 观察某目录下的文件列表。文件夹排在前面，再按名称字典序。
     */
    @Query(
        "SELECT * FROM drive_cache WHERE parentId = :parentId " +
            "ORDER BY (itemType = 'FOLDER') DESC, name COLLATE NOCASE ASC"
    )
    fun itemsByParent(parentId: String): Flow<List<DriveCacheEntity>>

    /**
     * 取某目录最近一次同步时间，用于判断缓存是否过期。
     */
    @Query("SELECT MAX(lastSyncAt) FROM drive_cache WHERE parentId = :parentId")
    suspend fun lastSyncAt(parentId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DriveCacheEntity>)

    @Query("DELETE FROM drive_cache WHERE parentId = :parentId")
    suspend fun deleteByParent(parentId: String)

    /**
     * 用远程结果覆盖整目录：先删该 parentId 下所有缓存，再插入新数据。原子操作。
     */
    @Transaction
    suspend fun replaceParent(parentId: String, items: List<DriveCacheEntity>) {
        deleteByParent(parentId)
        upsertAll(items)
    }

    @Query("DELETE FROM drive_cache")
    suspend fun clearAll()
}
