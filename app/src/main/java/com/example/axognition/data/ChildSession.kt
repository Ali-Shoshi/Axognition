package com.example.axognition.data

import android.content.Context
import com.example.axognition.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ChildSession(
    val accessToken: String,
    val childId: String,
    val displayName: String,
    val grade: Int?
)

object ChildSessionStore {
    private const val PREFERENCES = "child_session"
    private const val TOKEN = "access_token"
    private const val CHILD_ID = "child_id"
    private const val DISPLAY_NAME = "display_name"
    private const val GRADE = "grade"

    fun load(context: Context): ChildSession? {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val token = preferences.getString(TOKEN, null) ?: return null
        val childId = preferences.getString(CHILD_ID, null) ?: return null
        val displayName = preferences.getString(DISPLAY_NAME, null) ?: return null
        return ChildSession(token, childId, displayName, preferences.getInt(GRADE, -1).takeIf { it >= 1 })
    }

    fun save(context: Context, session: ChildSession) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString(TOKEN, session.accessToken)
            .putString(CHILD_ID, session.childId)
            .putString(DISPLAY_NAME, session.displayName)
            .putInt(GRADE, session.grade ?: -1)
            .apply()
    }
}

object ChildAuthApi {
    fun login(username: String, password: String): ChildSession {
        val connection = URL("${BuildConfig.AXOGNITION_SERVER_URL.trimEnd('/')}/auth/child/login")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(JSONObject().put("username", username).put("password", password).toString())
            }
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() }.orEmpty()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                JSONObject(body.ifBlank { "{}" }).optString("error", "Could not sign in (HTTP ${connection.responseCode}).")
            }
            val response = JSONObject(body)
            val child = response.getJSONObject("child")
            ChildSession(
                accessToken = response.getString("accessToken"),
                childId = child.getString("childId"),
                displayName = child.getString("displayName"),
                grade = if (child.isNull("grade")) null else child.getInt("grade")
            )
        } finally {
            connection.disconnect()
        }
    }
}
