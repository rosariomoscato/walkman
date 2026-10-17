package com.walktape.app.core.profiles

import androidx.room.withTransaction
import com.walktape.app.core.db.ProfileEntity
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.Credentials
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ServerAddress
import com.walktape.app.core.net.ServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ProfileRepository(
    private val database: WalkTapeDatabase,
    private val vault: CredentialVault,
    private val filesRoot: File,
) {
    private val dao = database.profileDao()
    private val mutex = Mutex()
    val profiles = dao.observeAll()

    suspend fun addVerified(name: String, url: String, username: String, password: String,
                            allowLocalHttp: Boolean): ApiResult<Long> = withContext(Dispatchers.IO) {
        if (name.isBlank() || name.length > 80 || username.isBlank() || password.isEmpty()) {
            return@withContext ApiResult.Failure(ApiError.InvalidRequest)
        }
        val address = ServerAddress.parse(url, allowLocalHttp)
            ?: return@withContext ApiResult.Failure(ApiError.InvalidRequest)
        when (val result = OpenSubsonicClient(address, Credentials(username, password)).ping()) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> mutex.withLock {
                val keyId = UUID.randomUUID().toString()
                try {
                    val encrypted = vault.encrypt(keyId, password)
                    val id = database.withTransaction {
                        val id = dao.insert(ProfileEntity(name = name.trim(), serverUrl = address.url.toString(),
                            username = username, createdAt = System.currentTimeMillis(), credentialId = keyId,
                            encryptedPassword = encrypted, allowLocalHttp = allowLocalHttp))
                        if (dao.active() == null) dao.activate(id)
                        id
                    }
                    ApiResult.Success(id)
                } catch (_: android.database.sqlite.SQLiteConstraintException) {
                    vault.delete(keyId)
                    ApiResult.Failure(ApiError.InvalidRequest)
                } catch (_: java.security.GeneralSecurityException) {
                    vault.delete(keyId)
                    ApiResult.Failure(ApiError.CredentialsUnavailable)
                }
            }
        }
    }

    suspend fun clientFor(id: Long): OpenSubsonicClient? = withContext(Dispatchers.IO) {
        val profile = dao.get(id) ?: return@withContext null
        val address = ServerAddress.parse(profile.serverUrl, profile.allowLocalHttp) ?: return@withContext null
        try {
            OpenSubsonicClient(address, Credentials(profile.username,
                vault.decrypt(profile.credentialId, profile.encryptedPassword)),
                networkAllowed = { dao.get(id) != null &&
                    database.downloadDao().settings(id)?.offlineOnly != true &&
                    dao.active()?.id?.let { database.downloadDao().settings(it)?.offlineOnly } != true })
        } catch (_: java.security.GeneralSecurityException) { null }
          catch (_: IllegalArgumentException) { null }
    }

    suspend fun verify(id: Long): ApiResult<ServerInfo> =
        clientFor(id)?.ping() ?: ApiResult.Failure(ApiError.CredentialsUnavailable)

    /**
     * W11-T03: fresh sign-in for an imported (credential-less) profile. Verifies the
     * credentials against the server, then stores them under a new Keystore key; the
     * previous key, if any, is removed. Never touches queue, downloads or the server.
     */
    suspend fun login(id: Long, url: String, username: String, password: String,
                      allowLocalHttp: Boolean): ApiResult<Unit> = withContext(Dispatchers.IO) {
        if (username.isBlank() || password.isEmpty()) {
            return@withContext ApiResult.Failure(ApiError.InvalidRequest)
        }
        val address = ServerAddress.parse(url, allowLocalHttp)
            ?: return@withContext ApiResult.Failure(ApiError.InvalidRequest)
        when (val result = OpenSubsonicClient(address, Credentials(username, password)).ping()) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> mutex.withLock {
                val profile = dao.get(id) ?: return@withLock ApiResult.Failure(ApiError.InvalidRequest)
                val oldKeyId = profile.credentialId
                val keyId = UUID.randomUUID().toString()
                try {
                    val encrypted = vault.encrypt(keyId, password)
                    database.withTransaction {
                        dao.update(profile.copy(
                            serverUrl = address.url.toString(),
                            username = username,
                            credentialId = keyId,
                            encryptedPassword = encrypted,
                            allowLocalHttp = allowLocalHttp,
                        ))
                    }
                    if (oldKeyId.isNotEmpty()) vault.delete(oldKeyId)
                    ApiResult.Success(Unit)
                } catch (_: android.database.sqlite.SQLiteConstraintException) {
                    vault.delete(keyId)
                    ApiResult.Failure(ApiError.InvalidRequest)
                } catch (_: java.security.GeneralSecurityException) {
                    vault.delete(keyId)
                    ApiResult.Failure(ApiError.CredentialsUnavailable)
                }
            }
        }
    }

    suspend fun activate(id: Long): Boolean = mutex.withLock {
        database.withTransaction {
            val profile = dao.get(id) ?: return@withTransaction false
            if (profile.credentialId.isEmpty()) return@withTransaction false
            dao.clearActive()
            dao.activate(id)
            true
        }
    }

    fun directory(id: Long): File {
        require(id > 0)
        return File(filesRoot, "profiles/$id")
    }

    suspend fun localBytes(id: Long): Long = withContext(Dispatchers.IO) {
        directory(id).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /** Only private data of this profile; failed cleanup is retryable, never a server mutation. */
    suspend fun remove(id: Long): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val profile = dao.get(id) ?: return@withLock false
            val folder = directory(id)
            if (folder.exists() && !folder.deleteRecursively()) return@withLock false
            vault.delete(profile.credentialId)
            database.withTransaction {
                val library = database.libraryDao()
                library.deleteSongsFor(id)
                library.deleteAlbumsFor(id)
                library.deleteArtistsFor(id)
                library.deleteGenresFor(id)
                library.deleteStateFor(id)
                database.browseDao().deletePlaylists(id)
                database.browseDao().deleteLocalAudio(id)
                dao.delete(id)
            }
            true
        }
    }
}
