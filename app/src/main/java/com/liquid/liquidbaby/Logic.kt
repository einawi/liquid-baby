package com.liquid.liquidbaby

/** Which recordings the child phone should delete so that only the newest [keep] remain. */
object Retention {
    private fun ts(name: String): Long = name.removeSuffix(".wav").toLongOrNull() ?: 0L

    /** Names to delete, oldest first. */
    fun toDelete(names: List<String>, keep: Int): List<String> {
        if (names.size <= keep) return emptyList()
        return names.sortedBy { ts(it) }.dropLast(keep)
    }
}

/** Lets a message through at most once per interval for each key (for lines that repeat every second). */
class RateGate {
    private val last = HashMap<String, Long>()

    @Synchronized
    fun allow(key: String, now: Long, everyMs: Long): Boolean {
        val prev = last[key]
        if (prev != null && now - prev < everyMs) return false
        last[key] = now
        return true
    }
}
