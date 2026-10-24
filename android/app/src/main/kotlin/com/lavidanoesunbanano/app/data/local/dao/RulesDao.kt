package com.lavidanoesunbanano.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.lavidanoesunbanano.app.data.local.entity.BlockRangeEntity
import com.lavidanoesunbanano.app.data.local.entity.BlockedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RulesDao {
    @Query("SELECT * FROM blocked_apps ORDER BY label")
    abstract fun observeBlockedApps(): Flow<List<BlockedAppEntity>>

    @Query("SELECT * FROM blocked_apps ORDER BY label")
    abstract suspend fun getBlockedApps(): List<BlockedAppEntity>

    @Query("SELECT * FROM block_ranges ORDER BY id")
    abstract fun observeRanges(): Flow<List<BlockRangeEntity>>

    @Query("SELECT * FROM block_ranges ORDER BY id")
    abstract suspend fun getRanges(): List<BlockRangeEntity>

    @Transaction
    open suspend fun replaceRules(apps: List<BlockedAppEntity>, ranges: List<BlockRangeEntity>) {
        deleteBlockedApps()
        deleteRanges()
        insertBlockedApps(apps)
        insertRanges(ranges)
    }

    @Query("DELETE FROM blocked_apps")
    protected abstract suspend fun deleteBlockedApps()

    @Query("DELETE FROM block_ranges")
    protected abstract suspend fun deleteRanges()

    @Insert
    protected abstract suspend fun insertBlockedApps(apps: List<BlockedAppEntity>)

    @Insert
    protected abstract suspend fun insertRanges(ranges: List<BlockRangeEntity>)
}
