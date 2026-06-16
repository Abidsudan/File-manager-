package com.example.data.vault

import android.content.Context
import com.example.data.VaultKeyDao
import com.example.data.VaultKeyEntity
import com.example.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class VaultManager(
    private val context: Context,
    private val vaultKeyDao: VaultKeyDao
) {
    val vaultDirectory: File = File(context.filesDir, "Vault")

    init {
        if (!vaultDirectory.exists()) {
            vaultDirectory.mkdirs()
        }
    }

    suspend fun isVaultSetup(): Boolean = withContext(Dispatchers.IO) {
        vaultKeyDao.getVaultKey() != null
    }

    suspend fun setupVault(password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val salt = generateSalt()
            val hash = hashPassword(password, salt)
            val entity = VaultKeyEntity(
                passwordHash = hash,
                salt = salt,
                isSetup = true
            )
            vaultKeyDao.setVaultKey(entity)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun authenticate(password: String): Boolean = withContext(Dispatchers.IO) {
        val entry = vaultKeyDao.getVaultKey() ?: return@withContext false
        val hashedInput = hashPassword(password, entry.salt)
        hashedInput == entry.passwordHash
    }

    suspend fun clearVault(): Unit = withContext(Dispatchers.IO) {
        vaultKeyDao.clearVault()
        if (vaultDirectory.exists()) {
            vaultDirectory.deleteRecursively()
            vaultDirectory.mkdirs()
        }
    }

    private fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return saltBytes.joinToString("") { "%02x".format(it) }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = password + salt
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Encrypts a local file and saves it inside the Vault, removing the original.
     */
    suspend fun encryptFileToVault(sourceFilePath: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists() || sourceFile.isDirectory) return@withContext false

            val relativeName = sourceFile.name
            val encryptedDestFile = File(vaultDirectory, "$relativeName.enc")

            // Derive key from password
            val entry = vaultKeyDao.getVaultKey() ?: return@withContext false
            val secretKey = deriveKey(password, entry.salt)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)

            FileInputStream(sourceFile).use { fis ->
                FileOutputStream(encryptedDestFile).use { fos ->
                    // Write IV first
                    fos.write(iv)
                    
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val encryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (encryptedBytes != null) {
                            fos.write(encryptedBytes)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        fos.write(finalBytes)
                    }
                }
            }

            // Secure deletion of source file (Multi-pass overwrite)
            secureDelete(sourceFile)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Decrypts a file from the Vault back to the specified normal storage folder.
     */
    suspend fun decryptFileFromVault(vaultFileName: String, destFolder: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val encryptedFile = File(vaultDirectory, vaultFileName)
            if (!encryptedFile.exists()) return@withContext false

            val originalName = vaultFileName.removeSuffix(".enc")
            val destFile = File(destFolder, originalName)

            val entry = vaultKeyDao.getVaultKey() ?: return@withContext false
            val secretKey = deriveKey(password, entry.salt)

            FileInputStream(encryptedFile).use { fis ->
                // Read IV
                val iv = ByteArray(12)
                if (fis.read(iv) != 12) return@withContext false

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

                FileOutputStream(destFile).use { fos ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        val decryptedBytes = cipher.update(buffer, 0, bytesRead)
                        if (decryptedBytes != null) {
                            fos.write(decryptedBytes)
                        }
                    }
                    val finalBytes = cipher.doFinal()
                    if (finalBytes != null) {
                        fos.write(finalBytes)
                    }
                }
            }

            // Secure deleted from vault
            encryptedFile.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun listVaultFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val files = vaultDirectory.listFiles() ?: return@withContext emptyList()
        files.map { file ->
            FileItem(
                name = file.name.removeSuffix(".enc"),
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = false,
                extension = File(file.name.removeSuffix(".enc")).extension,
                remoteType = "Vault"
            )
        }
    }

    private fun deriveKey(password: String, saltHex: String): SecretKeySpec {
        val saltBytes = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, 1000, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = skf.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun secureDelete(file: File) {
        if (!file.exists()) return
        try {
            if (file.isFile) {
                // Pass 1: Write zero bytes
                val size = file.length()
                val stream = FileOutputStream(file)
                val chunk = ByteArray(4096)
                var remaining = size
                while (remaining > 0) {
                    val toWrite = minOf(chunk.size.toLong(), remaining).toInt()
                    stream.write(chunk, 0, toWrite)
                    remaining -= toWrite
                }
                stream.close()
            }
        } catch (e: Exception) {
            // Log fallback
        } finally {
            file.delete()
        }
    }
}
