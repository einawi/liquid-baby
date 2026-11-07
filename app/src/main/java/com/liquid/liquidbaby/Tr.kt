package com.liquid.liquidbaby

/**
 * Arabic interface. Known English texts (and their known prefixes) are translated; anything not in the table
 * is shown as it is. Set in Settings > Language. Default: Arabic.
 */
object Tr {
    fun isAr(): Boolean = Prefs.getString("lang", "ar") != "en"

    fun toggle() {
        Prefs.putString("lang", if (isAr()) "en" else "ar")
    }

    private val exact = mapOf(
        "Liquid Baby" to "ليكويد بيبي",
        "Settings" to "الإعدادات",
        "Parent unit" to "وحدة الأبوين",
        "Child unit" to "وحدة الطفل",
        "Choose what this phone will be:" to "اختر دور هذا الجوال:",
        "Use this phone as the child unit" to "استخدم هذا الجوال كوحدة طفل",
        "Use this phone as a parent unit" to "استخدم هذا الجوال كوحدة أبوين",
        "Open the child screen" to "افتح شاشة وحدة الطفل",
        "Open the parent screen" to "افتح شاشة وحدة الأبوين",
        "This phone is the CHILD unit." to "هذا الجوال هو وحدة الطفل.",
        "This phone is a PARENT unit." to "هذا الجوال هو وحدة الأبوين.",
        "Pair with the child phone" to "اقرن مع جوال الطفل",
        "First, on the child phone tap 'Allow a new parent phone to pair'." to
            "أولاً، على جوال الطفل اضغط 'السماح بإقران جوال أبوين جديد'.",
        "Start listening" to "ابدأ المراقبة",
        "Stop listening" to "أوقف المراقبة",
        "Remove pairing" to "إلغاء الاقتران",
        "Listen to the room (sound only)" to "استمع للغرفة (صوت فقط)",
        "Stop listening to the room" to "أوقف الاستماع للغرفة",
        "Open the live camera" to "افتح الكاميرا المباشرة",
        "Hold to talk to the child" to "اضغط مع الاستمرار للتحدث مع الطفل",
        "Recordings" to "التسجيلات",
        "(no recordings yet)" to "(لا توجد تسجيلات بعد)",
        "Start monitoring" to "ابدأ المراقبة",
        "Stop monitoring" to "أوقف المراقبة",
        "Allow a new parent phone to pair (2 minutes)" to "السماح بإقران جوال أبوين جديد (دقيقتان)",
        "Paired parent phones" to "جوالات الأبوين المقترنة",
        "(none yet)" to "(لا يوجد بعد)",
        "Keep running: battery settings" to "إعدادات البطارية للاستمرار",
        "Allow the camera (needed for the live camera)" to "السماح بالكاميرا (مطلوبة للكاميرا المباشرة)",
        "Dark screen (touch the screen to wake it)" to "شاشة مظلمة (المس الشاشة لإظهارها)",
        "Walkie-talkie volume is set from the parent phone." to "صوت الواكي توكي يُضبط من جوال الأبوين.",
        "Live camera" to "الكاميرا المباشرة",
        "Night mode" to "الوضع الليلي",
        "Flash" to "الفلاش",
        "Switch camera" to "تبديل الكاميرا",
        "Quality" to "الجودة",
        "Rotate the picture" to "تدوير الصورة",
        "Live sound" to "الصوت المباشر",
        "Stop and close" to "أوقف وأغلق",
        "Unlock" to "فتح القفل",
        "Use fingerprint" to "استخدم البصمة",
        "Liquid Baby is locked" to "ليكويد بيبي مقفل",
        "Enter your PIN to open the parent screen." to "أدخل الرمز السري لفتح شاشة الأبوين.",
        "Wrong PIN" to "الرمز السري غير صحيح",
        "App updates" to "تحديثات التطبيق",
        "Diagnostics" to "التشخيص",
        "Change phone role" to "تغيير دور الجوال",
        "Notification sound" to "نغمة الإشعارات",
        "Set PIN (lock the parent phone)" to "ضع رمزاً سرياً (لقفل جوال الأبوين)",
        "Change PIN" to "غيّر الرمز السري",
        "Remove PIN" to "احذف الرمز السري",
        "Backup / Export of your settings" to "نسخ احتياطي / تصدير الإعدادات",
        "Export settings (share)" to "تصدير الإعدادات (مشاركة)",
        "Copy settings to clipboard" to "انسخ الإعدادات",
        "Restore settings from clipboard" to "استرجع الإعدادات من الحافظة",
        "Write a test line to Diagnostics" to "اكتب سطر تجربة في التشخيص",
        "Saved" to "تم الحفظ",
        "Remove" to "إزالة",
        "Hold to talk" to "اضغط للتحدث",
        "Copied" to "تم النسخ",
        "Done. Open Diagnostics to see it." to "تم. افتح التشخيص لرؤيته.",
        "Listening: continuously" to "المراقبة: مستمرة",
        "Keep this phone on the charger, on the same Wi-Fi as the parent phones. If the phone restarts, open this screen and tap Start monitoring." to
            "أبقِ هذا الجوال على الشاحن، وعلى نفس الـ Wi-Fi مع جوالات الأبوين. إذا أعاد الجوال التشغيل، افتح هذه الشاشة واضغط ابدأ المراقبة.",
    )

