package com.rapii.snapje.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rapii.snapje.util.L
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room database for SnapJe!
 * Provides local caching for categories and photos.
 * 
 * Database name: snapje_database
 * Version: 1
 */
@Database(
    entities = [
        CategoryEntity::class,
        PhotoEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SnapJeDatabase : RoomDatabase() {

    /**
     * Get Category DAO for category operations.
     */
    abstract fun categoryDao(): CategoryDao

    /**
     * Get Photo DAO for photo operations.
     */
    abstract fun photoDao(): PhotoDao

    companion object {
        const val DATABASE_NAME = "snapje_database"
        
        @Volatile
        private var INSTANCE: SnapJeDatabase? = null

        /**
         * Get singleton database instance.
         * Creates database if it doesn't exist.
         */
        fun getDatabase(context: Context): SnapJeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SnapJeDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration() // For development; use migrations in production
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        L.d("SnapJeDatabase", "Database created")
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        L.d("SnapJeDatabase", "Database opened")
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Get singleton database instance with Hilt.
         * Use this in Hilt modules.
         */
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): SnapJeDatabase {
            return getDatabase(context)
        }
    }
}

/**
 * Type converters for Room database.
 * Converts types that Room doesn't support natively.
 */
class Converters {

    /**
     * Convert URI string to android.net.Uri.
     */
    @TypeConverter
    fun fromUriString(uri: String?): android.net.Uri? {
        return uri?.let { android.net.Uri.parse(it) }
    }

    /**
     * Convert Uri to string for database storage.
     */
    @TypeConverter
    fun uriToString(uri: android.net.Uri?): String? {
        return uri?.toString()
    }

    /**
     * Convert SortBy enum to string.
     */
    @TypeConverter
    fun fromSortBy(sortBy: com.rapii.snapje.data.SortBy): String {
        return sortBy.name
    }

    /**
     * Convert string to SortBy enum.
     */
    @TypeConverter
    fun toSortBy(sortBy: String): com.rapii.snapje.data.SortBy {
        return com.rapii.snapje.data.SortBy.valueOf(sortBy)
    }
}
