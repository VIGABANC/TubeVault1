package com.example.ui.components

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHelper {

    fun authenticate(
        activity: FragmentActivity,
        title: String = "TubeVault Private Vault",
        subtitle: String = "Authenticate to access private vault",
        negativeButtonText: String = "Cancel",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            return
        }

        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val biometricManager = BiometricManager.from(activity)
        val canAuthStatus = biometricManager.canAuthenticate(authenticators)

        when (canAuthStatus) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // proceed to prompt
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                // Check if device credential alone is supported
                val credStatus = biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                if (credStatus == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                    onError("Aucun verrouillage configuré. Veuillez définir un code ou schéma dans les paramètres.")
                    return
                }
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                onError("Aucun code ou biométrie configuré. Veuillez activer le verrouillage de l'appareil.")
                return
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                // Allow fallback prompt with device credentials
            }
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
                onError("Mise à jour de sécurité requise pour la biométrie.")
                return
            }
            else -> {
                // Attempt prompt with device credential fallback
            }
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onFailed()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
