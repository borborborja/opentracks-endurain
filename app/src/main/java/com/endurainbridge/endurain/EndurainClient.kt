package com.endurainbridge.endurain

import com.endurainbridge.data.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Minimal HTTP client for Endurain's activity-upload API.
 *
 * Confirmed against Endurain source (backend/app/activities/activity/router.py,
 * core/routes.py, auth/internal_dependencies.py):
 *  - POST {base}/api/v1/activities/create/upload  -> 201 on success
 *  - multipart/form-data, file field literally named "file", media type application/gpx+xml
 *  - header X-API-Key: <key>       (upload accepts an API key with scope activities:upload)
 *  - header X-Client-Type: mobile  (mandatory; other values are rejected with 403)
 *  - accepted extensions: .gpx, .fit, .tcx, .gz
 */
class EndurainClient(private val settings: Settings) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Uploads a GPX file. The filename should end in ".gpx". */
    fun uploadGpx(file: File, displayName: String): UploadResult {
        val base = settings.serverUrl
        val key = settings.apiKey
        if (base.isBlank() || key.isBlank()) {
            return UploadResult.ConfigError("Falta la URL del servidor o la API key")
        }

        val filename = if (displayName.endsWith(".gpx", ignoreCase = true)) displayName else "$displayName.gpx"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                filename,
                file.asRequestBody(GPX_MEDIA_TYPE),
            )
            .build()

        val request = Request.Builder()
            .url("$base$UPLOAD_PATH")
            .addHeader(HEADER_API_KEY, key)
            .addHeader(HEADER_CLIENT_TYPE, CLIENT_TYPE_MOBILE)
            .post(body)
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful ->
                        UploadResult.Success(response.code, parseActivityId(response.body?.string()))
                    response.code == 401 || response.code == 403 ->
                        UploadResult.AuthError(response.code, response.body?.string().orEmpty())
                    response.code in 400..499 ->
                        UploadResult.RejectedError(response.code, response.body?.string().orEmpty())
                    else ->
                        UploadResult.ServerError(response.code, response.body?.string().orEmpty())
                }
            }
        } catch (e: IOException) {
            UploadResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Lightweight reachability probe of the upload endpoint. The path is POST-only, so a GET is
     * expected to return 405 when the server and path are correct. Does not validate the API key
     * (that only happens on a real upload) but confirms the URL/version are right.
     */
    fun testConnection(): TestResult {
        val base = settings.serverUrl
        if (base.isBlank()) return TestResult.Failure("Introduce primero la URL del servidor")

        val request = Request.Builder()
            .url("$base$UPLOAD_PATH")
            .addHeader(HEADER_CLIENT_TYPE, CLIENT_TYPE_MOBILE)
            .get()
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    405 -> TestResult.Success("Servidor accesible y endpoint correcto (405 esperado en GET).")
                    404 -> TestResult.Failure("Endpoint no encontrado (404). ¿Versión de Endurain sin /api/v1?")
                    401, 403 -> TestResult.Success("Servidor accesible (respondió ${response.code}). La API key se valida al subir.")
                    else -> TestResult.Success("Servidor accesible (HTTP ${response.code}).")
                }
            }
        } catch (e: IOException) {
            TestResult.Failure("No se pudo conectar: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * The upload endpoint returns a JSON array of created Activity objects (a file may yield more
     * than one). Extract the first activity's `id` for deep-linking; returns null if not parseable.
     */
    private fun parseActivityId(body: String?): Long? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val trimmed = body.trimStart()
            val first = if (trimmed.startsWith("[")) {
                org.json.JSONArray(body).optJSONObject(0)
            } else {
                org.json.JSONObject(body)
            }
            first?.takeIf { it.has("id") && !it.isNull("id") }?.getLong("id")
        }.getOrNull()
    }

    companion object {
        const val UPLOAD_PATH = "/api/v1/activities/create/upload"
        const val HEADER_API_KEY = "X-API-Key"
        const val HEADER_CLIENT_TYPE = "X-Client-Type"
        const val CLIENT_TYPE_MOBILE = "mobile"
        val GPX_MEDIA_TYPE = "application/gpx+xml".toMediaType()
    }
}

sealed interface TestResult {
    data class Success(val message: String) : TestResult
    data class Failure(val message: String) : TestResult
}
