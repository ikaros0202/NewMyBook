package com.xinyue.reader.feature.library

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LibraryStateDispatcher

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides
    @LibraryStateDispatcher
    fun provideLibraryStateDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
}
