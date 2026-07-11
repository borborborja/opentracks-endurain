package com.endurainbridge.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Downloads the release APK and launches the system package installer. */
object ApkInstaller {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Whether the app is allowed to install APKs (Android 8+ requires the user to grant this). */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen to grant "install unknown apps" for this app. */
    fun requestInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Blocking download of [url] into the external cache. Returns the file or null on failure. */
    fun downloadApk(context: Context, url: String): File? {
        val dir = File(context.externalCacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "Endurain-Bridge.apk")
        val request = Request.Builder().url(url).header("User-Agent", "EndurainBridge").build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                out.outputStream().use { os -> body.byteStream().copyTo(os) }
            }
            if (out.length() > 0) out else null
        } catch (e: IOException) {
            null
        }
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
