package com.xinyue.reader.feature.home

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HomeStateDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HomeZoneId

@Module
@InstallIn(SingletonComponent::class)
object HomeModule {
    @Provides
    @HomeStateDispatcher
    fun provideHomeStateDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @HomeZoneId
    fun provideHomeZoneId(): ZoneId = ZoneId.systemDefault()
}
