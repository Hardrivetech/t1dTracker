package com.hardrivetech.t1dtracker

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object BiometricAuth {
    fun isAvailable(context: Context): Boolean {
        return try {
            val bm = BiometricManager.from(context)
            bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    suspend fun authenticate(activity: FragmentActivity, title: String, subtitle: String? = null): Boolean {
        return suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val builder = BiometricPrompt.PromptInfo.Builder().setTitle(title)
            if (subtitle != null) builder.setSubtitle(subtitle)
            // Allow device credential as fallback
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            val promptInfo = builder.build()

            val prompt = BiometricPrompt(
                activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onAuthenticationFailed() {
                        // allow retry; do nothing here
                    }
                }
            )

            prompt.authenticate(promptInfo)

            cont.invokeOnCancellation {
                // no-op
            }
        }
    }
}
