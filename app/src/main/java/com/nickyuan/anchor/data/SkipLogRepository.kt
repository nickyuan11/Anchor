package com.nickyuan.anchor.data

import com.nickyuan.anchor.data.db.SkipLogDao
import com.nickyuan.anchor.data.db.SkipLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 跳过记录的仓库（Repository），相当于 Spring 里的 Service 层：
 * 页面和无障碍服务都通过它读写记录，不直接接触 Dao。
 *
 * @Inject constructor：告诉 Hilt "用这个构造函数创建我"，参数由 Hilt 自动注入（构造器注入）。
 * @Singleton：整个 App 只有一个实例。
 */
@Singleton
class SkipLogRepository @Inject constructor(
    private val dao: SkipLogDao,
) {
    fun observeRecent(limit: Int = DEFAULT_LIMIT): Flow<List<SkipLogEntity>> = dao.observeRecent(limit)

    suspend fun record(entry: SkipLogEntity) {
        dao.insert(entry)
    }

    /** 某个 App 最近的几条记录，附在页面快照里方便排查 */
    suspend fun recentFor(packageName: String, limit: Int = 20): List<SkipLogEntity> =
        dao.recentFor(packageName, limit)

    suspend fun clear() = dao.clear()

    private companion object {
        const val DEFAULT_LIMIT = 200
    }
}
