package com.walktape.app.core.updates

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * W11-T04: coordinates manual update checks, APK downloads, verification and the
 * install hand-off. Every network operation goes through the injected [networkAllowed]
 * gate (offline-only global + awaitReady): a returning network never re-enables a
 * disabled opt-in nor starts anything automatically. Installation is always confirmed
 * by Android's package installer; WalkTape never installs silently.
 */
class UpdateManager(
    private val context: Context,
    http: OkHttpClient = GitHubReleases.defaultClient(),
    private val owner: String = "rosariomoscato",
    private val repo: String = "walktape",
    /** Injectable so tests can point at a local server; the default is the real API. */
    apiBaseUrl: String = "https://api.github.com",
    /** Gate: awaitReady + offline-only check; injected so tests can stub it. */
    private val networkAllowed: suspend () -> Boolean = { true },
) {
    private val releases = GitHubReleases(http, apiBaseUrl)
    private val verifier = ApkVerifier(context)
    private val http = http

    @Volatile private var activeCall: okhttp3.Call? = null

    val currentVersionName: String by lazy {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    sealed interface CheckOutcome {
        data class Available(val release: ReleaseInfo) : CheckOutcome
        data object UpToDate : CheckOutcome
        data object NoReleases : CheckOutcome
        data object RateLimited : CheckOutcome
        data object Network : CheckOutcome
        data object Invalid : CheckOutcome
        data object BlockedOffline : CheckOutcome
        /** The tag or the installed version cannot be compared: no automatic decision. */
        data class UnknownVersion(val release: ReleaseInfo) : CheckOutcome
    }

    /** Short stable token recorded in preferences for the last automatic check. */
    fun outcomeToken(outcome: CheckOutcome): String = when (outcome) {
        is CheckOutcome.Available -> "available"
        is CheckOutcome.UnknownVersion -> "unknown"
        CheckOutcome.UpToDate -> "uptodate"
        CheckOutcome.NoReleases -> "noreleases"
        CheckOutcome.RateLimited -> "ratelimit"
        CheckOutcome.Network -> "network"
        CheckOutcome.Invalid -> "invalid"
        CheckOutcome.BlockedOffline -> "offline"
    }

    suspend fun check(): CheckOutcome {
        if (!networkAllowed()) return CheckOutcome.BlockedOffline
        return when (val result = releases.latest(owner, repo)) {
            is GitHubReleases.Result.Latest -> decide(result.release, currentVersionName)
            GitHubReleases.Result.NoReleases -> CheckOutcome.NoReleases
            is GitHubReleases.Result.RateLimited -> CheckOutcome.RateLimited
            GitHubReleases.Result.Network -> CheckOutcome.Network
            GitHubReleases.Result.Invalid -> CheckOutcome.Invalid
        }
    }

    sealed interface DownloadOutcome {
        data class Done(val file: File) : DownloadOutcome
        data object Cancelled : DownloadOutcome
        data object Failed : DownloadOutcome
        data object BlockedOffline : DownloadOutcome
    }

    fun updatesDirectory(): File = File(context.cacheDir, "updates").apply { mkdirs() }

    /**
     * Streams the asset into the private cache with progress callbacks. Cancellation
     * (coroutine or [cancelDownload]) always removes the partial file: a truncated
     * APK is never handed to the verifier or the installer.
     */
    suspend fun download(asset: ReleaseAsset, tag: String,
                         onProgress: (bytes: Long, total: Long) -> Unit): DownloadOutcome =
        withContext(Dispatchers.IO) {
            if (!networkAllowed()) return@withContext DownloadOutcome.BlockedOffline
            val target = File(updatesDirectory(), "walktape-${tag.replace(Regex("""[^\w.-]"""), "_")}.apk")
            val request = Request.Builder()
                .url(asset.url)
                .header("User-Agent", GitHubReleases.USER_AGENT)
                .build()
            val call = http.newCall(request)
            activeCall = call
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) return@withContext DownloadOutcome.Failed
                    val body = response.body
                    val total = body.contentLength()
                    val temp = File(target.absolutePath + ".part")
                    try {
                        temp.outputStream().use { out ->
                            val source = body.source()
                            val buffer = okio.Buffer()
                            var downloaded = 0L
                            while (true) {
                                ensureActive()
                                val read = source.read(buffer, 64 * 1024)
                                if (read == -1L) break
                                out.write(buffer.readByteArray())
                                downloaded += read
                                onProgress(downloaded, total)
                            }
                            out.flush()
                        }
                        if (total > 0) {
                            val actual = temp.length()
                            if (actual != total) {
                                temp.delete()
                                return@withContext DownloadOutcome.Failed
                            }
                        }
                        if (target.exists()) target.delete()
                        if (!temp.renameTo(target)) {
                            temp.delete()
                            return@withContext DownloadOutcome.Failed
                        }
                        DownloadOutcome.Done(target)
                    } finally {
                        temp.delete()
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                target.delete()
                DownloadOutcome.Cancelled
            } catch (_: IOException) {
                target.delete()
                if (call.isCanceled()) DownloadOutcome.Cancelled else DownloadOutcome.Failed
            } catch (_: Exception) {
                target.delete()
                DownloadOutcome.Failed
            } finally {
                activeCall = null
            }
        }

    fun cancelDownload() { activeCall?.cancel() }

    fun verify(file: File): ApkVerifier.Outcome = verifier.verify(file)

    fun canRequestInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** View intent over a FileProvider URI: Android's installer asks for confirmation. */
    fun installIntent(file: File): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                FileProvider.getUriForFile(context, "com.walktape.app.files", file),
                "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** System settings page to grant unknown-apps installs for WalkTape only. */
    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

    companion object {
        /** Pure version decision, unit-testable without Android. */
        fun decide(release: ReleaseInfo, currentVersionName: String): CheckOutcome {
            val remote = ParsedVersion.parse(release.tagName)
                ?: return CheckOutcome.UnknownVersion(release)
            val local = ParsedVersion.parse(currentVersionName)
                ?: return CheckOutcome.UnknownVersion(release)
            return if (remote > local) CheckOutcome.Available(release) else CheckOutcome.UpToDate
        }
    }
}
