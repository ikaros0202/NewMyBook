package com.xinyue.reader.feature.reader

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderModule {
    @Binds
    abstract fun bindBookPaginator(implementation: AndroidTextPaginator): BookPaginator
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReaderComputationDispatcher

@Module
@InstallIn(SingletonComponent::class)
object ReaderDispatcherModule {
    @Provides
    @ReaderComputationDispatcher
    fun provideReaderComputationDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
