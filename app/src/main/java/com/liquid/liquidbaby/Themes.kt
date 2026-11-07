package com.liquid.liquidbaby

/** One look: colours, glass, shape and blur. */
data class Style(
    val id: String,
    val nameAr: String,
    val nameEn: String,
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

object Themes {
    private val white = 0xFFFFFFFF.toInt()
    private val soft = 0xE6FFFFFF.toInt()

    val all = listOf(
        Style("ocean", "المحيط", "Ocean",
            0xFF0B1D2A.toInt(), 0xFF163B4E.toInt(), 0xFF1F5A6B.toInt(), 0xFF7FE8FF.toInt(),
            white, soft, 0x33FFFFFF, 0x4DFFFFFF, 0xCC1E88E5.toInt(), 0x66FFFFFF, 22, 60),
        Style("sunset", "الغروب", "Sunset",
            0xFF2B1B3D.toInt(), 0xFF6A2C5F.toInt(), 0xFFB5485D.toInt(), 0xFFFFB38A.toInt(),
            white, soft, 0x33FFFFFF, 0x4DFFFFFF, 0xCCE0633F.toInt(), 0x66FFD2B8, 26, 40),
        Style("forest", "الغابة", "Forest",
            0xFF0E2A1C.toInt(), 0xFF1B4D35.toInt(), 0xFF2E7D57.toInt(), 0xFFA8F0C6.toInt(),
            white, soft, 0x33FFFFFF, 0x4DFFFFFF, 0xCC2E7D57.toInt(), 0x66A8F0C6, 20, 60),
        Style("aurora", "الشفق", "Aurora",
            0xFF0A0F24.toInt(), 0xFF1B1F4A.toInt(), 0xFF3A2C6B.toInt(), 0xFF9AF7FF.toInt(),
            white, soft, 0x2AFFFFFF, 0x40FFFFFF, 0xCC6A4CC7.toInt(), 0x669AF7FF, 28, 80),
        Style("minimal", "بسيط فاتح", "Minimal light",
            0xFFF4F7FA.toInt(), 0xFFDDE7EF.toInt(), 0xFFC9D8E4.toInt(), 0xFF1E88E5.toInt(),
            0xFF1B2A36.toInt(), 0xFF4A5B68.toInt(), 0x99FFFFFF.toInt(), 0xCCFFFFFF.toInt(), 0xCC1E88E5.toInt(), 0x33000000, 16, 20),
        Style("neon", "نيون حاد", "Neon sharp",
            0xFF000000.toInt(), 0xFF0A1A0A.toInt(), 0xFF052E12.toInt(), 0xFF39FF88.toInt(),
            white, soft, 0x1A39FF88, 0x3339FF88, 0xCC0B6B34.toInt(), 0x9939FF88.toInt(), 4, 0)
    )

    fun current(): Style {
        val id = Prefs.getString("theme", "ocean")
        return all.firstOrNull { it.id == id } ?: all[0]
    }

    fun select(id: String) {
        Prefs.putString("theme", id)
    }
}
