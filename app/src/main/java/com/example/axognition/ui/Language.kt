package com.example.axognition.ui

import android.content.Context
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

@Composable
fun LanguageOptions(context: Context) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("en" to "English", "sq" to "Shqip").forEach { (code, label) ->
            FilterChip(selected = AppLanguage.code == code,
                onClick = { AppLanguage.select(context, code) }, label = { Text(label) })
        }
    }
}
