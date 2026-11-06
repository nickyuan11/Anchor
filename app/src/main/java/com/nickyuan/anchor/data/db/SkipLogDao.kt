package com.nickyuan.anchor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 跳过记录的数据访问接口，相当于 MyBatis 的 Mapper。
 * 只写接口和 SQL，实现类由 Room 在编译期自动生成。
 *
 * suspend：挂起函数，Room 会自动放到后台线程执行，不会卡住界面。
 * 返回 Flow：表里数据一变化，订阅者就会收到最新的列表，界面因此自动刷新。
 */
@Dao
interface SkipLogDao {

    @Insert
    suspend fun insert(entry: SkipLogEntity): Long

    @Query("SELECT * FROM skip_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SkipLogEntity>>

    @Query("DELETE FROM skip_log")
    suspend fun clear()
}
