package com.walktape.app.playback.browse

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.walktape.app.Graph
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileNotFoundException

/**
 * Read-only cover-art endpoint for external media browsers such as Android Auto (W10-T01).
 *
 * What it deliberately is NOT: a generic file server. It resolves exactly one kind of
 * request — a cover image of the **active** profile identified by art id — through
 * [com.walktape.app.core.library.CoverArtStore], so no audio, database, credentials or
 * arbitrary profile paths can ever be reached through it. Callers must be WalkTape
 * itself, the Android system, or the Android Auto projection package; anything else
 * gets nothing. In offline-only mode the store serves disk content only, so browsing
 * cover art never issues a network request behind the user's back.
 */
class CoverArtProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/*"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val file = resolve(uri) ?: throw FileNotFoundException("Cover art unavailable")
        val cursor = MatrixCursor(columns)
        cursor.addRow(columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        })
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Cover art is read-only")
        val file = resolve(uri) ?: throw FileNotFoundException("Cover art unavailable")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun resolve(uri: Uri): File? {
        if (!callerAllowed()) return null
        val request = CoverArtUris.parse(uri.toString()) ?: return null
        if (!Graph.ready()) return null
        return runBlocking {
            Graph.networkGate.awaitReady()
            // Profile isolation: only the active profile's covers are ever reachable here.
            val active = Graph.database.profileDao().active() ?: return@runBlocking null
            if (active.id != request.profileId) return@runBlocking null
            val file = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                Graph.coverArtStore(request.profileId).file(request.coverArtId, request.sizePx,
                    fetchIfMissing = true)
            }
            if (Graph.database.profileDao().active()?.id == request.profileId) file else null
        }?.takeIf { it.isFile }
    }

    private fun callerAllowed(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == android.os.Process.myUid() || uid == 0 || uid == android.os.Process.SYSTEM_UID) return true
        val packages = context?.packageManager?.getPackagesForUid(uid) ?: return false
        return packages.any { it in TRUSTED_BROWSER_PACKAGES }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?): Int = 0

    companion object {
        /** The Android Auto phone-projection app that browses and loads cover art. */
        val TRUSTED_BROWSER_PACKAGES = setOf("com.google.android.projection.gearhead")
        private const val FETCH_TIMEOUT_MS = 8_000L
    }
}
