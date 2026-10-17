package com.walktape.app.core.diagnostics

/**
 * Defence-in-depth redaction applied to every diagnostics rendering (W11-T02). The report
 * is built from an allowlist, so these patterns are a second barrier: they must cover
 * credentials in URLs, short OpenSubsonic auth parameters (p/t/s/u), auth headers,
 * labeled secrets, long encoded blobs and full URL query strings.
 */
object Redactor {

    private val urlCredentials = Regex("""(?i)([a-z][a-z0-9+.-]*://)([^/@\s:]+):([^/@\s]+)@""")

    private val sensitiveQuery = Regex("""(?i)(password|passwd|token|secret|api_?key|authorization|session)=([^&\s]+)""")

    private val subsonicToken = Regex("""(?i)\bt=[0-9a-f]{32}\b""")

    /** Short OpenSubsonic auth parameters on their own: p=, t=, s=, u= (also after ?/&/;/space). */
    private val subsonicShortParams = Regex("""(?i)(^|[?&; ])(p|t|s|u)=([^&\s"']+)""")

    /** Authorization/Cookie style headers: the whole line value is replaced. */
    private val authHeader = Regex("""(?i)\b(authorization|cookie)\s*:[^\n]+""")

    /** password/token/secret/api-key/credential labels followed by any single value. */
    private val labeledSecret = Regex("""(?i)\b(password|passwd|token|secret|api_?key|credential\w*|encryptedpassword|credentialid)\b(\s*[:=]\s*)[^&\s"']+""")

    /** Long base64-ish blobs (encrypted payloads, encoded tokens); ≥48 chars without spaces. */
    private val encodedBlob = Regex("""\b[A-Za-z0-9+/]{48,}={0,2}\b""")

    /** Any URL query string is dropped entirely: full authenticated URLs never survive. */
    private val urlQuery = Regex("""(?i)(https?://[^\s?]+)\?[^\s]+""")

    fun redact(input: String): String = input
        .replace(labeledSecret) { m -> "${m.groupValues[1]}${m.groupValues[2]}***" }
        .replace(authHeader) { m -> "${m.groupValues[1]}: ***" }
        .replace(urlCredentials) { m -> "${m.groupValues[1]}${m.groupValues[2]}:***@" }
        .replace(sensitiveQuery) { m -> "${m.groupValues[1]}=***" }
        .replace(subsonicToken, "t=***")
        .replace(subsonicShortParams) { m -> "${m.groupValues[1]}${m.groupValues[2]}=***" }
        .replace(urlQuery) { m -> "${m.groupValues[1]}?***" }
        .replace(encodedBlob, "***")
}
