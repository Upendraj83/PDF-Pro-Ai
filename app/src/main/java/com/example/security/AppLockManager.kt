package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

/**
 * Hardware & enrollment status for Biometric Authentication (Fingerprint & Face Unlock).
 */
enum class BiometricHardwareStatus {
    READY,
    NOT_ENROLLED,
    NO_HARDWARE,
    UNAVAILABLE
}

/**
 * Manages:
 * 1. Application-level lock with PIN passcode & Biometric Authentication (Fingerprint or Face Unlock).
 * 2. Sensitive PDF document protection with Biometric verification before opening.
 * 3. Biometric password vault for encrypted PDFs (unlock password-protected PDFs with fingerprint next time).
 */
class AppLockManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_security_prefs", Context.MODE_PRIVATE)

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Reactive set of document URIs protected with Biometric Authentication
    private val _protectedDocuments = MutableStateFlow<Set<String>>(loadProtectedDocuments())
    val protectedDocuments: StateFlow<Set<String>> = _protectedDocuments.asStateFlow()

    private var lastBackgroundTimeMs: Long = 0L

    init {
        // If app lock is enabled and has a configured PIN, start locked on cold launch
        if (isLockConfigured()) {
            _isAppLocked.value = true
        }
    }

    // ---------------------------------------------------------
    // App Lock Core
    // ---------------------------------------------------------

    fun isLockConfigured(): Boolean {
        val enabled = prefs.getBoolean(KEY_LOCK_ENABLED, false)
        val hash = prefs.getString(KEY_PIN_HASH, "") ?: ""
        return enabled && hash.isNotBlank()
    }

    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_LOCK_ENABLED, false)

    fun getTimeoutSeconds(): Long = prefs.getLong(KEY_TIMEOUT_SECONDS, 0L)

    fun isBiometricsEnabled(): Boolean = prefs.getBoolean(KEY_USE_BIOMETRICS, true)

    fun setLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
        if (!enabled) {
            _isAppLocked.value = false
        }
    }

    fun setTimeoutSeconds(seconds: Long) {
        prefs.edit().putLong(KEY_TIMEOUT_SECONDS, seconds).apply()
    }

    fun setBiometricsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USE_BIOMETRICS, enabled).apply()
    }

    fun saveNewPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit()
            .putString(KEY_PIN_HASH, hash)
            .putBoolean(KEY_LOCK_ENABLED, true)
            .apply()
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .putBoolean(KEY_LOCK_ENABLED, false)
            .apply()
        _isAppLocked.value = false
    }

    fun verifyPin(enteredPin: String): Boolean {
        val storedHash = prefs.getString(KEY_PIN_HASH, "") ?: ""
        if (storedHash.isBlank()) return true

        val enteredHash = hashPin(enteredPin)
        val matches = storedHash.equals(enteredHash, ignoreCase = true)
        if (matches) {
            _isAppLocked.value = false
        }
        return matches
    }

    fun unlockViaBiometric() {
        _isAppLocked.value = false
    }

    fun forceLock() {
        if (isLockConfigured()) {
            _isAppLocked.value = true
        }
    }

    fun onAppBackgrounded() {
        lastBackgroundTimeMs = System.currentTimeMillis()
    }

    fun onAppForegrounded() {
        if (!isLockConfigured()) return
        val timeoutMs = getTimeoutSeconds() * 1000L
        val elapsed = System.currentTimeMillis() - lastBackgroundTimeMs

        if (lastBackgroundTimeMs > 0 && elapsed >= timeoutMs) {
            _isAppLocked.value = true
        }
    }

    // ---------------------------------------------------------
    // Biometric Hardware & Enrollment Capabilities
    // ---------------------------------------------------------

    fun getBiometricStatus(): BiometricHardwareStatus {
        val biometricManager = BiometricManager.from(context)
        return when (
            biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricHardwareStatus.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricHardwareStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricHardwareStatus.NO_HARDWARE
            else -> BiometricHardwareStatus.UNAVAILABLE
        }
    }

    fun canAuthenticateWithBiometrics(): Boolean {
        return getBiometricStatus() == BiometricHardwareStatus.READY
    }

    fun getBiometricStatusDescription(): String {
        return when (getBiometricStatus()) {
            BiometricHardwareStatus.READY -> "Fingerprint & Face unlock ready"
            BiometricHardwareStatus.NOT_ENROLLED -> "No fingerprint or face enrolled in Android Settings"
            BiometricHardwareStatus.NO_HARDWARE -> "Biometric hardware not present"
            BiometricHardwareStatus.UNAVAILABLE -> "Biometric sensors temporarily unavailable"
        }
    }

    // ---------------------------------------------------------
    // Unified Biometric Prompts
    // ---------------------------------------------------------

    fun promptBiometrics(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        promptBiometrics(
            activity = activity,
            title = "Unlock PDF ALL IN ONE",
            subtitle = "Confirm identity (fingerprint or face) to open app",
            negativeButtonText = "Use PIN",
            onSuccess = {
                unlockViaBiometric()
                onSuccess()
            },
            onError = onError
        )
    }

    fun promptBiometrics(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isBiometricsEnabled() || !canAuthenticateWithBiometrics()) {
            onError("Biometrics not available or disabled")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("Fingerprint or face not recognized")
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()

        prompt.authenticate(promptInfo)
    }

    fun testBiometrics(
        activity: FragmentActivity?,
        onResult: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        if (activity == null) {
            onResult(false, "Activity not available")
            return
        }
        promptBiometrics(
            activity = activity,
            title = "Test Biometric Authentication",
            subtitle = "Touch the fingerprint sensor or glance at the camera",
            negativeButtonText = "Cancel",
            onSuccess = { onResult(true, "Biometric authentication successful!") },
            onError = { err -> onResult(false, err) }
        )
    }

    // ---------------------------------------------------------
    // Sensitive Document Protection via Biometrics
    // ---------------------------------------------------------

    private fun loadProtectedDocuments(): Set<String> {
        return prefs.getStringSet(KEY_PROTECTED_DOCS, emptySet()) ?: emptySet()
    }

    fun isDocumentBiometricProtected(uri: String): Boolean {
        return _protectedDocuments.value.contains(uri)
    }

    fun setDocumentBiometricProtected(uri: String, protected: Boolean) {
        val current = _protectedDocuments.value.toMutableSet()
        if (protected) {
            current.add(uri)
        } else {
            current.remove(uri)
        }
        prefs.edit().putStringSet(KEY_PROTECTED_DOCS, current).apply()
        _protectedDocuments.value = current
    }

    fun toggleDocumentBiometricProtection(uri: String): Boolean {
        val newState = !isDocumentBiometricProtected(uri)
        setDocumentBiometricProtected(uri, newState)
        return newState
    }

    fun promptBiometricsForDocument(
        activity: FragmentActivity,
        fileName: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        promptBiometrics(
            activity = activity,
            title = "Unlock Sensitive PDF",
            subtitle = "Scan your fingerprint or face to open \"$fileName\"",
            negativeButtonText = "Cancel",
            onSuccess = onSuccess,
            onError = onError
        )
    }

    // ---------------------------------------------------------
    // Biometric Password Vault for Encrypted PDFs
    // ---------------------------------------------------------

    private fun docKey(uri: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(uri.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return KEY_PREFIX_DOC_PWD + hash
    }

    fun saveDocumentPasswordForBiometrics(uri: String, password: String) {
        val encoded = obfuscateString(password)
        prefs.edit().putString(docKey(uri), encoded).apply()
    }

    fun getSavedDocumentPasswordForBiometrics(uri: String): String? {
        val encoded = prefs.getString(docKey(uri), null) ?: return null
        return deobfuscateString(encoded)
    }

    fun removeDocumentPasswordForBiometrics(uri: String) {
        prefs.edit().remove(docKey(uri)).apply()
    }

    fun hasSavedDocumentPasswordForBiometrics(uri: String): Boolean {
        return prefs.contains(docKey(uri))
    }

    // ---------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salt = "PDF_ALL_IN_ONE_SALT_2026"
        val bytes = digest.digest((pin + salt).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateVaultSecretKey(): javax.crypto.SecretKey {
        return try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val existingEntry = keyStore.getEntry(KEYSTORE_ALIAS_DOC_VAULT, null) as? java.security.KeyStore.SecretKeyEntry
            if (existingEntry != null) {
                existingEntry.secretKey
            } else {
                val keyGenerator = javax.crypto.KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS_DOC_VAULT,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Fallback 256-bit AES key derived via SHA-256 when AndroidKeyStore provider is unavailable (e.g. local JVM unit tests)
            val digest = MessageDigest.getInstance("SHA-256")
            val keyBytes = digest.digest(("PDF_VAULT_AES256_KEY_" + context.packageName).toByteArray(Charsets.UTF_8))
            javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        }
    }

    private fun obfuscateString(value: String): String {
        return try {
            val secretKey = getOrCreateVaultSecretKey()
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            val iv = try {
                cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
                cipher.iv
            } catch (_: Exception) {
                val generatedIv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
                cipher.init(
                    javax.crypto.Cipher.ENCRYPT_MODE,
                    secretKey,
                    javax.crypto.spec.GCMParameterSpec(128, generatedIv)
                )
                generatedIv
            }
            val cipherText = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + cipherText.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherText, 0, combined, 1 + iv.size, cipherText.size)
            "GCM1:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            "B64:" + Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
    }

    private fun deobfuscateString(encoded: String): String? {
        return try {
            if (encoded.startsWith("GCM1:")) {
                val payload = Base64.decode(encoded.removePrefix("GCM1:"), Base64.NO_WRAP)
                val ivLength = payload[0].toInt() and 0xFF
                val iv = payload.copyOfRange(1, 1 + ivLength)
                val cipherText = payload.copyOfRange(1 + ivLength, payload.size)
                val secretKey = getOrCreateVaultSecretKey()
                val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    javax.crypto.Cipher.DECRYPT_MODE,
                    secretKey,
                    javax.crypto.spec.GCMParameterSpec(128, iv)
                )
                val plainBytes = cipher.doFinal(cipherText)
                String(plainBytes, Charsets.UTF_8)
            } else {
                val raw = encoded.removePrefix("B64:")
                val bytes = Base64.decode(raw, Base64.NO_WRAP)
                String(bytes, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val KEY_LOCK_ENABLED = "pref_lock_enabled"
        private const val KEY_PIN_HASH = "pref_pin_hash"
        private const val KEY_TIMEOUT_SECONDS = "pref_lock_timeout_sec"
        private const val KEY_USE_BIOMETRICS = "pref_use_biometrics"
        private const val KEY_PROTECTED_DOCS = "pref_biometric_protected_docs"
        private const val KEY_PREFIX_DOC_PWD = "pref_doc_pwd_"
        private const val KEYSTORE_ALIAS_DOC_VAULT = "pdf_all_in_one_doc_vault_aes256"
    }
}
