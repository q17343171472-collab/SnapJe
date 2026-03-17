package com.rapii.snapje.di

import android.content.Context
import com.rapii.snapje.data.local.CategoryDao
import com.rapii.snapje.data.local.PhotoDao
import com.rapii.snapje.data.local.SnapJeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing Room database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Provide Category DAO.
     */
    @Provides
    @Singleton
    fun provideCategoryDao(database: SnapJeDatabase): CategoryDao {
        return database.categoryDao()
    }

    /**
     * Provide Photo DAO.
     */
    @Provides
    @Singleton
    fun providePhotoDao(database: SnapJeDatabase): PhotoDao {
        return database.photoDao()
    }

    /**
     * Provide Room database singleton.
     */
    @Provides
    @Singleton
    fun provideSnapJeDatabase(
        @ApplicationContext context: Context
    ): SnapJeDatabase {
        return SnapJeDatabase.getDatabase(context)
    }
}
