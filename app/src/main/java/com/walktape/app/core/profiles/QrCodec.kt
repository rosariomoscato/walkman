package com.walktape.app.core.profiles

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

/** Configuration QR carries URL, profile name and username only — never the password. */
data class QrPayload(val name: String, val url: String, val username: String) {
    override fun toString() = "QrPayload([redacted])"
}

object QrCodec {
    private const val SUPPORTED_VERSION = 1
    private const val MAX_NAME = 80
    private const val MAX_FIELD = 512

    fun encode(payload: QrPayload): String = JsonObject().apply {
        addProperty("v", SUPPORTED_VERSION)
        addProperty("n", payload.name)
        addProperty("u", payload.url)
        addProperty("un", payload.username)
    }.toString()

    /** Accepts WalkTape JSON or a bare server URL; returns null on anything malformed. */
    fun decode(raw: String): QrPayload? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > 2048) return null
        if (!trimmed.startsWith("{")) {
            return valid(trimmed)?.let { QrPayload(name = "", url = it, username = "") }
        }
        return try {
            val reader = JsonReader(StringReader(trimmed)).apply { strictness = Strictness.STRICT }
            val document = JsonParser.parseReader(reader)
            if (reader.peek() != JsonToken.END_DOCUMENT) return null
            val root = document.asJsonObject
            val version = root.get("v")?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt ?: return null
            if (version != SUPPORTED_VERSION) return null
            QrPayload(
                name = root.get("n")?.asString?.takeIf { it.length <= MAX_NAME } ?: return null,
                url = valid(root.get("u")?.asString ?: return null) ?: return null,
                username = root.get("un")?.asString?.takeIf { it.isNotEmpty() && it.length <= MAX_FIELD } ?: return null,
            )
        } catch (_: RuntimeException) { null }
          catch (_: java.io.IOException) { null }
    }

    private fun valid(url: String) = url
        .takeIf { it.startsWith("https://") || it.startsWith("http://") }
        ?.takeIf { it.length <= MAX_FIELD }
}
