package com.example.axognition.data

import com.example.axognition.BuildConfig
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class RemoteSubject(val id: String, val name: String)

object SubjectApi {
    fun fetchSubjects(grade: Int): List<RemoteSubject> {
        val connection = URL("${BuildConfig.AXOGNITION_SERVER_URL.trimEnd('/')}/subjects?grade=$grade")
            .openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        return try {
            check(connection.responseCode == 200) { "Could not load subjects (HTTP ${connection.responseCode})" }
            val rows = JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                RemoteSubject(row.getString("id"), row.getString("nameEnglish"))
            }
        } finally {
            connection.disconnect()
        }
    }
}
