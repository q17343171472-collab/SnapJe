package com.rapii.snapje.data

import android.net.Uri
import com.rapii.snapje.data.local.VaultPhotoEntity
import java.util.UUID

/**
 * 保险库照片领域模型。
 * 对应加密存储的照片元数据（内容为密文，不可直接展示）。
 */
data class VaultPhoto(
    val id: String,              // UUID
    val originalName: String,    // 原始文件名
    val bucketId: Long,          // 保险库相册 ID
    val bucketName: String,      // 保险库相册名
    val dateTaken: Long,         // 拍摄/导入时间
    val size: Long,
    val mimeType: String,
    val encryptedPath: String,
    val thumbnailPath: String = ""
) {
    /**
     * 稳定的 Long 代理 ID（用于现有 UI 组件的 key 与过滤逻辑）。
     * 由 UUID 的 128 位做异或得到 64 位值，冲突概率可忽略。
     */
    val longId: Long
        get() = runCatching {
            val uuid = UUID.fromString(id)
            uuid.mostSignificantBits xor uuid.leastSignificantBits
        }.getOrDefault(id.hashCode().toLong())

    /**
     * 转换为 UI 层使用的 PhotoItem。
     * @param displayUri 解密后的临时文件 URI（缩略图或原图）
     */
    fun toPhotoItem(displayUri: Uri): PhotoItem {
        return PhotoItem(
            id = longId,
            uri = displayUri,
            displayName = originalName,
            dateTaken = dateTaken,
            bucketId = bucketId,
            bucketName = bucketName,
            size = size,
            mimeType = mimeType,
            vaultId = id
        )
    }

    fun toEntity(): VaultPhotoEntity {
        return VaultPhotoEntity(
            id = id,
            originalName = originalName,
            bucketId = bucketId,
            bucketName = bucketName,
            dateTaken = dateTaken,
            size = size,
            mimeType = mimeType,
            encryptedPath = encryptedPath,
            thumbnailPath = thumbnailPath
        )
    }

    companion object {
        fun fromEntity(entity: VaultPhotoEntity): VaultPhoto {
            return VaultPhoto(
                id = entity.id,
                originalName = entity.originalName,
                bucketId = entity.bucketId,
                bucketName = entity.bucketName,
                dateTaken = entity.dateTaken,
                size = entity.size,
                mimeType = entity.mimeType,
                encryptedPath = entity.encryptedPath,
                thumbnailPath = entity.thumbnailPath
            )
        }
    }
}
