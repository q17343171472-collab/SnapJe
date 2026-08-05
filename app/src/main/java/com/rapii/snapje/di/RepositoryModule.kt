package com.rapii.snapje.di

import android.content.ContentResolver
import android.content.Context
import com.rapii.snapje.data.CachedPhotoRepository
import com.rapii.snapje.data.PhotoRepository
import com.rapii.snapje.data.PhotoRepositoryInterface
import com.rapii.snapje.data.SettingsManager
import com.rapii.snapje.data.VaultRepository
import com.rapii.snapje.data.encryption.EncryptionManager
import com.rapii.snapje.data.local.CategoryDao
import com.rapii.snapje.data.local.PhotoDao
import com.rapii.snapje.data.local.VaultPhotoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger Hilt module for repository dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    /**
     * Provides the ContentResolver from application context.
     */
    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver {
        return context.contentResolver
    }

    /**
     * Provides PhotoRepository as PhotoRepositoryInterface.
     * Allows for easy swapping in tests.
     */
    @Provides
    @Singleton
    fun providePhotoRepository(
        contentResolver: ContentResolver
    ): PhotoRepositoryInterface {
        return PhotoRepository(contentResolver)
    }

    /**
     * Provides SettingsManager for app settings persistence.
     */
    @Provides
    @Singleton
    fun provideSettingsManager(
        @ApplicationContext context: Context
    ): SettingsManager {
        return SettingsManager(context)
    }

    /**
     * Provides FileOperations for file operations.
     */
    @Provides
    @Singleton
    fun provideFileOperations(
        @ApplicationContext context: Context
    ): com.rapii.snapje.data.FileOperations {
        return com.rapii.snapje.data.FileOperations(context)
    }

    /**
     * Provides CachedPhotoRepository for offline caching support.
     */
    @Provides
    @Singleton
    fun provideCachedPhotoRepository(
        photoRepository: PhotoRepository,
        categoryDao: CategoryDao,
        photoDao: PhotoDao
    ): CachedPhotoRepository {
        return CachedPhotoRepository(categoryDao, photoDao, photoRepository)
    }

    /**
     * Provides EncryptionManager (Keystore-backed AES-256-GCM).
     */
    @Provides
    @Singleton
    fun provideEncryptionManager(
        @ApplicationContext context: Context
    ): EncryptionManager {
        return EncryptionManager(context)
    }

    /**
     * Provides VaultRepository (encrypted vault storage).
     */
    @Provides
    @Singleton
    fun provideVaultRepository(
        @ApplicationContext context: Context,
        encryptionManager: EncryptionManager,
        vaultPhotoDao: VaultPhotoDao
    ): VaultRepository {
        return VaultRepository(context, encryptionManager, vaultPhotoDao)
    }
}
