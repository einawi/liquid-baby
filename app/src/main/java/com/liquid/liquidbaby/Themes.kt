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

    private val midnightDark = Style("midnight-dark",
        0xFF0A0F1E.toInt(), 0xFF0E1630.toInt(), 0xFF141F44.toInt(), 0xFF8FA8FF.toInt(),
        0xFFEEF2FF.toInt(), 0xFFA6B3D6.toInt(), 0x2EFFFFFF, 0xCC2B3F7A.toInt(), 0x332B3F7A, 0x4DFFFFFF, 18, 40)
    private val midnightLight = Style("midnight-light",
        0xFFF6F8FF.toInt(), 0xFFE6ECFF.toInt(), 0xFFD6E0FF.toInt(), 0xFF3557D6.toInt(),
        0xFF101A3A.toInt(), 0xFF4A5A8A.toInt(), 0x99FFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x223557D6, 0xCCFFFFFF.toInt(), 18, 0)

    private val auroraDark = Style("aurora-dark",
        0xFF06201F.toInt(), 0xFF0B3B3A.toInt(), 0xFF1A4D5C.toInt(), 0xFF5CF2C8.toInt(),
        0xFFE9FFF8.toInt(), 0xFFA6E3D2.toInt(), 0x2EFFFFFF, 0xCC1E7A6E.toInt(), 0x335CF2C8, 0x4DFFFFFF, 20, 40)
    private val auroraLight = Style("aurora-light",
        0xFFEFFFFA.toInt(), 0xFFD9FBF1.toInt(), 0xFFBDEBE0.toInt(), 0xFF0E9E84.toInt(),
        0xFF0A2E2A.toInt(), 0xFF3D6E66.toInt(), 0x99FFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x220E9E84, 0xCCFFFFFF.toInt(), 20, 0)

    private val sunsetDark = Style("sunset-dark",
        0xFF2A1420.toInt(), 0xFF4A1E2E.toInt(), 0xFF6B2B2E.toInt(), 0xFFFFB067.toInt(),
        0xFFFFF1E6.toInt(), 0xFFF2C6A8.toInt(), 0x2EFFFFFF, 0xCCE0734D.toInt(), 0x33FFB067, 0x4DFFFFFF, 20, 40)
    private val sunsetLight = Style("sunset-light",
        0xFFFFF8F2.toInt(), 0xFFFFE9DB.toInt(), 0xFFFFD2B8.toInt(), 0xFFE0734D.toInt(),
        0xFF3A1A12.toInt(), 0xFF7A4A3A.toInt(), 0x99FFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x22E0734D, 0xCCFFFFFF.toInt(), 20, 0)

    private val roseDark = Style("rose-dark",
        0xFF1F0E17.toInt(), 0xFF331426.toInt(), 0xFF4A1B35.toInt(), 0xFFFF8FB1.toInt(),
        0xFFFFEEF4.toInt(), 0xFFE7B5C8.toInt(), 0x2EFFFFFF, 0xCCB8456F.toInt(), 0x33FF8FB1, 0x4DFFFFFF, 18, 40)
    private val roseLight = Style("rose-light",
        0xFFFFF5F8.toInt(), 0xFFFFE3EC.toInt(), 0xFFFFCBDB.toInt(), 0xFFC2446F.toInt(),
        0xFF3A0F22.toInt(), 0xFF7A3A52.toInt(), 0x99FFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x22C2446F, 0xCCFFFFFF.toInt(), 18, 0)

    private val forestDark = Style("forest-dark",
        0xFF0C1A10.toInt(), 0xFF12301C.toInt(), 0xFF1B4428.toInt(), 0xFF9BDB8A.toInt(),
        0xFFEFFAEA.toInt(), 0xFFB6D6A8.toInt(), 0x2EFFFFFF, 0xCC2F7A3E.toInt(), 0x339BDB8A, 0x4DFFFFFF, 16, 40)
    private val forestLight = Style("forest-light",
        0xFFF4FAEF.toInt(), 0xFFE0F1D6.toInt(), 0xFFC9E6B8.toInt(), 0xFF2F7A3E.toInt(),
        0xFF12301C.toInt(), 0xFF4A6B3F.toInt(), 0x99FFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x222F7A3E, 0xCCFFFFFF.toInt(), 16, 0)

    private val graphiteDark = Style("graphite-dark",
        0xFF15171A.toInt(), 0xFF1E2227.toInt(), 0xFF2A3038.toInt(), 0xFFB8C2CC.toInt(),
        0xFFF0F2F5.toInt(), 0xFFA9B2BD.toInt(), 0x2EFFFFFF, 0xCC4A5563.toInt(), 0x33B8C2CC, 0x4DFFFFFF, 12, 20)
    private val graphiteLight = Style("graphite-light",
        0xFFF7F8FA.toInt(), 0xFFE9ECF0.toInt(), 0xFFD8DDE4.toInt(), 0xFF5A6675.toInt(),
        0xFF1B1F24.toInt(), 0xFF5A6370.toInt(), 0x99FFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x225A6675, 0xCCFFFFFF.toInt(), 12, 0)

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
        0xFF0C3B35.toInt(), 0xFF3E6E66.toInt(), 0x99FFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x1F1A9E8F, 0xCCFFFFFF.toInt(), 18, 0)

    private val brightDark = Style("bright-dark",
        0xFF2A1B3D.toInt(), 0xFF3D2457.toInt(), 0xFF5A2E6B.toInt(), 0xFFFFB020.toInt(),
        0xFFFFF4E8.toInt(), 0xFFFFD9B8.toInt(), 0x33FFFFFF, 0xCCFF6B6B.toInt(), 0x33FFB020, 0x4DFFFFFF, 20, 40)

    private val brightLight = Style("bright-light",
        0xFFFFF6EE.toInt(), 0xFFFFE9DA.toInt(), 0xFFFFD9C2.toInt(), 0xFFFF6B6B.toInt(),
        0xFF2B1A12.toInt(), 0xFF7A4D3A.toInt(), 0xAAFFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0x33FF6B6B, 0xCCFFFFFF.toInt(), 20, 0)

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
            "midnight" -> if (dark) midnightDark else midnightLight
            "aurora" -> if (dark) auroraDark else auroraLight
            "sunset" -> if (dark) sunsetDark else sunsetLight
            "rose" -> if (dark) roseDark else roseLight
            "forest" -> if (dark) forestDark else forestLight
            "graphite" -> if (dark) graphiteDark else graphiteLight
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
