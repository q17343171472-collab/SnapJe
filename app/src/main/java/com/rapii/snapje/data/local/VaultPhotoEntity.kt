package com.rapii.snapje.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room 实体：保险库加密照片元数据。
 *
 * 实际图片内容以密文形式存放在 App 沙盒目录（filesDir/vault/）下的 .enc 文件中，
 * 这里只保存元数据。系统相册无法扫描到沙盒内的密文文件。
 *
 * 字段说明（相对任务清单的补充）：
 * - [bucketName]：相册名（任务清单只给了 bucketId，但首页分组展示需要名字，故补充该字段）。
 */
@Entity(
    tableName = "vault_photos",
    indices = [
        Index(value = ["bucketId"]),
        Index(value = ["dateTaken"]),
        Index(value = ["bucketId", "dateTaken"])
    ]
)
data class VaultPhotoEntity(
    @PrimaryKey val id: String,              // UUID，用于密文文件命名与删除定位
    val originalName: String,                // 原始文件名（加密存储）
    val bucketId: Long,                      // 所属保险库相册 ID
    val bucketName: String,                  // 保险库相册名（自定义相册名）
    val dateTaken: Long,                     // 拍摄/导入时间
    val size: Long,                          // 原始文件大小
    val mimeType: String,                    // 图片类型
    val encryptedPath: String,               // 加密文件路径（App 沙盒内）
    @ColumnInfo(defaultValue = "''")
    val thumbnailPath: String = ""          // 加密缩略图路径
)
