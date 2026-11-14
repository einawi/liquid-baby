package com.liquid.liquidbaby

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts a finished recording in chunks, so a long video never has to fit in memory.
 * Format per chunk: 4-byte length, 12-byte IV, ciphertext with its 16-byte tag.
 * File header: "LBV1".
 */
object VideoVault {
    private const val CHUNK = 1024 * 1024
    private val MAGIC = byteArrayOf(0x4C, 0x42, 0x56, 0x31) // "LBV1"
    private val rnd = SecureRandom()

    /** Encrypts [plain] into [out] and deletes [plain]. Returns true when the encrypted file is complete. */
    fun encryptFile(plain: File, out: File): Boolean {
        return try {
            val key = Vault.storageKey()
            DataOutputStream(FileOutputStream(out).buffered()).use { dos ->
                dos.write(MAGIC)
                FileInputStream(plain).buffered().use { input ->
                    val buf = ByteArray(CHUNK)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        val iv = ByteArray(12).also { rnd.nextBytes(it) }
                        val c = Cipher.getInstance("AES/GCM/NoPadding")
                        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                        val ct = c.doFinal(buf, 0, n)
                        dos.writeInt(ct.size)
                        dos.write(iv)
                        dos.write(ct)
                    }
                }
            }
            plain.delete()
            true
        } catch (e: Exception) {
            Diag.i("Video could not be encrypted: " + e.message)
            out.delete()
            false
        }
    }

    /** Decrypts an LBV1 file into [out] (used for playback). */
    fun decryptFile(enc: File, out: File): Boolean {
        return try {
            val key = Vault.storageKey()
            DataInputStream(FileInputStream(enc).buffered()).use { dis ->
                val magic = ByteArray(4)
                dis.readFully(magic)
                if (!magic.contentEquals(MAGIC)) return false
                FileOutputStream(out).buffered().use { fos ->
                    while (true) {
                        val len = try { dis.readInt() } catch (e: java.io.EOFException) { break }
                        val iv = ByteArray(12)
                        dis.readFully(iv)
                        val ct = ByteArray(len)
                        dis.readFully(ct)
                        val c = Cipher.getInstance("AES/GCM/NoPadding")
                        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                        fos.write(c.doFinal(ct))
                    }
                }
            }
            true
        } catch (e: Exception) {
            Diag.i("Video could not be decrypted: " + e.message)
            out.delete()
            false
        }
    }
}
