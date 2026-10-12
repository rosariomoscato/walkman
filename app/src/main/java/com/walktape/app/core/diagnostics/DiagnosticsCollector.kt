package com.walktape.app.core.diagnostics

import android.content.Context
import android.os.Build
import android.os.StatFs
import com.walktape.app.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class DiagnosticsCollector(private val context: Context) {

    suspend fun buildReport(): String = withContext(Dispatchers.IO) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val profileCount = Graph.database.profileDao().count()
        val filesStat = StatFs(context.filesDir.absolutePath)
        val cacheBytes = dirSize(context.cacheDir)
        val metrics = context.resources.displayMetrics
        val locale = context.resources.configuration.locales[0]

        val lines = listOf(
            "app.version" to (packageInfo.versionName ?: "unknown"),
            "app.package" to context.packageName,
            "device.manufacturer" to Build.MANUFACTURER,
            "device.model" to Build.MODEL,
            "device.android" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "display" to "${metrics.widthPixels}x${metrics.heightPixels} @${metrics.density}x",
            "locale" to locale.toLanguageTag(),
            "storage.files.availableMb" to (filesStat.availableBytes / (1024 * 1024)).toString(),
            "storage.files.totalMb" to (filesStat.totalBytes / (1024 * 1024)).toString(),
            "storage.cacheMb" to (cacheBytes / (1024 * 1024)).toString(),
            "profiles.configured" to profileCount.toString()
        )

        Redactor.redact(lines.joinToString("\n") { "${it.first}: ${it.second}" })
    }

    private fun dirSize(dir: java.io.File): Long =
        dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    companion object {
        fun formatBytes(bytes: Long): String = String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0)
    }
}
