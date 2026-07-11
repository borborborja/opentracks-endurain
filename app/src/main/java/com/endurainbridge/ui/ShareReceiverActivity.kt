package com.endurainbridge.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.endurainbridge.R
import com.endurainbridge.data.Settings
import com.endurainbridge.dedup.UploadLedger
import com.endurainbridge.upload.UploadEnqueuer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Vía A — receives a GPX file shared from OpenTracks (or any app) via ACTION_SEND / SEND_MULTIPLE
 * and uploads it to Endurain. OpenTracks generates the GPX itself and hands us a ready content URI,
 * so we don't touch its content-provider columns at all (robust), and "Show on map" stays free for
 * the real map app. Dedup is by file content hash to avoid accidental double-shares.
 */
class ShareReceiverActivity : ComponentActivity() {

    private enum class Outcome { ENQUEUED, DUPLICATE, FAILED }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Settings.get(this).isConfigured) {
            toast(getString(R.string.toast_configure_first)); finish(); return
        }
        val uris = extractStreamUris(intent)
        if (uris.isEmpty()) {
            toast(getString(R.string.toast_no_data)); finish(); return
        }

        lifecycleScope.launch {
            val outcomes = withContext(Dispatchers.IO) { uris.map { processOne(it) } }
            toast(summarize(outcomes))
            finish()
        }
    }

    private fun processOne(uri: Uri): Outcome {
        val copied = copyToCache(uri) ?: return Outcome.FAILED
        val (file, sha256, displayName) = copied
        val key = "sha256:$sha256"
        if (UploadLedger(this).isUploaded(key)) {
            file.delete()
            return Outcome.DUPLICATE
        }
        UploadEnqueuer.enqueue(this, file, displayName, key)
        return Outcome.ENQUEUED
    }

    private data class Copied(val file: File, val sha256: String, val displayName: String)

    private fun copyToCache(uri: Uri): Copied? {
        val name = queryDisplayName(uri) ?: "OpenTracks-${System.currentTimeMillis()}.gpx"
        val out = File(cacheDir, "share-${System.currentTimeMillis()}-${name.hashCode()}.gpx")
        val digest = MessageDigest.getInstance("SHA-256")
        val input = contentResolver.openInputStream(uri) ?: return null
        input.use { inp ->
            out.outputStream().use { os ->
                val buf = ByteArray(8192)
                var n = inp.read(buf)
                while (n >= 0) {
                    digest.update(buf, 0, n)
                    os.write(buf, 0, n)
                    n = inp.read(buf)
                }
            }
        }
        if (out.length() == 0L) {
            out.delete()
            return null
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return Copied(out, hex, name)
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && !c.isNull(i)) return c.getString(i)
            }
        }
        return null
    }

    private fun extractStreamUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND ->
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { listOf(it) }
                ?: emptyList()
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                ?: emptyList()
        else -> emptyList()
    }

    private fun summarize(outcomes: List<Outcome>): String {
        val enqueued = outcomes.count { it == Outcome.ENQUEUED }
        val duplicate = outcomes.count { it == Outcome.DUPLICATE }
        val failed = outcomes.count { it == Outcome.FAILED }
        return when {
            enqueued > 0 && duplicate == 0 && failed == 0 ->
                resources.getQuantityString(R.plurals.share_enqueued, enqueued, enqueued)
            enqueued == 0 && duplicate > 0 && failed == 0 ->
                getString(R.string.notif_skipped_duplicate)
            enqueued == 0 && failed > 0 ->
                getString(R.string.toast_read_failed)
            else ->
                getString(R.string.toast_share_mixed, enqueued, duplicate, failed)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
