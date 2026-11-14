package com.liquid.liquidbaby

import android.app.Activity
import android.os.Bundle

/** Diagnostics: filter the log, and copy or share it with secrets removed. */
class DiagnosticsActivity : Activity() {

    private var filter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    private fun t(en: String, ar: String): String = Tr.pick(en, ar)

    private fun filtered(text: String): String = when (filter) {
        "errors" -> text.lines().filter { it.contains("ERROR") || it.contains("Exception") }.joinToString("\n")
        "connection" -> text.lines().filter {
            Regex("(?i)connect|parent|child|wi-fi|scan|address|broadcast|pair|listen|view|disconnect").containsMatchIn(it)
        }.joinToString("\n")
        else -> text
    }

    private fun report(): String = Diag.redact(Diag.deviceInfo(this) + "\n" + Diag.readTail(60_000))

    private fun build() {
        val log = filtered(Diag.readTail(30_000))
        screen {
            addTitle(t("Diagnostics", "التشخيص"))
            addBody(t("Last errors and crashes. Secrets are removed before you copy or share.",
                "آخر الأخطاء والأعطال. تُحذف الأسرار قبل النسخ أو المشاركة."))
            addButton((if (filter == "all") "✓ " else "") + t("All", "الكل")) { filter = "all"; build() }
            addButton((if (filter == "errors") "✓ " else "") + t("Errors only", "الأخطاء فقط")) { filter = "errors"; build() }
            addButton((if (filter == "connection") "✓ " else "") + t("Connection only", "الاتصال فقط")) { filter = "connection"; build() }
            addButton(t("Copy", "نسخ")) { copyToClipboard(this@DiagnosticsActivity, "Liquid Baby diagnostics", report()) }
            addButton(t("Share", "مشاركة")) { shareText(this@DiagnosticsActivity, "Liquid Baby diagnostics", report()) }
            addButton(t("Refresh", "تحديث")) { build() }
            addButton(t("Clear", "مسح")) {
                Diag.clear()
                build()
            }
            addMono(Diag.redact(Diag.deviceInfo(this@DiagnosticsActivity)) + "\n" +
                (if (log.isEmpty()) t("(no entries yet)", "(لا توجد سجلات بعد)") else log))
        }
    }
}
