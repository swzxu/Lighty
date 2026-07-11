package com.hrdcoreee.lighty.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OTA updates via the GitHub Releases API. All network work runs on IO.
 *
 * [check] throws on a genuine network failure and returns null when there is no
 * newer release, so callers can distinguish "up to date" from "couldn't check".
 */
class UpdateManager(context: Context) {

    private val appContext = context.applicationContext

    private val currentVersion: String =
        runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull() ?: "0"

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = httpGet(RELEASES_URL) ?: return@withContext null
        val obj = JSONObject(json)
        val tag = obj.optString("tag_name").ifBlank { obj.optString("name") }
        if (tag.isBlank() || compareVersions(tag, currentVersion) <= 0) return@withContext null

        var apkUrl: String? = null
        obj.optJSONArray("assets")?.let { assets ->
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
        }

        UpdateInfo(
            versionName = tag.trim().trimStart('v', 'V'),
            releaseNotes = obj.optString("body").trim(),
            apkUrl = apkUrl,
            pageUrl = obj.optString("html_url"),
        )
    }

    suspend fun downloadApk(url: String, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = openConnection(url)
                conn.connect()
                if (conn.responseCode !in 200..299) return@withContext null

                val length = conn.contentLength.toLong()
                val file = File(appContext.cacheDir, "update.apk")
                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            total += read
                            if (length > 0) {
                                onProgress(((total * 100) / length).toInt().coerceIn(0, 100))
                            }
                        }
                    }
                }
                file
            }.getOrNull()
        }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            appContext, "${appContext.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun openPage(url: String) {
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun httpGet(url: String): String? {
        val conn = openConnection(url).apply {
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }

    companion object {
        private const val RELEASES_URL =
            "https://api.github.com/repos/swzxu/Lighty/releases/latest"
        private const val USER_AGENT = "Lighty-App"

        /** Returns >0 if [a] is newer than [b], numeric component-wise ("v1.2" vs "1.2.0"). */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.trim().trimStart('v', 'V')
                .split(Regex("[.\\-+_]"))
                .mapNotNull { it.toIntOrNull() }

            val pa = parts(a)
            val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val diff = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
                if (diff != 0) return diff
            }
            return 0
        }
    }
}
