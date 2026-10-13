package com.walktape.app.core.library

import com.walktape.app.core.db.BrowseDao
import com.walktape.app.core.db.LocalAudioEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalAudioCatalog(private val dao: BrowseDao, private val profileDirectory: (Long) -> File) {
    suspend fun register(profileId: Long, songId: String, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        if (profileId <= 0 || songId.isBlank() || File(relativePath).isAbsolute) return@withContext false
        val root = profileDirectory(profileId).canonicalFile
        val file = File(root, relativePath).canonicalFile
        if (!file.path.startsWith(root.path + File.separator) || !file.isFile || file.length() <= 0) return@withContext false
        dao.insertLocalAudio(LocalAudioEntity(profileId, songId, relativePath, file.length()))
        true
    }
}
