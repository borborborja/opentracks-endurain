package com.endurainbridge.dedup

import android.content.Context

/**
 * Client-side dedup: Endurain does NOT reject a re-uploaded file — it silently creates a *hidden
 * duplicate* activity (crud.py create_activity, duplicate-start-time branch). So we track which
 * OpenTracks track UUIDs we have already uploaded and skip them.
 */
class UploadLedger(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isUploaded(trackUuid: String): Boolean =
        prefs.getStringSet(KEY_UPLOADED, emptySet())!!.contains(trackUuid)

    fun markUploaded(trackUuid: String) {
        val current = HashSet(prefs.getStringSet(KEY_UPLOADED, emptySet())!!)
        current.add(trackUuid)
        prefs.edit().putStringSet(KEY_UPLOADED, current).apply()
    }

    companion object {
        private const val FILE = "upload_ledger"
        private const val KEY_UPLOADED = "uploaded_track_uuids"
    }
}
