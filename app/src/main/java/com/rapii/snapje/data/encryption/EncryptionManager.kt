package com.rapii.snapje.data.encryption

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.rapii.snapje.util.L
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密管理器：负责图片文件的 AES-256-GCM 加密 / 解密。
 *
 * 安全模型：
 * - 数据加密密钥（MasterKey）由 Android Keystore 保护（硬件级，无法被导出）。
 * - 所有密文只写入 App 沙盒目录（filesDir/vault），系统相册 / 其他 App 无法读取或识别。
 * - 解密后的明文只存在于内存或 cacheDir 临时文件，临时文件用完后由调用方立即删除。
 * - App 启动时先通过生物识别（指纹/面部）解锁（见 MainActivity / AuthScreen），
 *   只有解锁通过后才会触发本类的解密操作，实现"只有生物识别通过后才能解密密钥"。
 */
@Singleton
class EncryptionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Keystore 保护的 MasterKey（AES-256-GCM）。
     * lazy 初始化：首次使用时才创建，避免 App 启动时不必要的 Keystore 操作。
     */
    private val masterKey: MasterKey by lazy {
        L.d("EncryptionManager", "Creating Keystore-backed MasterKey")
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /**
     * 加密文件：明文 input -> 密文 output（AES-256-GCM，带认证标签）。
     * 完成后调用方负责删除原始文件。
     */
    @Suppress("DEPRECATION") // security-crypto 的 EncryptedFile API（任务清单指定方案）
    suspend fun encryptFile(input: File, output: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!input.exists()) {
                return@withContext Result.failure(IllegalStateException("Source file not found: ${input.path}"))
            }
            output.parentFile?.mkdirs()

            val encryptedFile = EncryptedFile.Builder(
                context,
                output,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            input.inputStream().use { inputStream ->
                encryptedFile.openFileOutput().use { outputStream ->
                    inputStream.copyTo(outputStream, bufferSize = DEFAULT_BUFFER_SIZE)
                }
            }
            L.d("EncryptionManager", "Encrypted ${input.name} -> ${output.name} (${output.length()} bytes)")
            Result.success(Unit)
        } catch (e: Exception) {
            L.e("EncryptionManager", "Encryption failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 流式加密：把 [input] 的内容加密写入 [output]（分块拷贝，避免整包读入内存）。
     */
    @Suppress("DEPRECATION")
    suspend fun encryptStream(input: java.io.InputStream, output: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            output.parentFile?.mkdirs()
            val encryptedFile = EncryptedFile.Builder(
                context,
                output,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileOutput().use { outputStream ->
                input.copyTo(outputStream, bufferSize = DEFAULT_BUFFER_SIZE)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            L.e("EncryptionManager", "Stream encryption failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 加密字节数组（用于缩略图等小数据）。
     */
    @Suppress("DEPRECATION")
    suspend fun encryptBytes(data: ByteArray, output: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            output.parentFile?.mkdirs()
            val encryptedFile = EncryptedFile.Builder(
                context,
                output,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileOutput().use { it.write(data) }
            Result.success(Unit)
        } catch (e: Exception) {
            L.e("EncryptionManager", "Encrypt bytes failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 解密到内存：加密文件 -> ByteArray。
     * 明文只存在于内存，不写回磁盘。
     */
    @Suppress("DEPRECATION")
    suspend fun decryptToBytes(encrypted: File): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            if (!encrypted.exists()) {
                return@withContext Result.failure(IllegalStateException("Encrypted file not found: ${encrypted.path}"))
            }
            val encryptedFile = EncryptedFile.Builder(
                context,
                encrypted,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            val bytes = encryptedFile.openFileInput().use { it.readBytes() }
            Result.success(bytes)
        } catch (e: Exception) {
            L.e("EncryptionManager", "Decryption failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 解密到临时文件：加密文件 -> output（明文临时文件，显示用）。
     * 调用方负责在使用完毕后 delete() 临时文件。
     */
    @Suppress("DEPRECATION")
    suspend fun decryptToFile(encrypted: File, output: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!encrypted.exists()) {
                return@withContext Result.failure(IllegalStateException("Encrypted file not found: ${encrypted.path}"))
            }
            output.parentFile?.mkdirs()
            val encryptedFile = EncryptedFile.Builder(
                context,
                encrypted,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileInput().use { inputStream ->
                output.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream, bufferSize = DEFAULT_BUFFER_SIZE)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            L.e("EncryptionManager", "Decrypt to file failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 验证设备生物识别（指纹 / 面部）是否可用。
     */
    fun isBiometricAvailable(): Boolean {
        return when (BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }
}
