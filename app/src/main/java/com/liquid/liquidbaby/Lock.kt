package com.liquid.liquidbaby

/** Parent phone lock. Asked when the app opens and after 2 minutes in the background. */
object Lock {
    @Volatile
    var unlockedOnce = false

    @Volatile
    var leftAt = 0L

    fun pinSet(): Boolean = Prefs.getString("lockHash").isNotEmpty()

    fun needsUnlock(): Boolean =
        Prefs.getString("role") == "parent" && pinSet() &&
            (!unlockedOnce || System.currentTimeMillis() - leftAt > 120_000L)

    internal fun hash(salt: String, pin: String): String =
        Hex.encode(Crypto.sha256(salt.toByteArray(), pin.toByteArray()))

    fun setPin(pin: String) {
        val salt = Hex.encode(Crypto.random(16))
        Prefs.putString("lockSalt", salt)
        Prefs.putString("lockHash", hash(salt, pin))
    }

    /** Seconds left before the PIN can be tried again after too many wrong tries (0 when allowed). */
    fun lockedSecondsLeft(): Long {
        val until = Prefs.getString("pinLockedUntil").toLongOrNull() ?: 0L
        return maxOf(0L, (until - System.currentTimeMillis()) / 1000)
    }

    /** Five wrong tries lock the PIN for 30 seconds; each further wrong try doubles the wait (up to about 2 hours). */
    fun checkPin(pin: String): Boolean {
        if (lockedSecondsLeft() > 0) return false
        val ok = hash(Prefs.getString("lockSalt"), pin) == Prefs.getString("lockHash")
        if (ok) {
            Prefs.putInt("pinFails", 0)
            Prefs.putString("pinLockedUntil", "")
        } else {
            val fails = Prefs.getInt("pinFails", 0) + 1
            Prefs.putInt("pinFails", fails)
            if (fails >= 5) {
                val seconds = 30L shl minOf(fails - 5, 8)
                Prefs.putString("pinLockedUntil", (System.currentTimeMillis() + seconds * 1000).toString())
            }
        }
        return ok
    }

    fun clearPin() {
        Prefs.remove("lockSalt")
        Prefs.remove("lockHash")
    }

    fun unlocked() {
        unlockedOnce = true
        leftAt = System.currentTimeMillis()
    }
}
