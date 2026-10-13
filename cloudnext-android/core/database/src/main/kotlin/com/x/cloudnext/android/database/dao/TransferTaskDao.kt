package com.x.cloudnext.android.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.x.cloudnext.android.database.entity.TransferTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferTaskDao {

    @Query("SELECT * FROM transfer_task ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransferTaskEntity>>

    @Query("SELECT * FROM transfer_task WHERE status IN (:statuses) ORDER BY createdAt DESC")
    fun observeByStatus(statuses: List<String>): Flow<List<TransferTaskEntity>>

    @Query("SELECT * FROM transfer_task WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): TransferTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TransferTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TransferTaskEntity>)

    @Query(
        "UPDATE transfer_task SET transferredBytes = :transferred, updatedAt = :updatedAt " +
            "WHERE id = :id"
    )
    suspend fun updateProgress(id: String, transferred: Long, updatedAt: Long)

    @Query(
        "UPDATE transfer_task SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt " +
            "WHERE id = :id"
    )
    suspend fun updateStatus(id: String, status: String, errorMessage: String?, updatedAt: Long)

    @Query("UPDATE transfer_task SET conflictStrategy = :strategy, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateConflictStrategy(id: String, strategy: String?, updatedAt: Long)

    @Query("UPDATE transfer_task SET outputUri = :outputUri, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateOutputUri(id: String, outputUri: String?, updatedAt: Long)

    @Query("DELETE FROM transfer_task WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM transfer_task WHERE status IN ('SUCCEEDED', 'CANCELED', 'FAILED')")
    suspend fun clearFinished()
}
