package com.rapii.snapje.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.rapii.snapje.data.encryption.EncryptionManager
import com.rapii.snapje.data.local.VaultBucket
import com.rapii.snapje.data.local.VaultPhotoDao
import com.rapii.snapje.util.L
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 保险库仓库：负责加密照片的导入、解密显示、删除与分组。
 *
 * 安全要点：
 * - 密文只写入 [vaultDir]（filesDir/vault，App 沙盒），系统相册无法扫描。
 * - 解密后的明文只写入 [tempDir]（cacheDir/vault_tmp）临时文件，显示用，可随时清理。
 * - 删除照片时同时删除密文文件 + DB 记录 + 临时解密文件。
 */
@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptionManager: EncryptionManager,
    private val vaultPhotoDao: VaultPhotoDao
) {

    companion object {
        /** 默认保险库相册名 */
        const val DEFAULT_ALBUM = "我的保险库"

        private const val VAULT_DIR = "vault"
        private const val TEMP_DIR = "vault_tmp"
        private const val THUMBNAIL_MAX_DIMENSION = 720
        private const val THUMBNAIL_QUALITY = 85

        /** 临时文件最长保留时间：超过后启动时清理 */
        private const val TEMP_FILE_MAX_AGE_MS = 12 * 60 * 60 * 1000L
    }

    private val vaultDir: File by lazy {
        File(context.filesDir, VAULT_DIR).apply { if (!exists()) mkdirs() }
    }

    private val tempDir: File by lazy {
        File(context.cacheDir, TEMP_DIR).apply { if (!exists()) mkdirs() }
    }

    /** 解密临时文件缓存：vaultId -> File（避免重复解密） */
    private val thumbCache = ConcurrentHashMap<String, File>()
    private val fullCache = ConcurrentHashMap<String, File>()

    /** 每个照片 ID 的解密锁（防止并发写同一临时文件） */
    private val decryptLocks = ConcurrentHashMap<String, Mutex>()

    // ---------------------------------------------------------------------
    // 查询
    // ---------------------------------------------------------------------

    /**
     * 全部保险库照片（最新在前）。
     */
    fun getVaultPhotos(): Flow<List<VaultPhoto>> =
        vaultPhotoDao.getAllPhotos().map { list -> list.map { VaultPhoto.fromEntity(it) } }

    /**
     * 某个保险库相册的照片。
     */
    fun getPhotosByBucket(bucketId: Long): Flow<List<VaultPhoto>> =
        vaultPhotoDao.getPhotosByBucket(bucketId).map { list -> list.map { VaultPhoto.fromEntity(it) } }

    /**
     * 搜索保险库照片（按名称）。
     */
    fun searchPhotos(query: String): Flow<List<VaultPhoto>> =
        vaultPhotoDao.searchPhotos(query).map { list -> list.map { VaultPhoto.fromEntity(it) } }

    /**
     * 获取已有相册名列表（导入时选择用）。
     */
    suspend fun getAlbumNames(): List<String> = withContext(Dispatchers.IO) {
        vaultPhotoDao.getBuckets().map { it.bucketName }.distinct()
    }

    /**
     * 获取所有分组（bucketId -> bucketName），供"移动到其他分组"选择用。
     */
    suspend fun getBuckets(): List<VaultBucket> = withContext(Dispatchers.IO) {
        vaultPhotoDao.getBuckets()
    }

    /**
     * 批量把照片移动到另一个分组。
     * 只更新元数据（bucketId/bucketName），加密文件不动。
     *
     * @return 成功移动的数量
     */
    suspend fun movePhotosToBucket(ids: List<String>, newBucketId: Long, newBucketName: String): Int =
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext 0
            val effectiveName = newBucketName.ifBlank { DEFAULT_ALBUM }
            runCatching {
                vaultPhotoDao.movePhotos(ids, newBucketId, effectiveName)
                ids.size
            }.getOrElse {
                L.e("VaultRepository", "movePhotosToBucket failed: ${it.message}")
                0
            }
        }

    // ---------------------------------------------------------------------
    // 导入（加密存储）
    // ---------------------------------------------------------------------

    /**
     * 从 [sourceUri]（系统相册 / 相机输出）导入一张照片到保险库：
     * 流式 AES-256-GCM 加密写入沙盒 -> 生成并加密缩略图 -> 元数据存入 Room。
     *
     * @param albumName 目标保险库相册名（相同名字归入同一相册；空则用默认名）
     */
    suspend fun addPhotoToVault(sourceUri: Uri, albumName: String): Result<VaultPhoto> =
        withContext(Dispatchers.IO) {
            // 先归一化相册名，保证同名相册的 bucketId 一致（避免空名与默认名被拆成两组）
            val effectiveAlbumName = albumName.ifBlank { DEFAULT_ALBUM }
            val id = UUID.randomUUID().toString()
            val encFile = File(vaultDir, "$id.enc")
            val thumbFile = File(vaultDir, "$id.thumb.enc")

            try {
                val resolver = context.contentResolver

                // 1) 流式加密原图（不整包读入内存，避免大图 OOM）
                val input = resolver.openInputStream(sourceUri)
                    ?: return@withContext Result.failure(IllegalStateException("无法读取所选图片"))
                input.use { stream ->
                    val encResult = encryptionManager.encryptStream(stream, encFile)
                    if (encResult.isFailure) {
                        return@withContext Result.failure(
                            encResult.exceptionOrNull() ?: IllegalStateException("加密失败")
                        )
                    }
                }

                // 2) 解析元数据（原始文件名 / 拍摄时间 / MIME）
                var displayName = sourceUri.lastPathSegment ?: "photo_${System.currentTimeMillis()}.jpg"
                var dateTaken = System.currentTimeMillis()
                val projection = arrayOf(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_TAKEN
                )
                runCatching {
                    resolver.query(sourceUri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getString(0)?.let { displayName = it }
                            val date = cursor.getLong(1)
                            if (date > 0) dateTaken = date
                        }
                    }
                }
                val mimeType = resolver.getType(sourceUri) ?: "image/jpeg"

                // 3) 缩略图（图片解码 / 视频取首帧；失败不影响导入，回退到直接解密原图展示）
                var thumbnailPath = ""
                val thumbBytes = runCatching {
                    resolver.openInputStream(sourceUri)?.use { it.readBytes() }
                }.getOrNull()
                if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                    val tb = if (mimeType.startsWith("video/")) {
                        generateVideoThumbnail(thumbBytes)
                    } else {
                        generateThumbnail(thumbBytes)
                    }
                    tb?.let {
                        if (encryptionManager.encryptBytes(it, thumbFile).isSuccess) {
                            thumbnailPath = thumbFile.absolutePath
                        }
                    }
                }

                val photo = VaultPhoto(
                    id = id,
                    originalName = displayName,
                    bucketId = effectiveAlbumName.hashCode().toLong(),
                    bucketName = effectiveAlbumName,
                    dateTaken = dateTaken,
                    size = encFile.length(),
                    mimeType = mimeType,
                    encryptedPath = encFile.absolutePath,
                    thumbnailPath = thumbnailPath
                )
                vaultPhotoDao.insertPhoto(photo.toEntity())

                L.d("VaultRepository", "Imported ${photo.originalName} to vault (${encFile.length()} bytes)")
                Result.success(photo)
            } catch (e: Exception) {
                // 失败时清理已创建的密文文件，避免孤儿文件
                deleteFileSafely(encFile.absolutePath)
                deleteFileSafely(thumbFile.absolutePath)
                L.e("VaultRepository", "Import failed: ${e.message}", e)
                Result.failure(e)
            }
        }

    // ---------------------------------------------------------------------
    // 删除 / 重命名
    // ---------------------------------------------------------------------

    /**
     * 删除保险库照片：删除密文文件 + 缩略图 + 临时解密文件 + DB 记录。
     */
    suspend fun deletePhoto(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = vaultPhotoDao.getPhotoById(id)
            entity?.let {
                deleteFileSafely(it.encryptedPath)
                deleteFileSafely(it.thumbnailPath)
            }
            deleteFileSafely(thumbCache.remove(id)?.absolutePath)
            deleteFileSafely(fullCache.remove(id)?.absolutePath)
            decryptLocks.remove(id)
            vaultPhotoDao.deletePhoto(id)
            L.d("VaultRepository", "Deleted vault photo: $id")
            Result.success(Unit)
        } catch (e: Exception) {
            L.e("VaultRepository", "Delete failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 重命名保险库照片（仅更新显示名）。
     */
    suspend fun renamePhoto(id: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            vaultPhotoDao.renamePhoto(id, newName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------
    // 解密显示
    // ---------------------------------------------------------------------

    /**
     * 获取解密缩略图 URI（网格 / 封面用）。
     * 解密结果缓存于内存与临时目录；同一照片的并发解密用 per-id 锁串行化，避免写坏临时文件。
     */
    suspend fun thumbnailUri(photo: VaultPhoto): Uri {
        thumbCache[photo.id]?.let { if (it.exists()) return Uri.fromFile(it) }
        val lock = decryptLocks.computeIfAbsent(photo.id) { Mutex() }
        return lock.withLock {
            thumbCache[photo.id]?.let { if (it.exists()) return@withLock Uri.fromFile(it) }
            val file = decryptThumbnail(photo)
            thumbCache[photo.id] = file
            Uri.fromFile(file)
        }
    }

    /**
     * 获取解密原图 URI（全屏预览用）。
     */
    suspend fun fullImageUri(photo: VaultPhoto): Uri {
        fullCache[photo.id]?.let { if (it.exists()) return Uri.fromFile(it) }
        val lock = decryptLocks.computeIfAbsent(photo.id) { Mutex() }
        return lock.withLock {
            fullCache[photo.id]?.let { if (it.exists()) return@withLock Uri.fromFile(it) }
            val file = decryptFull(photo)
            fullCache[photo.id] = file
            Uri.fromFile(file)
        }
    }

    /**
     * 按 vaultId 获取解密原图 URI（全屏预览 / 分享用）。
     */
    suspend fun fullImageUri(vaultId: String): Uri? = withContext(Dispatchers.IO) {
        val entity = vaultPhotoDao.getPhotoById(vaultId) ?: return@withContext null
        fullImageUri(VaultPhoto.fromEntity(entity))
    }

    private suspend fun decryptThumbnail(photo: VaultPhoto): File {
        val outFile = File(tempDir, "${photo.id}_thumb.jpg")
        if (outFile.exists() && outFile.length() > 0) return outFile

        val thumbPath = photo.thumbnailPath
        if (thumbPath.isNotBlank()) {
            val thumb = File(thumbPath)
            if (thumb.exists()) {
                encryptionManager.decryptToFile(thumb, outFile).getOrNull()?.let { return outFile }
            }
        }
        // 回退：解密原图作为缩略图
        return decryptFull(photo)
    }

    private suspend fun decryptFull(photo: VaultPhoto): File {
        val outFile = File(tempDir, "${photo.id}_full${extensionFor(photo.mimeType)}")
        if (outFile.exists() && outFile.length() > 0) return outFile
        val encrypted = File(photo.encryptedPath)
        encryptionManager.decryptToFile(encrypted, outFile).getOrNull()
            ?: throw IllegalStateException("解密失败: ${photo.originalName}")
        return outFile
    }

    private fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "image/heic", "image/heif" -> ".heic"
        else -> ".jpg"
    }

    // ---------------------------------------------------------------------
    // 临时文件清理
    // ---------------------------------------------------------------------

    /**
     * 启动时清理过期的临时解密文件（保留最近 [TEMP_FILE_MAX_AGE_MS] 内的）。
     * 防止临时明文堆积在 cacheDir。
     */
    fun cleanupStaleTempFiles() {
        runCatching {
            val cutoff = System.currentTimeMillis() - TEMP_FILE_MAX_AGE_MS
            tempDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }

    /**
     * 清空全部临时解密文件与缓存（App 启动 / 上锁等时机调用）。
     */
    fun clearTempFiles() {
        runCatching { tempDir.listFiles()?.forEach { it.delete() } }
        thumbCache.clear()
        fullCache.clear()
        decryptLocks.clear()
    }

    /**
     * 仅清空全屏原图临时文件（"退出全屏后自动删除"），保留网格缩略图缓存。
     */
    fun clearFullImageCache() {
        runCatching {
            tempDir.listFiles()
                ?.filter { it.name.contains("_full.") }
                ?.forEach { it.delete() }
        }
        fullCache.clear()
    }

    // ---------------------------------------------------------------------
    // 内部工具
    // ---------------------------------------------------------------------

    private fun generateThumbnail(bytes: ByteArray): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= THUMBNAIL_MAX_DIMENSION &&
                bounds.outHeight / (sampleSize * 2) >= THUMBNAIL_MAX_DIMENSION
            ) {
                sampleSize *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            return generateThumbnailFromBitmap(bitmap)
        } catch (e: Exception) {
            L.e("VaultRepository", "Thumbnail generation failed: ${e.message}")
            null
        }
    }

    /**
     * 视频取第一帧生成缩略图（视频文件导入时使用）。
     */
    private fun generateVideoThumbnail(bytes: ByteArray): ByteArray? {
        var retriever: android.media.MediaMetadataRetriever? = null
        var tmp: File? = null
        return try {
            // MediaMetadataRetriever 不支持 InputStream，先落临时文件再取帧
            tmp = File(context.cacheDir, "vthumb_${System.currentTimeMillis()}.mp4")
            tmp!!.writeBytes(bytes)
            retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(tmp!!.absolutePath)
            val frame = retriever.frameAtTime ?: return null
            generateThumbnailFromBitmap(frame)
        } catch (e: Exception) {
            L.e("VaultRepository", "Video thumbnail failed: ${e.message}")
            null
        } finally {
            runCatching { retriever?.release() }
            runCatching { tmp?.delete() }
        }
    }

    /**
     * 把 Bitmap 缩放并压缩为 JPEG 字节（图片/视频缩略图共用）。
     */
    private fun generateThumbnailFromBitmap(bitmap: Bitmap): ByteArray? {
        return try {
            val scaled = if (bitmap.width > THUMBNAIL_MAX_DIMENSION || bitmap.height > THUMBNAIL_MAX_DIMENSION) {
                val ratio = minOf(
                    THUMBNAIL_MAX_DIMENSION.toFloat() / bitmap.width,
                    THUMBNAIL_MAX_DIMENSION.toFloat() / bitmap.height
                )
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            out.toByteArray()
        } catch (e: Exception) {
            L.e("VaultRepository", "Thumbnail compress failed: ${e.message}")
            null
        }
    }

    // ---------------------------------------------------------------------
    // 导出到系统相册
    // ---------------------------------------------------------------------

    /**
     * 把保险库照片/视频导出到系统相册（解密后写入 MediaStore，恢复为普通文件）。
     * Android 10+（API 29+）无需权限；更低版本需要存储权限，无权限会返回失败。
     */
    suspend fun exportPhotoToGallery(photo: VaultPhoto): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val decrypted = decryptFull(photo)
            val resolver = context.contentResolver
            val isVideo = photo.mimeType.startsWith("video/")
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, photo.originalName)
                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    photo.mimeType.ifBlank { if (isVideo) "video/mp4" else "image/jpeg" }
                )
                put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
            }
            val insertUri = resolver.insert(collection, values)
                ?: return@withContext Result.failure(IllegalStateException("无法写入相册，可能缺少存储权限"))
            resolver.openOutputStream(insertUri)?.use { out ->
                decrypted.inputStream().use { it.copyTo(out) }
            } ?: run {
                resolver.delete(insertUri, null, null)
                return@withContext Result.failure(IllegalStateException("无法写入相册"))
            }
            L.d("VaultRepository", "Exported ${photo.originalName} to gallery")
            Result.success(insertUri)
        } catch (e: Exception) {
            L.e("VaultRepository", "Export failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 按保险库照片 ID 导出到系统相册。
     */
    suspend fun exportToGallery(vaultId: String): Result<Uri> = withContext(Dispatchers.IO) {
        val entity = vaultPhotoDao.getPhotoById(vaultId)
            ?: return@withContext Result.failure(IllegalStateException("找不到照片"))
        exportPhotoToGallery(VaultPhoto.fromEntity(entity))
    }

    private fun deleteFileSafely(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}
