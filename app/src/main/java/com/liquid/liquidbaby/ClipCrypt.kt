package com.liquid.liquidbaby

import android.content.Context
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Clips are stored encrypted on the phone (AES-GCM, key in the Android Keystore).
 * Layout: "LBE1" + 12-byte IV + ciphertext + 16-byte tag. Plain WAV clips from older builds still open.
 */
object ClipCrypt {
    private val MAGIC = byteArrayOf(0x4C, 0x42, 0x45, 0x31) // "LBE1"
    private const val IV_LEN = 12
    private const val OVERHEAD = 4 + IV_LEN + 16

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun hasEncryptedHeader(f: File): Boolean = try {
        f.inputStream().use { input ->
            val h = ByteArray(MAGIC.size)
            input.read(h) == MAGIC.size && h.contentEquals(MAGIC)
        }
    } catch (e: Exception) {
        false
    }

    /** Encrypts a whole WAV. Returns null when the Keystore is unavailable: the caller keeps the plain clip. */
    fun seal(plain: ByteArray): ByteArray? = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, Vault.storageKey())
        val iv = c.iv
        val ct = c.doFinal(plain)
        MAGIC + iv + ct
    } catch (e: Exception) {
        Diag.limited("clip-seal", "Clip kept without encryption: " + (e.message ?: "Keystore not available"), 600_000L)
        null
    }

    /** Decrypts an encrypted clip. Plain clips are returned as they are. */
    fun open(bytes: ByteArray): ByteArray {
        if (!isEncrypted(bytes)) return bytes
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, Vault.storageKey(), GCMParameterSpec(128, bytes.copyOfRange(4, 4 + IV_LEN)))
            c.doFinal(bytes.copyOfRange(4 + IV_LEN, bytes.size))
        } catch (e: Exception) {
            Diag.i("A recording could not be decrypted")
            ByteArray(0)
        }
    }

    /** The WAV bytes of a clip file, decrypted if needed. */
    fun readPlain(f: File): ByteArray = try {
        open(f.readBytes())
    } catch (e: Exception) {
        ByteArray(0)
    }

    /** Length of the WAV inside the file, so durations stay correct for encrypted clips. */
    fun plainSize(f: File): Long {
        val len = f.length()
        return if (hasEncryptedHeader(f)) maxOf(0L, len - OVERHEAD) else len
    }

    /** Turns a plain clip file into an encrypted one, in place. Already encrypted files are left alone. */
    fun encryptInPlace(f: File): Boolean {
        if (!f.exists() || hasEncryptedHeader(f)) return true
        val sealed = seal(f.readBytes()) ?: return false
        val tmp = File(f.parentFile, f.name + ".enc")
        tmp.writeBytes(sealed)
        return tmp.renameTo(f)
    }

    /**
     * A plain WAV path that MediaPlayer can open. Encrypted clips are decrypted into the app cache
     * (only the last one is kept there). Plain clips return their own path.
     */
    fun playablePath(ctx: Context, f: File): String {
        if (!hasEncryptedHeader(f)) return f.absolutePath
        val dir = File(ctx.cacheDir, "play")
        dir.listFiles()?.forEach { it.delete() }
        dir.mkdirs()
        val out = File(dir, f.name)
        out.writeBytes(readPlain(f))
        return out.absolutePath
    }
}
