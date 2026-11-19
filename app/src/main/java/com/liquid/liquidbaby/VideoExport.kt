package com.liquid.liquidbaby

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import java.io.File

/**
 * Shares or saves an encrypted video as a normal MP4 (Movies/LiquidBaby). Used by the recordings list and the player,
 * so both behave the same. The unencrypted copy is made only when asked for, and removed afterwards.
 */
object VideoExport {

    /** Sharing is a choice the user makes now. Saving to the gallery makes an unlocked copy visible to other apps, so ask first. */
    fun run(activity: Activity, src: File, share: Boolean) {
        if (share) {
            exportNow(activity, src, true)
            return
        }
        Dialogs.show(
            activity,
            Tr.pick("Save an unlocked copy?", "حفظ نسخة غير مشفّرة؟"),
            Tr.pick(
                "The saved video is not encrypted, and other apps with access to media can see it. Continue?",
                "الفيديو المحفوظ غير مشفّر، ويمكن لتطبيقات الوسائط رؤيته. هل تتابع؟"
            ),
            listOf(
                Tr.pick("Save", "حفظ") to { exportNow(activity, src, false) },
                Tr.pick("Cancel", "تراجع") to {}
            )
        )
    }

    private fun exportNow(activity: Activity, src: File, share: Boolean) {
        fun t(en: String, ar: String): String = Tr.pick(en, ar)
        fun msg(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

        if (android.os.Build.VERSION.SDK_INT < 29) {
            msg(t("Saving to the gallery needs Android 10 or newer.", "الحفظ في المعرض يحتاج أندرويد 10 أو أحدث."))
            return
        }
        msg(t("Preparing the video...", "جارٍ تجهيز الفيديو..."))
        Thread {
            val name = "LiquidBaby-" + android.text.format.DateFormat.format("yyyyMMdd-HHmmss", java.util.Date(src.lastModified())) + ".mp4"
            val tmp = File(File(activity.cacheDir, "export").apply { mkdirs() }, name)
            var uri: Uri? = null
            if (VideoVault.decryptFile(src, tmp)) {
                try {
                    val resolver = activity.contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, name)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LiquidBaby")
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                    val target = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    if (target != null) {
                        resolver.openOutputStream(target)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
                        resolver.update(target, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                        uri = target
                    }
                } catch (e: Exception) {
                    Diag.i("Video export problem: " + e.message)
                }
            }
            tmp.delete()
            activity.runOnUiThread {
                val target = uri
                if (target == null) {
                    msg(t("The video could not be saved.", "تعذّر حفظ الفيديو."))
                    return@runOnUiThread
                }
                if (share) {
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, target)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.startActivity(Intent.createChooser(i, t("Share video", "مشاركة الفيديو")))
                } else {
                    msg(t("Saved to Movies/LiquidBaby.", "تم الحفظ في Movies/LiquidBaby."))
                }
            }
        }.start()
    }
}
