package com.rustypastechat.di

import android.content.Context
import com.rustypastechat.data.api.ApiClientFactory
import com.rustypastechat.data.api.PasteAuthInterceptor
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.repository.LlmRepository
import com.rustypastechat.data.repository.PasteRepository
import com.rustypastechat.security.BiometricLockManager
import com.rustypastechat.security.EncryptedCache
import com.rustypastechat.security.SecurePreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager =
        PreferencesManager(context)

    @Provides
    @Singleton
    fun provideTokenProvider(preferencesManager: PreferencesManager): PasteAuthInterceptor.TokenProvider =
        object : PasteAuthInterceptor.TokenProvider {
            override fun getToken(): String? = preferencesManager.authTokenSync
        }

    @Provides
    @Singleton
    fun provideApiClientFactory(tokenProvider: PasteAuthInterceptor.TokenProvider): ApiClientFactory =
        ApiClientFactory(tokenProvider)

    @Provides
    @Singleton
    fun providePasteRepository(
        preferencesManager: PreferencesManager,
        apiClientFactory: ApiClientFactory
    ): PasteRepository = PasteRepository(preferencesManager, apiClientFactory)

    @Provides
    @Singleton
    fun provideLlmRepository(
        preferencesManager: PreferencesManager,
        apiClientFactory: ApiClientFactory
    ): LlmRepository = LlmRepository(preferencesManager, apiClientFactory)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideSecurePreferences(@ApplicationContext context: Context): SecurePreferences =
        SecurePreferences(context)

    @Provides
    @Singleton
    fun provideBiometricLockManager(@ApplicationContext context: Context): BiometricLockManager =
        BiometricLockManager(context)

    @Provides
    @Singleton
    fun provideEncryptedCache(@ApplicationContext context: Context): EncryptedCache =
        EncryptedCache(context)
}
