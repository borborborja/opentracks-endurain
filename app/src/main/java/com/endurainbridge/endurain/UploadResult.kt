package com.endurainbridge.endurain

/** Outcome of a single upload attempt. Distinguishes retriable failures from permanent ones. */
sealed interface UploadResult {
    /** HTTP 2xx (Endurain returns 201). */
    data class Success(val httpCode: Int) : UploadResult

    /** Server/network problems worth retrying (5xx, timeouts, no connectivity). */
    data class ServerError(val httpCode: Int, val body: String) : UploadResult
    data class NetworkError(val detail: String) : UploadResult

    /** Permanent failures — retrying will not help. */
    data class AuthError(val httpCode: Int, val body: String) : UploadResult      // bad/missing API key, 401/403
    data class RejectedError(val httpCode: Int, val body: String) : UploadResult  // 4xx e.g. 406 bad format, 422
    data class ConfigError(val detail: String) : UploadResult                     // app not configured

    val isRetriable: Boolean
        get() = this is ServerError || this is NetworkError
}
