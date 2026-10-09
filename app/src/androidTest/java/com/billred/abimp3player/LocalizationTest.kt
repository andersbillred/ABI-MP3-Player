package com.billred.abimp3player

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalizationTest {
    private fun contextFor(tag: String) = InstrumentationRegistry.getInstrumentation().targetContext.let { context ->
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        context.createConfigurationContext(config)
    }
    @Test fun swedishResourcesIncludeFormattedAndAccessibleLabels() {
        val context = contextFor("sv-SE")
        assertEquals("Spela", context.getString(R.string.play))
        assertEquals("SPELLISTA · 3", context.getString(R.string.playlist_count, 3))
        assertEquals("−12:34 kvar", context.getString(R.string.remaining_time, "12:34"))
        assertEquals("Ta bort Min podd från spellistan", context.getString(R.string.remove_episode, "Min podd"))
    }
    @Test fun englishUsesDefaultResources() {
        val context = contextFor("en-GB")
        assertEquals("Play", context.getString(R.string.play))
        assertEquals("PLAYLIST · 3", context.getString(R.string.playlist_count, 3))
        assertEquals("Podcast playback", context.getString(R.string.playback_channel))
    }
    @Test fun unsupportedLanguageFallsBackToEnglish() {
        val context = contextFor("fr-FR")
        assertEquals("Play", context.getString(R.string.play))
        assertEquals("−12:34 left", context.getString(R.string.remaining_time, "12:34"))
        assertEquals("Could not add the file", context.getString(R.string.add_file_failed))
    }
}
