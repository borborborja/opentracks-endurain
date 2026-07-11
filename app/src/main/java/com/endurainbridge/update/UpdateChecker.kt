package com.endurainbridge.update

import com.endurainbridge.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Checks the GitHub "latest release" for a newer build. Release tags are `v0.1.<runNumber>` and the
 * app's versionCode is that same run number, so we compare the tag's trailing integer to
 * [BuildConfig.VERSION_CODE]. No auth needed (public repo; 60 req/h unauthenticated is plenty).
 */
object UpdateChecker {

    sealed interface Result {
        data class Available(
            val versionName: String,
            val versionCode: Int,
            val apkUrl: String,
            val pageUrl: String,
        ) : Result
        data object UpToDate : Result
        data class Error(val message: String) : Result
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun check(): Result {
        val url = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "EndurainBridge")
            .build()

        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return Result.Error("HTTP ${resp.code}")
                val json = JSONObject(resp.body?.string() ?: return Result.Error("Respuesta vacía"))
                val tag = json.optString("tag_name")
                val pageUrl = json.optString("html_url")
                val latestCode = tag.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull()
                    ?: return Result.Error("Versión no reconocida: $tag")

                val apkUrl = findApkUrl(json)
                when {
                    latestCode > BuildConfig.VERSION_CODE && apkUrl != null ->
                        Result.Available(tag, latestCode, apkUrl, pageUrl)
                    else -> Result.UpToDate
                }
            }
        } catch (e: IOException) {
            Result.Error(e.message ?: "Error de red")
        }
    }

    private fun findApkUrl(release: JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return a.optString("browser_download_url").ifBlank { null }
            }
        }
        return null
    }
}