    private val prefixes = listOf(
        "Status: " to "الحالة: ",
        "Wi-Fi IP: " to "عنوان Wi-Fi: ",
        "Paired with: " to "مقترن مع: ",
        "Clip length: " to "مدة التسجيل: ",
        "Sensitivity: " to "الحساسية: ",
        "Walkie-talkie volume on the child phone: " to "صوت الواكي توكي على جوال الطفل: ",
        "Child unit battery: " to "بطارية وحدة الطفل: ",
        "Child battery: " to "بطارية وحدة الطفل: ",
        "Temperature: " to "الحرارة: ",
        "Checking every " to "فحص كل ",
        "Sound level " to "مستوى الصوت ",
        "Language: " to "اللغة: ",
        "Live - watching now: " to "مباشر - يشاهد الآن: ",
        "Starting... - watching now: " to "جارٍ البدء... - يشاهد الآن: ",
        "Parent phones connected: " to "جوالات الأبوين المتصلة: ",
        "Live sound active" to "الصوت المباشر نشط",
        "Last clip: " to "آخر تسجيل: ",
        "Camera permission: " to "صلاحية الكاميرا: ",
    )

    private val words = listOf(
        " seconds" to " ثانية",
        " of 10" to " من 10",
        " of 5" to " من 5",
        "(5 = hears quieter sounds)" to "(5 = يسمع الأصوات الأهدأ)",
        "(set from the parent phone)" to "(يُضبط من جوال الأبوين)",
        "allowed" to "مسموحة",
        "(listens 5 s each time)" to "(يستمع 5 ثوان في كل مرة)",
        " (charging)" to " (على الشاحن)",
        "(tap to change)" to "(اضغط للتغيير)",
        "(tap to switch)" to "(اضغط للتبديل)",
        "(tap to turn on)" to "(اضغط للتشغيل)",
        "(tap to turn off)" to "(اضغط للإطفاء)",
        "(tap to change)" to "(اضغط للتغيير)",
        "off" to "مطفي",
        "ON" to "مشغّل",
        "Stopped" to "متوقف",
        "Listening" to "يستمع",
        "Connected to " to "متصل مع ",
    )

    fun t(s: String): String {
        if (!isAr()) return s
        exact[s]?.let { return it }
        for ((en, ar) in prefixes) {
            if (s.startsWith(en)) return ar + words.fold(s.substring(en.length)) { acc, w -> acc.replace(w.first, w.second) }
        }
        return words.fold(s) { acc, w -> acc.replace(w.first, w.second) }
    }
}
