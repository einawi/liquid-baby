package com.liquid.liquidbaby

/** Pure logic (no Android classes) so it can be unit tested. */
object VersionUtil {

    data class Tag(val name: String, val code: Int)

    /** Release tags look like "v0.1.0-build1". */
    fun parseTag(tag: String): Tag? {
        val m = Regex("^v?(.+?)-build(\\d+)$").find(tag.trim()) ?: return null
        val code = m.groupValues[2].toIntOrNull() ?: return null
        return Tag(m.groupValues[1], code)
    }

    /** "[required]" anywhere in the release notes marks a required update. */
    fun isRequired(notes: String?): Boolean =
        notes != null && notes.contains("[required]", ignoreCase = true)

    fun apkFileName(app: String, version: String, code: Int): String =
        "$app-${version.replace('.', '_')}-build$code.apk"

    /** Should the banner / notification be shown for this remote build? */
    fun shouldOffer(remoteCode: Int, localCode: Int, skippedCode: Int, required: Boolean): Boolean {
        if (remoteCode <= localCode) return false
        return required || remoteCode != skippedCode
    }

    /** First 64-hex-digit token of a .sha256 file, lower case, or null. */
    fun parseSha256(text: String): String? {
        val m = Regex("\\b([0-9a-fA-F]{64})\\b").find(text) ?: return null
        return m.groupValues[1].lowercase()
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb)
        return String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
    }
}
