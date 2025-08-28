package com.bergi.nbang_v1.di

import com.bergi.nbang_v1.data.repository.AuthRepository
import com.bergi.nbang_v1.data.repository.AuthRepositoryImpl
import com.bergi.nbang_v1.data.repository.PostRepository
import com.bergi.nbang_v1.data.repository.PostRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAuthRepository(authRepositoryImpl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindPostRepository(postRepositoryImpl: PostRepositoryImpl): PostRepository
}
