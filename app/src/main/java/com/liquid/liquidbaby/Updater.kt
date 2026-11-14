package com.liquid.liquidbaby

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Finds, downloads and installs new builds from GitHub Releases. */
object Updater {

    data class Info(
        val tag: String,
        val name: String,
        val code: Int,
        val notes: String,
        val required: Boolean,
        val apkUrl: String,
        val apkSize: Long,
        val shaUrl: String
    ) {
        fun toJson(): String = JSONObject()
            .put("tag", tag).put("name", name).put("code", code).put("notes", notes)
            .put("required", required).put("apkUrl", apkUrl).put("apkSize", apkSize)
            .put("shaUrl", shaUrl).toString()

        companion object {
            fun fromJson(s: String): Info? = try {
                val o = JSONObject(s)
                Info(
                    o.getString("tag"), o.getString("name"), o.getInt("code"),
                    o.optString("notes"), o.optBoolean("required"), o.getString("apkUrl"),
                    o.optLong("apkSize"), o.optString("shaUrl")
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    private const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    private var appCtx: Context? = null

    @Volatile
    private var useFallbackTrust = false

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
    }

    /** Runs the block; if the phone does not trust GitHub's certificate, retries once with the built-in list. */
    private fun <T> withTrustRetry(block: () -> T): T {
        try {
            return block()
        } catch (e: javax.net.ssl.SSLException) {
            if (useFallbackTrust || appCtx == null) throw e
            Diag.i("This phone does not trust the certificate (" + e.message + "). Retrying with built-in certificates.")
            useFallbackTrust = true
            return block()
        }
    }

    private fun open(url: String, json: Boolean): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        val ctx = appCtx
        if (useFallbackTrust && ctx != null && c is javax.net.ssl.HttpsURLConnection) {
            c.sslSocketFactory = TrustFallback.socketFactory(ctx)
        }
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "LiquidBaby-Updater")
        if (json) c.setRequestProperty("Accept", "application/vnd.github+json")
        return c
    }

    /** Blocking. Returns null if the repository has no release yet. */
    fun fetchLatest(): Info? = withTrustRetry { fetchLatestOnce() }

    private fun fetchLatestOnce(): Info? {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) throw IllegalStateException("Update repository is not configured in this build.")
        val c = open("https://api.github.com/repos/$repo/releases/latest", true)
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode == 403 || c.responseCode == 429) return fetchViaRedirect(repo)
            if (c.responseCode != 200) throw IllegalStateException("GitHub answered HTTP " + c.responseCode)
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val o = JSONObject(text)
            val tag = o.getString("tag_name")
            val parsed = VersionUtil.parseTag(tag)
                ?: throw IllegalStateException("Release tag '$tag' is not in the form v<version>-build<number>.")
            val notes = latestReleaseNotes(o.optString("body", ""))
            val assets: JSONArray = o.optJSONArray("assets") ?: JSONArray()
            var apkUrl = ""
            var apkSize = 0L
            var shaUrl = ""
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val n = a.getString("name")
                if (n.endsWith(".apk")) {
                    apkUrl = a.getString("browser_download_url")
                    apkSize = a.optLong("size")
                } else if (n.endsWith(".apk.sha256")) {
                    shaUrl = a.getString("browser_download_url")
                }
            }
            if (apkUrl.isEmpty()) throw IllegalStateException("Release $tag has no APK file.")
            return Info(tag, parsed.name, parsed.code, notes, VersionUtil.isRequired(notes), apkUrl, apkSize, shaUrl)
        } finally {
            c.disconnect()
        }
    }

    private fun fetchText(url: String): String? {
        return try {
            val c = open(url, false)
            try {
                if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                c.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Used when the GitHub API refuses (limit reached): read the latest tag from the normal web redirect. */
    private fun fetchViaRedirect(repo: String): Info? {
        val c = open("https://github.com/$repo/releases/latest", false)
        c.instanceFollowRedirects = false
        try {
            val code = c.responseCode
            if (code == 404) return null
            if (code !in 300..399) throw IllegalStateException("GitHub answered HTTP $code")
            val loc = c.getHeaderField("Location") ?: throw IllegalStateException("GitHub gave no release address")
            val tag = java.net.URLDecoder.decode(loc.substringAfterLast("/releases/tag/", ""), "UTF-8")
            if (tag.isEmpty()) return null
            val parsed = VersionUtil.parseTag(tag)
                ?: throw IllegalStateException("Release tag '$tag' is not in the form v<version>-build<number>.")
            val apk = VersionUtil.apkFileName("LiquidBaby", parsed.name, parsed.code)
            val base = "https://github.com/$repo/releases/download/$tag/$apk"
            val notes = latestReleaseNotes(fetchText("https://raw.githubusercontent.com/$repo/main/RELEASE_NOTES.md") ?: "")
            Diag.i("Update info read through the web redirect (API limit)")
            return Info(tag, parsed.name, parsed.code, notes, VersionUtil.isRequired(notes), base, 0L, "$base.sha256")
        } finally {
            c.disconnect()
        }
    }

    fun cached(): Info? {
        val s = Prefs.getString("availableJson")
        return if (s.isEmpty()) null else Info.fromJson(s)
    }

    private fun updatesDir(ctx: Context): File {
        val d = File(ctx.cacheDir, "updates")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun apkFile(ctx: Context, info: Info): File = File(updatesDir(ctx), "update-" + info.code + ".apk")

    fun isWifi(ctx: Context): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(net)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (e: Exception) {
            false
        }
    }

    private val downloadLock = Any()

    /** Blocking. Downloads, verifies the checksum, and returns the APK file. */
    fun download(ctx: Context, info: Info, onProgress: (Int) -> Unit): File =
        synchronized(downloadLock) { withTrustRetry { downloadLocked(ctx, info, onProgress) } }

    private fun downloadLocked(ctx: Context, info: Info, onProgress: (Int) -> Unit): File {
        val dir = updatesDir(ctx)
        dir.listFiles()?.forEach { if (it.name != apkFile(ctx, info).name) it.delete() }
        val target = apkFile(ctx, info)
        if (target.exists()) return target
        val part = File(dir, target.name + ".part")

        var expected: String? = null
        if (info.shaUrl.isNotEmpty()) {
            val sc = open(info.shaUrl, false)
            try {
                if (sc.responseCode == 200) {
                    expected = VersionUtil.parseSha256(sc.inputStream.bufferedReader().use { it.readText() })
                }
            } finally {
                sc.disconnect()
            }
        }

        val c = open(info.apkUrl, false)
        try {
            if (c.responseCode != 200) throw IllegalStateException("Download failed: HTTP " + c.responseCode)
            val total = if (c.contentLength > 0) c.contentLength.toLong() else info.apkSize
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastPct = -1
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt()
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            val actual = md.digest().joinToString("") { String.format("%02x", it) }
            if (expected != null && expected != actual) {
                part.delete()
                throw IllegalStateException("Checksum mismatch. The download was damaged. Try again.")
            }
            if (!part.renameTo(target)) {
                try {
                    part.copyTo(target, overwrite = true)
                    part.delete()
                } catch (e: Exception) {
                    throw IllegalStateException("Could not save the downloaded file.", e)
                }
            }
            Diag.i("Downloaded update " + info.tag + " (" + VersionUtil.formatSize(target.length()) + ")")
            return target
        } finally {
            c.disconnect()
        }
    }

    /** Hands the APK to the system installer (one-tap confirmation). */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val id = installer.createSession(params)
        val session = installer.openSession(id)
        try {
            apk.inputStream().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) flags = flags or PendingIntent.FLAG_MUTABLE
            val pending = PendingIntent.getBroadcast(ctx, id, intent, flags)
            session.commit(pending.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }

    /** Blocking. Used by the app start and by the background job. */
    fun backgroundCheck(ctx: Context, force: Boolean): Info? {
        val now = System.currentTimeMillis()
        if (!force && now - Prefs.getLong("lastCheckMs") < CHECK_EVERY_MS) return cached()
        val info = fetchLatest()
        Prefs.putLong("lastCheckMs", now)
        if (info == null) {
            Prefs.remove("availableJson")
            return null
        }
        Prefs.putString("availableJson", info.toJson())
        val local = AppInfo.versionCode(ctx)
        if (info.code > local) {
            if (Prefs.getBool("wifiPredownload", true) && isWifi(ctx) && !apkFile(ctx, info).exists()) {
                try {
                    download(ctx, info) { }
                } catch (e: Exception) {
                    Diag.e("Background pre-download failed", e)
                }
            }
            val skipped = Prefs.getInt("skippedCode")
            if (VersionUtil.shouldOffer(info.code, local, skipped, info.required) &&
                Prefs.getInt("notifiedCode") != info.code
            ) {
                Prefs.putInt("notifiedCode", info.code)
                Notifier.updateReady(ctx, info)
            }
        }
        return info
    }
}
