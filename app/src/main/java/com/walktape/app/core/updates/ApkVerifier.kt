package com.walktape.app.core.updates

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/**
 * W11-T04: verifies a downloaded APK before Android is ever asked to install it.
 * Fail-closed: package must be WalkTape, versionCode must strictly increase, the
 * signing certificate must match the installed app, and a readable but inconsistent
 * file is rejected. Disinstallare per aggirare una firma diversa non è un'opzione.
 */
class ApkVerifier(private val context: Context) {

    sealed interface Outcome {
        /** Same package, newer versionCode, same signer: safe to hand to the installer. */
        data object Ok : Outcome
        data object NotWalkTape : Outcome
        data object NotNewer : Outcome
        data object SignatureMismatch : Outcome
        data object Corrupt : Outcome
    }

    fun verify(file: File): Outcome {
        if (!file.isFile || file.length() <= 0) return Outcome.Corrupt
        val pm = context.packageManager
        val info = try {
            getArchiveInfo(pm, file)
        } catch (_: Exception) {
            return Outcome.Corrupt
        } ?: return Outcome.Corrupt

        if (info.packageName != context.packageName) return Outcome.NotWalkTape
        val incomingCode = PackageInfoCompat.getLongVersionCode(info)
        val installed = try {
            pm.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            return Outcome.NotWalkTape
        }
        if (incomingCode <= PackageInfoCompat.getLongVersionCode(installed)) return Outcome.NotNewer

        val incomingSigs = signaturesOf(info)
        val installedSigs = try {
            signaturesOf(pm.getPackageInfo(context.packageName, signingFlags()))
        } catch (_: Exception) {
            emptySet()
        }
        if (incomingSigs.isEmpty() || installedSigs.isEmpty()) return Outcome.SignatureMismatch
        if (incomingSigs != installedSigs) return Outcome.SignatureMismatch
        return Outcome.Ok
    }

    private fun getArchiveInfo(pm: PackageManager, file: File): PackageInfo? = try {
        pm.getPackageArchiveInfo(file.absolutePath, signingFlags())
    } catch (_: Exception) {
        null
    }

    private fun signingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        }

    /** Stable digests of all signing certificates; empty when unavailable (fail-closed). */
    private fun signaturesOf(info: PackageInfo): Set<String> = try {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        }.orEmpty()
        signatures.map { raw ->
            MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    } catch (_: Exception) {
        emptySet()
    }
}
