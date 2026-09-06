package com.example.axognition.data

import android.content.Context
import com.example.axognition.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class RemoteBook(
    val id: String,
    val title: String,
    val author: String?,
    val description: String?,
    val category: String,
    val format: String,
    val fileSizeBytes: Long,
    val isDownloadable: Boolean,
    val coverUrl: String?
)

/** Development client for Ktor. MinIO credentials never belong in the Android app. */
object BookApi {
    private val baseUrl = BuildConfig.AXOGNITION_SERVER_URL.trimEnd('/')

    fun fetchBooks(): List<RemoteBook> {
        val books = JSONArray(getText("$baseUrl/books"))
        return List(books.length()) { books.getJSONObject(it).toRemoteBook() }
    }

    fun downloadBook(context: Context, bookId: String): File {
        val url = JSONObject(getText("$baseUrl/books/$bookId/download")).getString("downloadUrl")
        val destination = File(File(context.cacheDir, "books").apply { mkdirs() }, "$bookId.pdf")
        URL(url).openConnection().getInputStream().use { input ->
            FileOutputStream(destination).use { output -> input.copyTo(output) }
        }
        return destination
    }

    private fun getText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        return try {
            if (connection.responseCode !in 200..299) error("Server returned HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun JSONObject.toRemoteBook() = RemoteBook(
        id = getString("id"), title = getString("title"),
        author = optString("author").ifBlank { null },
        description = optString("description").ifBlank { null },
        category = getString("category"), format = getString("format"),
        fileSizeBytes = getLong("fileSizeBytes"), isDownloadable = getBoolean("isDownloadable"),
        coverUrl = optString("coverUrl").ifBlank { null }
    )
}
