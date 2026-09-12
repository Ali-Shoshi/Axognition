package com.example.axognition.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.Locale

/** A saved device preference, independent of student progress and navigation IDs. */
object AppLanguage {
    var code by mutableStateOf("en")
        private set
    val locale: Locale get() = Locale.forLanguageTag(if (code == "sq") "sq-AL" else "en-US")
    private var catalog = TranslationCatalog(emptyMap())

    fun initialize(context: Context) {
        val json = JSONObject(context.assets.open("i18n/sq.json").bufferedReader().use { it.readText() })
        catalog = TranslationCatalog(json.keys().asSequence().associateWith { json.getString(it) })
        code = context.getSharedPreferences("axognition_preferences", Context.MODE_PRIVATE)
            .getString("language", "en").let { if (it == "sq") "sq" else "en" }
        Locale.setDefault(locale)
    }

    fun select(context: Context, language: String) {
        require(language == "en" || language == "sq")
        context.getSharedPreferences("axognition_preferences", Context.MODE_PRIVATE)
            .edit().putString("language", language).apply()
        code = language
        Locale.setDefault(locale)
    }

    fun translate(text: String): String {
        return if (code == "sq") catalog.translate(text) else text
    }
}

/** Call only at display boundaries; never translate database IDs or route names. */
fun tr(text: String): String = AppLanguage.translate(text)

/** Prefer Google's higher-quality voices when the engine is installed. */
@Suppress("DEPRECATION")
fun createAppTextToSpeech(context: Context, listener: TextToSpeech.OnInitListener): TextToSpeech {
    val googleEngine = "com.google.android.tts"
    val googleIsAvailable = runCatching {
        context.packageManager.getApplicationInfo(googleEngine, 0).enabled
    }.getOrDefault(false)
    return if (googleIsAvailable) TextToSpeech(context, listener, googleEngine)
    else TextToSpeech(context, listener)
}

/**
 * Select the best voice exposed by the engine for the active app language.
 *
 * Callers can prefer an enhanced network voice or the faster installed local
 * voice when their quality and locale match. A local voice remains available
 * as the fallback if network synthesis cannot start.
 */
fun TextToSpeech.configureNaturalAppVoice(rate: Float = 0.96f, preferNetwork: Boolean = true): Boolean {
    val target = AppLanguage.locale
    if (setLanguage(target) < TextToSpeech.LANG_AVAILABLE) return false
    val bestVoice = voices.orEmpty()
        .filter { it.locale.language.equals(target.language, ignoreCase = true) }
        .maxWithOrNull(
            compareBy<Voice> { it.quality }
                .thenBy { if (it.locale.country.equals(target.country, ignoreCase = true)) 1 else 0 }
                .thenBy { if (it.isNetworkConnectionRequired == preferNetwork) 1 else 0 }
        )
    if (bestVoice != null && voice?.name != bestVoice.name) voice = bestVoice
    // A slightly slower, gently raised profile gives the installed voices more
    // room for consonants and makes the result less clipped and mechanical.
    setPitch(if (AppLanguage.code == "sq") 1.03f else 1.01f)
    setSpeechRate((rate * if (AppLanguage.code == "sq") 0.94f else 0.95f).coerceIn(0.35f, 2.0f))
    Log.i(
        "AXO_TTS",
        "language=${AppLanguage.code} voice=${bestVoice?.name ?: "default"} " +
            "quality=${bestVoice?.quality ?: -1} network=${bestVoice?.isNetworkConnectionRequired ?: false} " +
            "rate=${rate * if (AppLanguage.code == "sq") 0.94f else 0.95f}"
    )
    return true
}

fun TextToSpeech.speakInAppLanguage(
    text: String,
    utteranceId: String,
    rate: Float = 0.96f,
    queueMode: Int = TextToSpeech.QUEUE_FLUSH,
    preferNetwork: Boolean = true,
    naturalize: Boolean = true
): Int {
    if (!configureNaturalAppVoice(rate, preferNetwork)) return TextToSpeech.ERROR
    val spokenText = if (naturalize) naturalizeSpeechForTts(text, AppLanguage.code) else text
    val result = speak(spokenText, queueMode, null, utteranceId)
    if (result == TextToSpeech.ERROR && voice?.isNetworkConnectionRequired == true) {
        // A network voice can be listed while the device is temporarily
        // offline. Retry immediately with the best installed local voice.
        val localVoice = voices.orEmpty()
            .filter {
                it.locale.language.equals(AppLanguage.locale.language, ignoreCase = true) &&
                    !it.isNetworkConnectionRequired
            }
            .maxWithOrNull(
                compareBy<Voice> { it.quality }
                    .thenBy { if (it.locale.country.equals(AppLanguage.locale.country, ignoreCase = true)) 1 else 0 }
            )
        if (localVoice != null) {
            voice = localVoice
            setPitch(if (AppLanguage.code == "sq") 1.03f else 1.01f)
            setSpeechRate((rate * if (AppLanguage.code == "sq") 0.94f else 0.95f).coerceIn(0.35f, 2.0f))
            return speak(spokenText, queueMode, null, utteranceId)
        }
    }
    return result
}

@Composable
fun LanguageOptions(context: Context) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("en" to "English", "sq" to "Shqip").forEach { (code, label) ->
            FilterChip(selected = AppLanguage.code == code,
                onClick = { AppLanguage.select(context, code) }, label = { Text(label) })
        }
    }
}
