package com.walktape.app.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun validQueue(): BackupQueue {
        val items = listOf(
            BackupQueueItem("queue/a1", "s1", "Title one", "Artist", "Album", 120_000L, "Playlist"),
            BackupQueueItem("queue/a2", "s2", "Title two", "Artist", "Album", 130_000L, null),
            BackupQueueItem("queue/a3", "s1", "Title one", "Artist", "Album", 120_000L, null),
        )
        return BackupQueue(items, listOf("queue/a1", "queue/a3", "queue/a2"),
            "queue/a2", 12_345L, 2, true, "Mix sera")
    }

    private fun validBackup(): BackupFile {
        val profile = BackupProfile(
            name = "Home", serverUrl = "http://192.168.1.10:4533", username = "demo",
            allowLocalHttp = true,
            settings = BackupProfileSettings(true, false, true, false, 192, false),
            downloads = listOf(
                BackupDownload("s1", "Title one", "Artist", "Album", "al1", "cov1", 120,
                    "mp3", "audio/mpeg", 3_000_000, "ORIGINAL", null, listOf("single")),
                BackupDownload("s2", "Title two", "Artist", "Album", "al1", "cov2", 130,
                    "flac", "audio/flac", 30_000_000, "REDUCED", 96, listOf("playlist:42")),
            ),
            groups = listOf(BackupDownloadGroup("playlist:42", "Road", listOf("s2", "s1", "s2"), false)),
            queue = validQueue(),
        )
        return BackupFile(
            format = BackupCodec.FORMAT, version = BackupCodec.VERSION,
            createdAt = 1_700_000_000_000, appVersion = "0.1.0",
            preferences = BackupPreferences(
                themeMode = "DARK", suggestionsEnabled = false, hapticsEnabled = true,
                keyClicksEnabled = false, audioCacheLimitBytes = 1024L * 1024 * 1024,
                coverCacheLimitBytes = 250L * 1024 * 1024, crossfadeEnabled = false,
                crossfadeDurationMs = 3000, replayGainMode = "OFF", equalizerEnabled = false,
                equalizerBands = "", bassBoostEnabled = false, bassBoostStrength = 500,
                tapeEffectEnabled = false, appLockEnabled = false),
            profiles = listOf(profile),
        )
    }

    private fun roundTrip(backup: BackupFile): BackupCodec.Parsed =
        BackupCodec.parse(BackupCodec.write(backup))

    private fun withProfile(transform: (BackupProfile) -> BackupProfile): BackupFile {
        val base = validBackup()
        return base.copy(profiles = base.profiles.map(transform))
    }

    @Test
    fun `round trips a valid backup`() {
        val parsed = roundTrip(validBackup())
        assertTrue(parsed is BackupCodec.Parsed.Valid)
        val backup = (parsed as BackupCodec.Parsed.Valid).backup
        assertEquals("Home", backup.profiles.first().name)
        val queue = backup.profiles.first().queue!!
        assertEquals(3, queue.items.size)
        assertEquals(listOf("queue/a1", "queue/a3", "queue/a2"), queue.traversal)
        assertEquals("Mix sera", queue.mixtapeName)
        assertEquals(2, queue.repeatMode)
    }

    @Test
    fun `round trip json carries no credential fields`() {
        val json = BackupCodec.write(validBackup())
        assertTrue(json.contains("serverUrl"))
        assertTrue(!json.contains("credentialId"))
        assertTrue(!json.contains("encryptedPassword"))
        assertTrue(!json.contains("password\":"))
    }

    @Test
    fun `rejects non json content`() {
        assertEquals(BackupCodec.Reason.NOT_JSON,
            (BackupCodec.parse("not a backup {{{") as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects a different format marker`() {
        val json = """{"format":"other.backup","version":1,"createdAt":1,"appVersion":"x","profiles":[]}"""
        assertEquals(BackupCodec.Reason.NOT_BACKUP,
            (BackupCodec.parse(json) as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects future backup versions`() {
        val json = BackupCodec.write(validBackup()).replaceFirst("\"version\":1", "\"version\":2")
        assertEquals(BackupCodec.Reason.UNSUPPORTED_VERSION,
            (BackupCodec.parse(json) as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects invalid profile urls`() {
        val backup = withProfile { it.copy(serverUrl = "htp://bad url") }
        assertEquals(BackupCodec.Reason.INVALID_PROFILE,
            (roundTrip(backup) as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects duplicate download song ids`() {
        val backup = withProfile { it.copy(downloads = it.downloads + it.downloads.first()) }
        assertEquals(BackupCodec.Reason.INVALID_DOWNLOAD,
            (roundTrip(backup) as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects unknown download origins`() {
        val backup = withProfile { profile ->
            profile.copy(downloads = profile.downloads.map {
                it.copy(origins = listOf("weird"))
            })
        }
        assertEquals(BackupCodec.Reason.INVALID_DOWNLOAD,
            (roundTrip(backup) as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects queues whose traversal is not a permutation`() {
        val backup = withProfile {
            it.copy(queue = validQueue().copy(
                traversal = listOf("queue/a1", "queue/a2", "queue/missing")))
        }
        assertEquals(BackupCodec.Reason.INVALID_QUEUE,
            (roundTrip(backup) as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects queue entries without the queue id prefix`() {
        val queue = validQueue()
        val backup = withProfile {
            it.copy(queue = queue.copy(
                items = queue.items.map { item -> item.copy(entryId = item.entryId.removePrefix("queue/")) },
                traversal = queue.traversal.map { id -> id.removePrefix("queue/") },
                currentId = "a2"))
        }
        assertEquals(BackupCodec.Reason.INVALID_QUEUE,
            (roundTrip(backup) as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects a current id outside the queue`() {
        val backup = withProfile { it.copy(queue = validQueue().copy(currentId = "queue/nope")) }
        assertEquals(BackupCodec.Reason.INVALID_QUEUE,
            (roundTrip(backup) as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects duplicate queue entry ids`() {
        val queue = validQueue()
        val backup = withProfile { it.copy(queue = queue.copy(items = queue.items + queue.items.first())) }
        assertEquals(BackupCodec.Reason.INVALID_QUEUE,
            (roundTrip(backup) as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects out of range preferences`() {
        val backup = validBackup().copy(
            preferences = validBackup().preferences!!.copy(crossfadeDurationMs = 10))
        assertEquals(BackupCodec.Reason.INVALID_PREFERENCES,
            (roundTrip(backup) as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `rejects too many profiles`() {
        val template = validBackup().profiles.first()
        val many = (1..33).map { template.copy(name = "P$it") }
        val parsed = roundTrip(validBackup().copy(profiles = many))
        assertEquals(BackupCodec.Reason.TOO_LARGE, (parsed as BackupCodec.Parsed.Invalid).reason)
    }

    @Test
    fun `accepts an explicitly empty queue for a profile`() {
        val backup = withProfile {
            it.copy(queue = BackupQueue(emptyList(), emptyList(), null, 0L, 0, false, null))
        }
        assertNull(BackupCodec.validate(backup))
        assertNotNull(roundTrip(backup) as? BackupCodec.Parsed.Valid)
    }
}
