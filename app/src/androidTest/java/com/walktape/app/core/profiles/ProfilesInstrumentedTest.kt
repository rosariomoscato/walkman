package com.walktape.app.core.profiles

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.walktape.app.core.db.WalkTapeDatabase
import com.walktape.app.core.net.ApiResult
import com.walktape.app.core.net.ApiError
import com.walktape.app.core.net.ProtocolErrorKind
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProfilesInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun migrateFromShellPreservesMetadataAndRequiresLogin() {
        // v1 database exactly as exported by wave-02 schema (identity hash included).
        val name = "wave03-migration-test.db"
        context.deleteDatabase(name)
        val helper = object : SQLiteOpenHelper(context, name, null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `serverUrl` TEXT NOT NULL, `username` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_profile_name` ON `profile` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '75d6f83630e806846cc40cc2ba0b6e5b')")
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        helper.writableDatabase.execSQL("INSERT INTO profile (id,name,serverUrl,username,isActive,createdAt) VALUES (1,'Existing','https://example.org','user',1,123)")
        helper.close()
        // Room migrates 1->2 and validates the resulting schema against the v2 export.
        val db = Room.databaseBuilder(context, WalkTapeDatabase::class.java, name)
            .addMigrations(*WalkTapeDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val migrated = kotlinx.coroutines.runBlocking { db.profileDao().get(1) }!!
            assertEquals("Existing", migrated.name)
            assertEquals("https://example.org", migrated.serverUrl)
            assertEquals("user", migrated.username)
            // Wave-02 shell rows have no credentials: must not stay active after migration.
            assertFalse(migrated.isActive)
            assertEquals("", migrated.credentialId)
            assertEquals("", migrated.encryptedPassword)
            assertFalse(migrated.allowLocalHttp)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun keystoreRoundTripTamperCrossProfileAndDeletion() {
        val vault = CredentialVault()
        val a = UUID.randomUUID().toString()
        val b = UUID.randomUUID().toString()
        try {
            val encryptedA = vault.encrypt(a, "fixture-password-A")
            vault.encrypt(b, "fixture-password-B")
            assertTrue(vault.decrypt(a, encryptedA) == "fixture-password-A")
            assertFalse(encryptedA.contains("fixture-password"))
            var rejected = false
            try { vault.decrypt(b, encryptedA) } catch (_: Exception) { rejected = true }
            assertTrue("Cross-profile ciphertext must be rejected", rejected)
            rejected = false
            val changed = encryptedA.dropLast(4) + "AAAA"
            try { vault.decrypt(a, changed) } catch (_: Exception) { rejected = true }
            assertTrue("Tampered ciphertext must be rejected", rejected)
            vault.delete(a)
            rejected = false
            try { vault.decrypt(a, encryptedA) } catch (_: Exception) { rejected = true }
            assertTrue("Deleted profile key must not decrypt", rejected)
        } finally { vault.delete(a); vault.delete(b) }
    }

    @Test fun profilesKeepAuthenticationAndFilesIsolatedDuringConcurrentSwitchAndRemoval() = runBlocking {
        MockWebServer().apply { start() }.use { server ->
            val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
            val root = File(context.cacheDir, "profile-isolation-${UUID.randomUUID()}").apply { mkdirs() }
            val repo = ProfileRepository(db, CredentialVault(), root)
            fun enqueueOk() = server.enqueue(MockResponse.Builder().body("""{"subsonic-response":{"status":"ok","version":"1.16.1"}}""").build())
            var a: Long? = null
            var b: Long? = null
            try {
                enqueueOk()
                a = (repo.addVerified("A", server.url("/").toString(), "user-A", "secret-A", true) as ApiResult.Success).value
                enqueueOk()
                b = (repo.addVerified("B", server.url("/").toString(), "user-B", "secret-B", true) as ApiResult.Success).value
                assertTrue(server.takeRequest().url.queryParameter("u") == "user-A")
                assertTrue(server.takeRequest().url.queryParameter("u") == "user-B")
                assertEquals(a, db.profileDao().active()?.id)
                assertNotEquals(db.profileDao().get(a)!!.credentialId, db.profileDao().get(b)!!.credentialId)
                coroutineScope { listOf(async { repo.activate(a) }, async { repo.activate(b) }).forEach { it.await() } }
                assertTrue(db.profileDao().active()?.id in listOf(a, b))
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM profile WHERE isActive=1").use {
                    assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
                }
                repo.directory(a).mkdirs(); repo.directory(b).mkdirs()
                File(repo.directory(a), "fixture").writeText("A-data")
                File(repo.directory(b), "fixture").writeText("B-data")
                assertEquals(6, repo.localBytes(a))
                enqueueOk(); assertTrue(repo.verify(b) is ApiResult.Success)
                assertTrue(server.takeRequest().url.queryParameter("u") == "user-B")
                assertTrue(repo.remove(a))
                assertNull(repo.clientFor(a))
                assertEquals("B-data", File(repo.directory(b), "fixture").readText())
                assertFalse(repo.activate(999999))
            } finally {
                a?.let { repo.remove(it) }; b?.let { repo.remove(it) }
                db.close(); root.deleteRecursively()
            }
        }
    }

    @Test fun realServerFromEmulatorAndWrongCredentialsDoNotCreateProfiles() = runBlocking {
        // Pushed manually with adb for opt-in runs; never embedded in any APK.
        val envFile = java.io.File("/data/local/tmp/walktape-test-server.env")
        org.junit.Assume.assumeTrue("Opt-in local server config required", envFile.exists())
        val config = envFile.readLines().filter { it.isNotBlank() && !it.startsWith('#') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        val url = config.getValue("ND_URL").replace("localhost", "10.0.2.2")
        val db = Room.inMemoryDatabaseBuilder(context, WalkTapeDatabase::class.java).build()
        val root = File(context.cacheDir, "real-profile-${UUID.randomUUID()}").apply { mkdirs() }
        val repo = ProfileRepository(db, CredentialVault(), root)
        var id: Long? = null
        try {
            val wrong = repo.addVerified("wrong", url, config.getValue("ND_USER"), "invalid-${UUID.randomUUID()}", true)
            assertTrue("Wrong password must fail without exposing details", wrong is ApiResult.Failure &&
                wrong.error is ApiError.Protocol && wrong.error.kind == ProtocolErrorKind.AUTHENTICATION)
            assertEquals(0, db.profileDao().count())
            val valid = repo.addVerified("Real emulator connection", url, config.getValue("ND_USER"), config.getValue("ND_PASS"), true)
            assertTrue("Real emulator connection must succeed (details suppressed)", valid is ApiResult.Success)
            id = (valid as ApiResult.Success).value
            assertTrue("Protected saved credentials must reconnect", repo.verify(id) is ApiResult.Success)
            assertFalse(db.profileDao().get(id)!!.encryptedPassword.contains(config.getValue("ND_PASS")))
        } finally { id?.let { repo.remove(it) }; db.close(); root.deleteRecursively() }
    }
}
