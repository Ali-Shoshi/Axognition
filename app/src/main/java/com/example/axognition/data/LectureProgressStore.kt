package com.example.axognition.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import kotlin.math.roundToInt

data class LectureScore(val correct: Int, val total: Int, val attempts: Int, val retryAt: String?) {
    val percent: Int get() = (100.0 * correct / total).roundToInt()
    val passed: Boolean get() = correct * 4L > total * 3L
}

/** Offline completion cache, refreshed from server progress and scoped to the student. */
class LectureProgressStore(context: Context) {
    companion object { private val writeLock = Any() }
    val preferences: SharedPreferences = context.getSharedPreferences("lecture_progress", Context.MODE_PRIVATE)
    private val student = ChildSessionStore.load(context)?.childId ?: "guest"
    private val key = "finished:$student"
    fun playerKey(lectureId: String): String = "axognition-$lectureId-v1:$student"

    fun completedLectures(): Set<String> = preferences.getStringSet(key, emptySet())!!.toSet()

    fun scores(): Map<String, LectureScore> = preferences.all.keys.filter { it.startsWith("score:$student:") }
        .mapNotNull { cacheKey -> score(cacheKey.substringAfter("score:$student:"))?.let { cacheKey.substringAfter("score:$student:") to it } }.toMap()

    fun score(lectureId: String): LectureScore? = runCatching {
        val json = JSONObject(preferences.getString("score:$student:$lectureId", null) ?: return null)
        LectureScore(json.getInt("correct"), json.getInt("total"), json.getInt("attempts"), json.optString("retryAt").takeIf { it.isNotBlank() && it != "null" })
    }.getOrNull()

    /** Both the offline player and a server snapshot publish the same result summary. */
    fun saveResults(lectureId: String, state: JSONObject): Boolean = synchronized(writeLock) {
        val results = state.optJSONArray("lectureResults") ?: return@synchronized true
        val result = results.optJSONObject(results.length() - 1)
        val correct = result?.optInt("correct") ?: 0
        val total = result?.optInt("total") ?: 0
        if (result != null && (total <= 0 || correct !in 0..total)) return@synchronized false
        val serialized = result?.let {
            JSONObject().put("correct", correct).put("total", total).put("attempts", results.length())
                .put("retryAt", it.opt("retryAt")).toString()
        }
        val cacheKey = "score:$student:$lectureId"
        val current = completedLectures()
        val updated = if (result != null && state.optBoolean("passed", correct * 4L > total * 3L)) current + lectureId else current - lectureId
        if (preferences.getString(cacheKey, null) == serialized && current == updated) return@synchronized true
        preferences.edit().putString(cacheKey, serialized).putStringSet(key, updated).commit()
    }

    fun setCompleted(lectureId: String, completed: Boolean): Boolean = synchronized(writeLock) {
        val current = completedLectures()
        val updated = if (completed) current + lectureId else current - lectureId
        current == updated || preferences.edit().putStringSet(key, updated).commit()
    }
}
