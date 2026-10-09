package com.billred.abimp3player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun clearLibrary() { context.getSharedPreferences("podcast_library_test", 0).edit().clear().commit() }
    @After fun cleanup() { clearLibrary() }
    @Test fun reopeningRestoresOrderSelectionAndIndependentPositions() {
        val first = Episode("content://test/first", "Första podden")
        val second = Episode("content://test/second", "Andra podden")
        PlaylistStore(context, "podcast_library_test").apply {
            save(listOf(second, first), first.uri)
            savePosition(first.uri, 127000)
            savePosition(second.uri, 63000)
        }
        val reopened = PlaylistStore(context, "podcast_library_test")
        assertEquals(listOf(second, first), reopened.episodes())
        assertEquals(first.uri, reopened.current())
        assertEquals(127000, reopened.position(first.uri))
        assertEquals(63000, reopened.position(second.uri))
    }
    @Test fun removingAndReaddingDoesNotLoseListeningPosition() {
        val episode = Episode("content://test/episode", "Podd")
        PlaylistStore(context, "podcast_library_test").apply { save(listOf(episode), episode.uri); savePosition(episode.uri, 42000); save(emptyList(), null) }
        val reopened = PlaylistStore(context, "podcast_library_test")
        assertTrue(reopened.episodes().isEmpty())
        assertNull(reopened.current())
        reopened.save(listOf(episode), episode.uri)
        assertEquals(42000, PlaylistStore(context, "podcast_library_test").position(episode.uri))
    }
}
