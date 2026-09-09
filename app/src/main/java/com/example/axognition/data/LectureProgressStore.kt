package com.example.axognition.data

import android.content.Context
import android.content.SharedPreferences

/** Device-local completion, scoped to the signed-in student. */
class LectureProgressStore(context: Context) {
    val preferences: SharedPreferences = context.getSharedPreferences("lecture_progress", Context.MODE_PRIVATE)
    private val student = ChildSessionStore.load(context)?.childId ?: "guest"
    private val key = "finished:$student"
    val geometryPlayerKey = "axognition-geometry-v1:$student"

    fun completedLectures(): Set<String> = preferences.getStringSet(key, emptySet())!!.toSet()

    @Synchronized
    fun setCompleted(lectureId: String, completed: Boolean): Boolean {
        val current = completedLectures()
        val updated = if (completed) current + lectureId else current - lectureId
        return current == updated || preferences.edit().putStringSet(key, updated).commit()
    }
}
