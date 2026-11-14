package com.liquid.liquidbaby

/** One look: colours, glass, shape and blur. */
data class Style(
    val id: String,
    val top: Int,
    val mid: Int,
    val bottom: Int,
    val accent: Int,
    val text: Int,
    val sub: Int,
    val glass: Int,
    val button: Int,
    val banner: Int,
    val edge: Int,
    val radius: Int,
    val blur: Int
)

/**
 * Looks: style (classic = glass gradient, minimal = flat colours) and mode (system / dark / light).
 * Settings chooses both. Default: minimal (flat), follows the phone.
 */
object Themes {
    /** Set by screen() from the phone's current dark/light setting. */
    @Volatile
    var systemDark = true

    private val white = 0xFFFFFFFF.toInt()
    private val soft = 0xE6FFFFFF.toInt()

    private val classicDark = Style("classic-dark",
        0xFF0B1D2A.toInt(), 0xFF163B4E.toInt(), 0xFF1F5A6B.toInt(), 0xFF7FE8FF.toInt(),
        white, soft, 0x33FFFFFF, 0x4DFFFFFF, 0xCC1E88E5.toInt(), 0x66FFFFFF, 22, 60)

    private val classicLight = Style("classic-light",
        0xFFF4F7FA.toInt(), 0xFFDDE7EF.toInt(), 0xFFC9D8E4.toInt(), 0xFF1E88E5.toInt(),
        0xFF1B2A36.toInt(), 0xFF4A5B68.toInt(), 0x99FFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0xCC1E88E5.toInt(), 0x33000000, 22, 20)

    private val minimalDark = Style("minimal-dark",
        0xFF121518.toInt(), 0xFF121518.toInt(), 0xFF121518.toInt(), 0xFF8AB4F8.toInt(),
        0xFFE8EAED.toInt(), 0xFF9AA0A6.toInt(), 0xFF1C2026.toInt(), 0xFF23282F.toInt(), 0xFF2B5FA8.toInt(), 0x33FFFFFF, 10, 0)

    private val minimalLight = Style("minimal-light",
        0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF1E66D0.toInt(),
        0xFF1B1F24.toInt(), 0xFF5F6368.toInt(), 0xFFF2F4F7.toInt(), 0xFFEEF1F5.toInt(), 0xFF1E66D0.toInt(), 0x1F000000, 10, 0)

    fun mode(): String = Prefs.getString("themeMode", "system")

    fun style(): String = Prefs.getString("themeStyle", "minimal")

    fun isDark(): Boolean = when (mode()) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    fun current(): Style {
        val minimal = style() == "minimal"
        return when {
            minimal && isDark() -> minimalDark
            minimal -> minimalLight
            isDark() -> classicDark
            else -> classicLight
        }
    }

    fun setMode(m: String) {
        Prefs.putString("themeMode", m)
    }

    fun setStyle(s: String) {
        Prefs.putString("themeStyle", s)
    }
}
