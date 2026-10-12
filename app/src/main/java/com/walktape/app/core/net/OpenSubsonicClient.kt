package com.walktape.app.core.net

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.StringReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

/** One instance per profile; no global mutable credentials, cookies, cache or HTTP logger. */
class OpenSubsonicClient(
    private val address: ServerAddress,
    private val credentials: Credentials,
    private val http: OkHttpClient = defaultHttpClient(),
    private val salt: () -> String = ::randomSalt,
) {
    suspend fun ping(): ApiResult<ServerInfo> = when (val result = request("ping")) {
        is ApiResult.Failure -> result
        is ApiResult.Success -> try {
            val root = result.value
            ApiResult.Success(ServerInfo(
                root.get("version").asString,
                root.get("type")?.asString,
                root.get("serverVersion")?.asString,
                root.get("openSubsonic")?.asBoolean ?: false,
            ))
        } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
    }

    suspend fun extensions(): ApiResult<List<ServerExtension>> =
        when (val result = request("getOpenSubsonicExtensions")) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> try {
                val extensions = result.value.getAsJsonArray("openSubsonicExtensions")
                    ?: return ApiResult.Failure(ApiError.InvalidResponse)
                ApiResult.Success(extensions.map {
                    val item = it.asJsonObject
                    ServerExtension(item.get("name").asString,
                        item.getAsJsonArray("versions").map { version -> version.asInt })
                })
            } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
        }

    /** Repeated parameters are preserved for playlist endpoints in later waves. */
    internal suspend fun request(
        endpoint: String,
        parameters: List<Pair<String, String>> = emptyList(),
    ): ApiResult<JsonObject> {
        if (!endpoint.matches(Regex("[a-zA-Z][a-zA-Z0-9]*")) ||
            parameters.any { it.first in RESERVED }) return ApiResult.Failure(ApiError.InvalidRequest)
        val requestSalt = salt()
        if (requestSalt.length < 6) return ApiResult.Failure(ApiError.InvalidRequest)
        val url = address.url.newBuilder().addPathSegment("rest").addPathSegment("$endpoint.view")
            .addQueryParameter("u", credentials.username)
            .addQueryParameter("t", credentials.token(requestSalt))
            .addQueryParameter("s", requestSalt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "WalkTape")
            .addQueryParameter("f", "json")
        parameters.forEach { (key, value) -> url.addQueryParameter(key, value) }
        val call = http.newCall(Request.Builder().url(url.build()).header("Accept", "application/json").build())
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(ApiResult.Failure(networkError(e)))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        if (!it.isSuccessful) ApiResult.Failure(ApiError.Http(it.code))
                        else try {
                            val body = it.body
                            if (body == null || body.contentLength() > MAX_BODY) {
                                ApiResult.Failure(ApiError.InvalidResponse)
                            } else {
                                val source = body.source()
                                source.request(MAX_BODY + 1)
                                if (source.buffer.size > MAX_BODY) ApiResult.Failure(ApiError.InvalidResponse)
                                else decode(source.readUtf8())
                            }
                        } catch (e: IOException) { ApiResult.Failure(networkError(e)) }
                    }
                    continuation.resume(result)
                }
            })
        }
    }

    companion object {
        private const val MAX_BODY = 8L * 1024 * 1024
        private val RESERVED = setOf("u", "p", "t", "s", "v", "c", "f", "apiKey", "callback")

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            // Redirects can transfer authentication to another host or downgrade TLS.
            .followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

        private fun networkError(e: IOException): ApiError = when (e) {
            is SSLException -> ApiError.Tls
            is SocketTimeoutException -> ApiError.Timeout
            is UnknownHostException, is ConnectException -> ApiError.Unreachable
            else -> if (e is java.io.InterruptedIOException) ApiError.Timeout else ApiError.Network
        }

        internal fun decode(body: String): ApiResult<JsonObject> = try {
            val reader = JsonReader(StringReader(body)).apply { strictness = Strictness.STRICT }
            val document = JsonParser.parseReader(reader)
            if (reader.peek() != JsonToken.END_DOCUMENT) throw IllegalArgumentException()
            val root = document.asJsonObject.getAsJsonObject("subsonic-response")
                ?: throw IllegalArgumentException()
            if (!root.get("version").asString.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
                throw IllegalArgumentException()
            }
            when (root.get("status").asString) {
                "ok" -> if (root.has("error")) ApiResult.Failure(ApiError.InvalidResponse)
                    else ApiResult.Success(root)
                "failed" -> {
                    val code = root.getAsJsonObject("error").get("code").asString.toInt()
                    ApiResult.Failure(ApiError.Protocol(code, ProtocolErrorKind.fromCode(code)))
                }
                else -> ApiResult.Failure(ApiError.InvalidResponse)
            }
        } catch (_: RuntimeException) { ApiResult.Failure(ApiError.InvalidResponse) }
          catch (_: IOException) { ApiResult.Failure(ApiError.InvalidResponse) }
    }
}
