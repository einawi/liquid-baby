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

    private val tealDark = Style("teal-dark",
        0xFF0B2F36.toInt(), 0xFF0F3E47.toInt(), 0xFF145059.toInt(), 0xFF7FE3D6.toInt(),
        0xFFE6FBF7.toInt(), 0xFF9FD9CF.toInt(), 0x26FFFFFF, 0xCC1F7A70.toInt(), 0x3300C9B0, 0x4DFFFFFF, 18, 40)

    private val tealLight = Style("teal-light",
        0xFFF2FBFA.toInt(), 0xFFE0F4F1.toInt(), 0xFFCDEBE6.toInt(), 0xFF1A9E8F.toInt(),
        0xFF0C3B35.toInt(), 0xFF3E6E66.toInt(), 0x99FFFFFF.toInt(), 0xCC1A9E8F.toInt(), 0x1F1A9E8F, 0xCCFFFFFF.toInt(), 18, 0)

    private val brightDark = Style("bright-dark",
        0xFF2A1B3D.toInt(), 0xFF3D2457.toInt(), 0xFF5A2E6B.toInt(), 0xFFFFB020.toInt(),
        0xFFFFF4E8.toInt(), 0xFFFFD9B8.toInt(), 0x33FFFFFF, 0xCCFF6B6B.toInt(), 0x33FFB020, 0x4DFFFFFF, 20, 40)

    private val brightLight = Style("bright-light",
        0xFFFFF6EE.toInt(), 0xFFFFE9DA.toInt(), 0xFFFFD9C2.toInt(), 0xFFFF6B6B.toInt(),
        0xFF2B1A12.toInt(), 0xFF7A4D3A.toInt(), 0xAAFFFFFF.toInt(), 0xFFFF6B6B.toInt(), 0x33FF6B6B, 0xCCFFFFFF.toInt(), 20, 0)

    fun mode(): String = Prefs.getString("themeMode", "system")

    fun style(): String = Prefs.getString("themeStyle", "minimal")

    fun isDark(): Boolean = when (mode()) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    fun current(): Style {
        val dark = isDark()
        return when (style()) {
            "teal" -> if (dark) tealDark else tealLight
            "bright" -> if (dark) brightDark else brightLight
            "minimal" -> if (dark) minimalDark else minimalLight
            else -> if (dark) classicDark else classicLight
        }
    }

    fun setMode(m: String) {
        Prefs.putString("themeMode", m)
    }

    fun setStyle(s: String) {
        Prefs.putString("themeStyle", s)
    }
}
