package com.nickyuan.anchor.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * App 的本地数据库（SQLite），由 Room 管理。
 *
 * version：表结构版本号。以后改表结构（加字段等）时要加 1，并写迁移逻辑，
 * 否则已安装用户升级后会因为表结构对不上而崩溃。
 */
@Database(entities = [SkipLogEntity::class], version = 1, exportSchema = false)
abstract class AnchorDatabase : RoomDatabase() {
    abstract fun skipLogDao(): SkipLogDao
}
