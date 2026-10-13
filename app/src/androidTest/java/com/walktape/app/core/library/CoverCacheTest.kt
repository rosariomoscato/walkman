package com.walktape.app.core.library

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.walktape.app.core.net.Credentials
import com.walktape.app.core.net.OpenSubsonicClient
import com.walktape.app.core.net.ServerAddress
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CoverCacheTest {
    @Test fun cacheIsBoundedIsolatedAndReadableWithoutNetwork(): Unit = runBlocking {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "cover-test-${UUID.randomUUID()}")
        val png = ByteArrayOutputStream().use { out ->
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
        MockWebServer().apply { start() }.use { server ->
            try {
                val client = OpenSubsonicClient(ServerAddress.parse(server.url("/").toString(), true)!!,
                    Credentials("fixture", "fixture-password"))
                val folder = File(root, "profileA/covers")
                val cache = CoverArtStore(folder, { client }, maxBytes = png.size.toLong() + 1)
                repeat(2) { server.enqueue(MockResponse.Builder().body(Buffer().write(png)).addHeader("Content-Type", "image/png").build()) }
                assertNotNull(cache.bitmap("one", 16))
                assertNotNull(cache.bitmap("two", 16))
                assertTrue(folder.listFiles()!!.sumOf { it.length() } <= png.size.toLong() + 1)
                assertEquals(1, folder.listFiles()!!.size)
                val offline = CoverArtStore(folder, { null })
                assertNotNull(offline.bitmap("two", 16))
                assertEquals(Color.RED, offline.bitmap("two", 16)!!.getPixel(0, 0))
                val other = CoverArtStore(File(root, "profileB/covers"), { null })
                assertNull(other.bitmap("two", 16))
                assertEquals(2, server.requestCount)
            } finally { root.deleteRecursively() }
        }
    }
}
