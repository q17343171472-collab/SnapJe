package com.rapii.snapje.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
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
 * Version: 2 (added vault_photos table for encrypted vault storage)
 */
@Database(
    entities = [
        CategoryEntity::class,
        PhotoEntity::class,
        VaultPhotoEntity::class
    ],
    version = 2,
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

    /**
     * Get Vault Photo DAO for encrypted vault photos.
     */
    abstract fun vaultPhotoDao(): VaultPhotoDao

    companion object {
        const val DATABASE_NAME = "snapje_database"

        /**
         * Migration 1 -> 2: 新增 vault_photos 表（保险库加密照片元数据）。
         * 表结构与 VaultPhotoEntity 一致（含索引），保证 Room 的 schema 校验通过。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `vault_photos` (
                        `id` TEXT NOT NULL, 
                        `originalName` TEXT NOT NULL, 
                        `bucketId` INTEGER NOT NULL, 
                        `bucketName` TEXT NOT NULL, 
                        `dateTaken` INTEGER NOT NULL, 
                        `size` INTEGER NOT NULL, 
                        `mimeType` TEXT NOT NULL, 
                        `encryptedPath` TEXT NOT NULL, 
                        `thumbnailPath` TEXT NOT NULL DEFAULT '', 
                        PRIMARY KEY(`id`)
                    )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_photos_bucketId` ON `vault_photos` (`bucketId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_photos_dateTaken` ON `vault_photos` (`dateTaken`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_photos_bucketId_dateTaken` ON `vault_photos` (`bucketId`, `dateTaken`)")
            }
        }

        @Volatile
        private var INSTANCE: SnapJeDatabase? = null

        /**
         * Get singleton database instance.
         * Creates database if it doesn't exist.
         *
         * 加固：打开/校验失败（例如旧版数据库 schema 不兼容）时自动删除重建，
         * 避免 App 启动时抛异常闪退。
         */
        fun getDatabase(context: Context): SnapJeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): SnapJeDatabase {
            return try {
                val db = createBuilder(context).build()
                // 主动打开并校验 schema；失败抛异常走下方重建逻辑
                db.openHelper.writableDatabase
                db
            } catch (e: Exception) {
                L.e("SnapJeDatabase", "DB open failed, deleting and rebuilding", e)
                context.deleteDatabase(DATABASE_NAME)
                val db = createBuilder(context).build()
                db.openHelper.writableDatabase
                db
            }
        }

        private fun createBuilder(context: Context): RoomDatabase.Builder<SnapJeDatabase> {
            return Room.databaseBuilder(
                context,
                SnapJeDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                // 版本号不匹配且无迁移路径时直接重建，避免启动崩溃
                .fallbackToDestructiveMigration()
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
