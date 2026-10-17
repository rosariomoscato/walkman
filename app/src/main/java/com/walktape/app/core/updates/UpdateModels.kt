package com.walktape.app.core.updates

/** One downloadable release asset (GitHub), as needed for the update flow. */
data class ReleaseAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val contentType: String,
)

/** Latest published release: tag, title, notes and assets; public data only. */
data class ReleaseInfo(
    val tagName: String,
    val name: String?,
    val body: String?,
    val publishedAt: String?,
    val assets: List<ReleaseAsset>,
)

/** Comparable parsing of "v1.2.3"/"1.2.3"/"1.2" style versions; null when unrecognizable. */
data class ParsedVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ParsedVersion> {
    override fun compareTo(other: ParsedVersion): Int = compareValuesBy(
        this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        private val pattern = Regex("""^v?(\d{1,9})\.(\d{1,9})(?:\.(\d{1,9}))?$""")
        fun parse(value: String): ParsedVersion? = pattern.matchEntire(value.trim())?.let { m ->
            ParsedVersion(
                m.groupValues[1].toInt(),
                m.groupValues[2].toInt(),
                m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
            )
        }
    }
}

/**
 * Picks the APK asset for this device: exactly one unambiguous candidate, preferring
 * universal builds over ABI-specific ones. Anything ambiguous returns null (honest
 * refusal instead of a wrong download).
 */
object AssetSelector {

    private val abiMarkers = listOf("arm64", "arm32", "armeabi", "x86", "x64", "abi")
    private val universalMarkers = listOf("universal", "all")

    fun selectApk(assets: List<ReleaseAsset>): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null
        val lower = apks.map { it to it.name.lowercase() }
        val universal = lower.filter { (a, n) -> universalMarkers.any { n.contains(it) } && abiMarkers.none { n.contains(it) } }
        if (universal.size == 1) return universal.first().first
        val plain = lower.filter { (a, n) -> universalMarkers.none { n.contains(it) } && abiMarkers.none { n.contains(it) } }
        if (plain.size == 1) return plain.first().first
        // Multiple equally-valid candidates: refuse rather than guess.
        return null
    }
}
