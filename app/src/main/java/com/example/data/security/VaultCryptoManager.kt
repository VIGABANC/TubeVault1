package com.example.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultCryptoManager {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "TubeVaultMasterKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BIT = 128
        private const val VERSION_HEADER_BYTE: Byte = 0x01
    }

    private val isAndroidKeyStoreAvailable: Boolean = try {
        KeyStore.getInstance(KEYSTORE_PROVIDER)
        true
    } catch (e: Exception) {
        false
    }

    private val keyStore: KeyStore = try {
        if (isAndroidKeyStoreAvailable) {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        } else {
            KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        }
    } catch (e: Exception) {
        KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
    }

    private val testFallbackKey: SecretKey by lazy {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        keyGen.generateKey()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        if (!isAndroidKeyStoreAvailable) {
            return testFallbackKey
        }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptBytes(plaintext: ByteArray): ByteArray {
        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        // Format: [Version(1 byte)][IV Length(1 byte)][IV][Ciphertext]
        val output = ByteArray(1 + 1 + iv.size + ciphertext.size)
        output[0] = VERSION_HEADER_BYTE
        output[1] = iv.size.toByte()
        System.arraycopy(iv, 0, output, 2, iv.size)
        System.arraycopy(ciphertext, 0, output, 2 + iv.size, ciphertext.size)
        return output
    }

    fun decryptBytes(encryptedData: ByteArray): ByteArray {
        require(encryptedData.size > 2) { "Encrypted data too short" }
        val version = encryptedData[0]
        require(version == VERSION_HEADER_BYTE) { "Unsupported encryption version: $version" }
        val ivLen = encryptedData[1].toInt() and 0xFF
        val iv = ByteArray(ivLen)
        System.arraycopy(encryptedData, 2, iv, 0, ivLen)

        val ciphertextOffset = 2 + ivLen
        val ciphertext = ByteArray(encryptedData.size - ciphertextOffset)
        System.arraycopy(encryptedData, ciphertextOffset, ciphertext, 0, ciphertext.size)

        val secretKey = getOrCreateMasterKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    fun encryptString(plainText: String): String {
        val encrypted = encryptBytes(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decryptString(encodedCipherText: String): String {
        val decoded = Base64.decode(encodedCipherText, Base64.NO_WRAP)
        val decryptedBytes = decryptBytes(decoded)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun encryptFile(inputFile: File, outputFile: File): Boolean {
        return try {
            val secretKey = getOrCreateMasterKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv

            inputFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    // Write version header and IV
                    output.write(VERSION_HEADER_BYTE.toInt())
                    output.write(iv.size)
                    output.write(iv)

                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val encryptedBlock = cipher.update(buffer, 0, bytesRead)
                        if (encryptedBlock != null) {
                            output.write(encryptedBlock)
                        }
                    }
                    val finalBlock = cipher.doFinal()
                    if (finalBlock != null) {
                        output.write(finalBlock)
                    }
                    output.flush()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun decryptFileToStream(encryptedFile: File, outputStream: OutputStream): Boolean {
        return try {
            encryptedFile.inputStream().use { input ->
                val version = input.read()
                if (version != VERSION_HEADER_BYTE.toInt()) return false
                val ivLen = input.read()
                if (ivLen <= 0) return false
                val iv = ByteArray(ivLen)
                val readIv = input.read(iv)
                if (readIv != ivLen) return false

                val secretKey = getOrCreateMasterKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val decryptedBlock = cipher.update(buffer, 0, bytesRead)
                    if (decryptedBlock != null) {
                        outputStream.write(decryptedBlock)
                    }
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null) {
                    outputStream.write(finalBlock)
                }
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun verifyEncryptedFile(encryptedFile: File, expectedLength: Long? = null): Boolean {
        return try {
            if (!encryptedFile.exists() || encryptedFile.length() < 16) return false
            var totalDecryptedBytes = 0L
            encryptedFile.inputStream().use { input ->
                val version = input.read()
                if (version != VERSION_HEADER_BYTE.toInt()) return false
                val ivLen = input.read()
                if (ivLen <= 0) return false
                val iv = ByteArray(ivLen)
                val readIv = input.read(iv)
                if (readIv != ivLen) return false

                val secretKey = getOrCreateMasterKey()
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val decryptedBlock = cipher.update(buffer, 0, bytesRead)
                    if (decryptedBlock != null) {
                        totalDecryptedBytes += decryptedBlock.size
                    }
                }
                val finalBlock = cipher.doFinal()
                if (finalBlock != null) {
                    totalDecryptedBytes += finalBlock.size
                }
            }
            if (expectedLength != null) {
                totalDecryptedBytes == expectedLength
            } else {
                totalDecryptedBytes >= 0L
            }
        } catch (e: Exception) {
            false
        }
    }
}
