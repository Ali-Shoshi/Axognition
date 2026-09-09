package com.example.axognition.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.speech.tts.TextToSpeech
import android.util.Log
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercise the shipped catalog with Android's ICU engine, not the desktop JVM. */
@RunWith(AndroidJUnit4::class)
class TranslationCatalogAndroidTest {
    @Test fun bundledCatalogLoadsAndSubstitutesOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val json = JSONObject(context.assets.open("i18n/sq.json").bufferedReader().use { it.readText() })
        val catalog = TranslationCatalog(json.keys().asSequence().associateWith { json.getString(it) })
        assertEquals("Cilësimet", catalog.translate("Settings"))
        assertEquals("Mirë se u ktheve, Çlirim", catalog.translate("Welcome back, Çlirim"))
        assertEquals("3 nga 10 të përfunduara", catalog.translate("3 of 10 completed"))
    }

    @Test fun naturalVoiceCanBeSelectedForBothLanguages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppLanguage.initialize(context)
        val originalLanguage = AppLanguage.code
        val initialized = CountDownLatch(1)
        var initStatus = TextToSpeech.ERROR
        val speech = createAppTextToSpeech(context) { status ->
            initStatus = status
            initialized.countDown()
        }
        try {
            assertTrue("TTS initialization timed out", initialized.await(15, TimeUnit.SECONDS))
            assertEquals(TextToSpeech.SUCCESS, initStatus)
            for (language in listOf("en", "sq")) {
                AppLanguage.select(context, language)
                val available = speech.configureNaturalAppVoice()
                val candidates = speech.voices.orEmpty()
                    .filter { it.locale.language.equals(AppLanguage.locale.language, ignoreCase = true) }
                    .joinToString { "${it.name}:q${it.quality}:${if (it.isNetworkConnectionRequired) "network" else "local"}" }
                Log.i("AXO_TTS", "$language candidates=$candidates")
                Log.i("AXO_TTS", "$language available=$available voice=${speech.voice?.name} quality=${speech.voice?.quality}")
                assertTrue("No $language voice is available in the selected TTS engine", available)
            }
        } finally {
            AppLanguage.select(context, originalLanguage)
            speech.shutdown()
        }
    }
}
