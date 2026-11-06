package com.nickyuan.anchor.di

import android.content.Context
import androidx.room.Room
import com.nickyuan.anchor.data.db.AnchorDatabase
import com.nickyuan.anchor.data.db.SkipLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块：告诉 Hilt 怎么创建那些"不能直接加 @Inject 构造函数"的对象，
 * 相当于 Spring 里 @Configuration 类中的 @Bean 方法。
 *
 * @InstallIn(SingletonComponent::class)：这里提供的对象在整个 App 范围内可用。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AnchorDatabase =
        Room.databaseBuilder(context, AnchorDatabase::class.java, "anchor.db").build()

    @Provides
    fun provideSkipLogDao(database: AnchorDatabase): SkipLogDao = database.skipLogDao()
}
