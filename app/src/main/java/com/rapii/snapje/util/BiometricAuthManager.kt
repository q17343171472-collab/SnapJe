package com.rapii.snapje.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 生物识别认证管理器：封装 BiometricPrompt（指纹 / 面部解锁）。
 *
 * 只允许 BIOMETRIC_STRONG / BIOMETRIC_WEAK（指纹、面部），不包含设备 PIN，
 * 符合"不用 PIN，只用指纹/面部解锁"的需求。
 */
object BiometricAuthManager {

    /**
     * 触发系统生物识别弹窗。
     *
     * @param onSuccess 认证成功
     * @param onError   认证出错（errorCode 见 [BiometricPrompt] 常量；
     *                  -1 表示识别失败但可重试，弹窗仍保持）
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        description: String? = null,
        onSuccess: () -> Unit,
        onError: (errorCode: Int, message: String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errorCode, errString.toString())
                }

                override fun onAuthenticationFailed() {
                    // 指纹不匹配等：系统弹窗保持，可重试
                    onError(-1, "验证失败，请重试")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .apply {
                if (description != null) {
                    setDescription(description)
                }
            }
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    /**
     * 设备是否配置了可用的生物识别（已录入指纹/面部）。
     */
    fun isAvailable(activity: FragmentActivity): Boolean {
        return when (BiometricManager.from(activity).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }
    }

    /**
     * 获取生物识别可用状态码（区分"已录入" / "有硬件但未录入" / "无硬件"）。
     * 返回 BiometricManager.BIOMETRIC_* 常量。
     */
    fun getAvailability(activity: FragmentActivity): Int {
        return BiometricManager.from(activity).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
    }
}
