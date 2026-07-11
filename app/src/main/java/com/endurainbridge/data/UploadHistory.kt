package com.endurainbridge.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class UploadStatus { PENDING, SUCCESS, FAILED }

/** One row in the upload history shown in the app. */
data class UploadHistoryEntry(
    val id: String,
    val name: String,
    val timeMs: Long,
    val status: UploadStatus,
    val endurainId: Long?,
    val detail: String?,
)

/**
 * Persistent, newest-first log of upload attempts (name, time, status, resulting Endurain activity
 * id). Stored as a JSON array in SharedPreferences — small and dependency-free. The worker updates
 * entries as uploads complete; the history screen reads them.
 */
class UploadHistory(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Adds a PENDING entry and returns its id, to be passed to the worker. */
    @Synchronized
    fun add(name: String, timeMs: Long): String {
        val id = UUID.randomUUID().toString()
        val list = load()
        list.add(0, UploadHistoryEntry(id, name, timeMs, UploadStatus.PENDING, null, null))
        save(list.take(MAX_ENTRIES))
        return id
    }

    @Synchronized
    fun markSuccess(id: String, endurainId: Long?) = update(id) {
        it.copy(status = UploadStatus.SUCCESS, endurainId = endurainId, detail = null)
    }

    @Synchronized
    fun markFailed(id: String, detail: String?) = update(id) {
        it.copy(status = UploadStatus.FAILED, detail = detail)
    }

    @Synchronized
    fun clear() = prefs.edit().remove(KEY).apply()

    fun all(): List<UploadHistoryEntry> = load()

    private fun update(id: String, transform: (UploadHistoryEntry) -> UploadHistoryEntry) {
        val list = load()
        val i = list.indexOfFirst { it.id == id }
        if (i >= 0) {
            list[i] = transform(list[i])
            save(list)
        }
    }

    private fun load(): MutableList<UploadHistoryEntry> {
        val raw = prefs.getString(KEY, null) ?: return mutableListOf()
        val out = mutableListOf<UploadHistoryEntry>()
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return out
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                UploadHistoryEntry(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    timeMs = o.optLong("timeMs"),
                    status = runCatching { UploadStatus.valueOf(o.optString("status")) }
                        .getOrDefault(UploadStatus.PENDING),
                    endurainId = if (o.has("endurainId") && !o.isNull("endurainId")) o.optLong("endurainId") else null,
                    detail = if (o.has("detail") && !o.isNull("detail")) o.optString("detail") else null,
                )
            )
        }
        return out
    }

    private fun save(list: List<UploadHistoryEntry>) {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("name", e.name)
                    put("timeMs", e.timeMs)
                    put("status", e.status.name)
                    put("endurainId", e.endurainId ?: JSONObject.NULL)
                    put("detail", e.detail ?: JSONObject.NULL)
                }
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val FILE = "upload_history"
        private const val KEY = "entries"
        private const val MAX_ENTRIES = 100
    }
}
