package com.walktape.app.core.diagnostics

object Redactor {

    private val urlCredentials = Regex("""(?i)([a-z][a-z0-9+.-]*://)([^/@\s:]+):([^/@\s]+)@""")

    private val sensitiveQuery = Regex("""(?i)(password|passwd|token|secret|api_?key|authorization|session)=([^&\s]+)""")

    private val subsonicToken = Regex("""(?i)\bt=[0-9a-f]{32}\b""")

    fun redact(input: String): String = input
        .replace(urlCredentials) { m -> "${m.groupValues[1]}${m.groupValues[2]}:***@" }
        .replace(sensitiveQuery) { m -> "${m.groupValues[1]}=***" }
        .replace(subsonicToken, "t=***")
}
