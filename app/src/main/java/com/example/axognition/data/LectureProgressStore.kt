package com.example.axognition.data

import android.content.Context
import android.content.SharedPreferences

/** Offline completion cache, refreshed from server progress and scoped to the student. */
class LectureProgressStore(context: Context) {
    companion object { private val writeLock = Any() }
    val preferences: SharedPreferences = context.getSharedPreferences("lecture_progress", Context.MODE_PRIVATE)
    private val student = ChildSessionStore.load(context)?.childId ?: "guest"
    private val key = "finished:$student"
    fun playerKey(lectureId: String): String = "axognition-$lectureId-v1:$student"

    fun completedLectures(): Set<String> = preferences.getStringSet(key, emptySet())!!.toSet()

    fun setCompleted(lectureId: String, completed: Boolean): Boolean = synchronized(writeLock) {
        val current = completedLectures()
        val updated = if (completed) current + lectureId else current - lectureId
        current == updated || preferences.edit().putStringSet(key, updated).commit()
    }
}
