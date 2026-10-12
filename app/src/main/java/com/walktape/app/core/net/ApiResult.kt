package com.walktape.app.core.net

sealed interface ApiResult<out T> {
    class Success<T>(val value: T) : ApiResult<T> {
        override fun toString() = "ApiResult.Success([redacted])"
    }
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

/** Only controlled categories/codes escape the network layer, never server text or causes. */
sealed interface ApiError {
    data object InvalidRequest : ApiError
    data object CredentialsUnavailable : ApiError
    data object LocalStorage : ApiError
    data object Unreachable : ApiError
    data object Timeout : ApiError
    data object Tls : ApiError
    data object Network : ApiError
    data object InvalidResponse : ApiError
    data class Http(val status: Int) : ApiError
    data class Protocol(val code: Int, val kind: ProtocolErrorKind) : ApiError
}

enum class ProtocolErrorKind {
    GENERIC, MISSING_PARAMETER, CLIENT_VERSION, SERVER_VERSION, AUTHENTICATION,
    AUTH_UNSUPPORTED, AUTH_CONFLICT, FORBIDDEN, LICENSE, NOT_FOUND;

    companion object {
        fun fromCode(code: Int) = when (code) {
            10 -> MISSING_PARAMETER
            20 -> CLIENT_VERSION
            30 -> SERVER_VERSION
            40, 44 -> AUTHENTICATION
            41, 42 -> AUTH_UNSUPPORTED
            43 -> AUTH_CONFLICT
            50 -> FORBIDDEN
            60 -> LICENSE
            70 -> NOT_FOUND
            else -> GENERIC
        }
    }
}

data class ServerInfo(
    val apiVersion: String,
    val type: String?,
    val serverVersion: String?,
    val openSubsonic: Boolean,
)

data class ServerExtension(val name: String, val versions: List<Int>)
