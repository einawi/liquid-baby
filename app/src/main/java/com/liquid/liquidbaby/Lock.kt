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

    private fun hash(salt: String, pin: String): String =
        Hex.encode(Crypto.sha256(salt.toByteArray(), pin.toByteArray()))

    fun setPin(pin: String) {
        val salt = Hex.encode(Crypto.random(16))
        Prefs.putString("lockSalt", salt)
        Prefs.putString("lockHash", hash(salt, pin))
    }

    fun checkPin(pin: String): Boolean = hash(Prefs.getString("lockSalt"), pin) == Prefs.getString("lockHash")

    fun clearPin() {
        Prefs.remove("lockSalt")
        Prefs.remove("lockHash")
    }

    fun unlocked() {
        unlockedOnce = true
        leftAt = System.currentTimeMillis()
    }
}
