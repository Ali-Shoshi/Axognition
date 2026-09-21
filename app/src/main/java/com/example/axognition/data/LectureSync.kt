package com.example.axognition.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.axognition.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Durable outbox: events are deleted only after the server acknowledges their UUIDs. */
class LectureOutbox private constructor(context: Context) : SQLiteOpenHelper(context, "lecture_sync.db", null, 1) {
    companion object {
        @Volatile private var instance: LectureOutbox? = null
        fun get(context: Context): LectureOutbox = instance ?: synchronized(this) {
            instance ?: LectureOutbox(context.applicationContext).also { instance = it }
        }
    }
    override fun onConfigure(db: SQLiteDatabase) { db.enableWriteAheadLogging() }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE outbox (seq INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT NOT NULL UNIQUE, child TEXT NOT NULL, origin TEXT NOT NULL, lecture TEXT NOT NULL, payload TEXT NOT NULL)")
        db.execSQL("CREATE INDEX outbox_owner ON outbox(child,origin,seq)")
        db.execSQL("CREATE TABLE snapshots (child TEXT NOT NULL,origin TEXT NOT NULL,lecture TEXT NOT NULL,payload TEXT NOT NULL,PRIMARY KEY(child,origin,lecture))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun enqueue(child: String, origin: String, lecture: String, payload: String): Boolean {
        if (payload.length > 16384) return false
        val event = JSONObject(payload)
        val values = ContentValues().apply {
            put("event_id", event.getString("id")); put("child", child); put("origin", origin)
            put("lecture", lecture); put("payload", payload)
        }
        if (writableDatabase.insertWithOnConflict("outbox", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) return true
        return readableDatabase.rawQuery("SELECT 1 FROM outbox WHERE event_id=? AND child=? AND origin=? AND lecture=? AND payload=?",
            arrayOf(event.getString("id"), child, origin, lecture, payload)).use { it.moveToFirst() }
    }
    fun pending(child: String, origin: String, lecture: String? = null): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM outbox WHERE child=? AND origin=?" + if (lecture == null) " LIMIT 1" else " AND lecture=? LIMIT 1",
        if (lecture == null) arrayOf(child, origin) else arrayOf(child, origin, lecture)
    ).use { it.moveToFirst() }
    fun batch(child: String, origin: String): Pair<String, JSONArray>? {
        val lecture = readableDatabase.rawQuery("SELECT lecture FROM outbox WHERE child=? AND origin=? ORDER BY seq LIMIT 1", arrayOf(child, origin))
            .use { if (it.moveToFirst()) it.getString(0) else null } ?: return null
        val events = JSONArray(); var bytes = 0
        readableDatabase.rawQuery("SELECT payload FROM outbox WHERE child=? AND origin=? AND lecture=? ORDER BY seq LIMIT 100", arrayOf(child, origin, lecture)).use { rows ->
            while (rows.moveToNext()) {
                val payload = rows.getString(0); val size = payload.toByteArray(Charsets.UTF_8).size
                if (bytes + size > 196608) break
                events.put(JSONObject(payload)); bytes += size
            }
        }
        return lecture to events
    }
    fun snapshot(child: String, origin: String, lecture: String): String? = readableDatabase.rawQuery(
        "SELECT payload FROM snapshots WHERE child=? AND origin=? AND lecture=?", arrayOf(child, origin, lecture)
    ).use { if (it.moveToFirst()) it.getString(0) else null }
    fun saveSnapshot(child: String, origin: String, lecture: String, state: JSONObject) {
        writableDatabase.insertWithOnConflict("snapshots", null, ContentValues().apply {
            put("child", child); put("origin", origin); put("lecture", lecture); put("payload", state.toString())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun acknowledge(child: String, origin: String, lecture: String, response: JSONObject) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ids = response.getJSONArray("acceptedIds")
            for (i in 0 until ids.length()) db.delete("outbox", "child=? AND origin=? AND lecture=? AND event_id=?", arrayOf(child, origin, lecture, ids.getString(i)))
            saveSnapshot(child, origin, lecture, response.getJSONObject("state"))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}

object LectureSync {
    val executor = Executors.newSingleThreadScheduledExecutor()
    private val queued = AtomicBoolean(false)
    private const val JOB_ID = 41027
    val origin: String get() = BuildConfig.AXOGNITION_SERVER_URL.trimEnd('/')

    fun schedule(context: Context) {
        val app = context.applicationContext
        val scheduler = app.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) == null) scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(app, LectureSyncJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPersisted(true)
            .setMinimumLatency(15_000).setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build())
        if (queued.compareAndSet(false, true)) executor.schedule({
            queued.set(false)
            runCatching { flush(app) }
        }, 5, TimeUnit.SECONDS)
    }

    /** Native code owns the token; it is never exposed in the WebView or a URL. */
    private fun request(session: ChildSession, path: String, payload: JSONObject? = null): JSONObject {
        val connection = URL(origin + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 7000; connection.readTimeout = 10000
        connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
        connection.setRequestProperty("Accept", "application/json")
        return try {
            if (payload != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
            }
            check(connection.responseCode in 200..299) { if (connection.responseCode == 401) "Sign in again to sync lecture progress." else "Lecture sync unavailable (HTTP ${connection.responseCode})." }
            connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }
    fun flush(context: Context, stopped: () -> Boolean = { false }): Boolean {
        val session = ChildSessionStore.load(context) ?: return false
        val store = LectureOutbox.get(context); val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!stopped() && System.nanoTime() < deadline) {
            if (ChildSessionStore.load(context)?.childId != session.childId) break
            val (lecture, events) = store.batch(session.childId, origin) ?: return false
            val response = request(session, "/me/lectures/$lecture/events", JSONObject().put("events", events))
            store.acknowledge(session.childId, origin, lecture, response)
            if (ChildSessionStore.load(context)?.childId == session.childId && !store.pending(session.childId, origin, lecture))
                LectureProgressStore(context).saveResults(lecture, response.getJSONObject("state"))
        }
        return store.pending(session.childId, origin)
    }
    fun load(context: Context, lecture: String, child: String): JSONObject {
        val session = ChildSessionStore.load(context)
        check(session?.childId == child) { "The signed-in student changed." }
        val store = LectureOutbox.get(context)
        flush(context)
        // Do not replace unsent local work with an older server snapshot.
        check(!store.pending(child, origin, lecture)) { "Lecture activity is waiting to sync." }
        val state = request(session!!, "/me/lectures/$lecture/progress")
        store.saveSnapshot(child, origin, lecture, state)
        LectureProgressStore(context).saveResults(lecture, state)
        return state
    }
    fun refreshCompletions(context: Context) {
        val app = context.applicationContext
        schedule(app)
        executor.execute {
            runCatching {
                val session = ChildSessionStore.load(app) ?: return@runCatching
                var cursor = ""
                do {
                    val page = request(session, "/me/lecture-progress?after=$cursor&limit=100")
                    val items = page.getJSONArray("items")
                    for (i in 0 until items.length()) {
                        if (ChildSessionStore.load(app)?.childId != session.childId) return@runCatching
                        val item = items.getJSONObject(i); val lecture = item.getString("lectureId")
                        if (!LectureOutbox.get(app).pending(session.childId, origin, lecture))
                            LectureProgressStore(app).saveResults(lecture, item.getJSONObject("state"))
                    }
                    cursor = if (page.isNull("nextCursor")) "" else page.getString("nextCursor")
                } while (cursor.isNotEmpty())
            }
        }
    }
}

class LectureSyncJob : JobService() {
    private var stopped = AtomicBoolean(false)
    override fun onStartJob(params: JobParameters): Boolean {
        val flag = AtomicBoolean(false); stopped = flag
        LectureSync.executor.execute {
            val pending = runCatching { LectureSync.flush(applicationContext) { flag.get() } }.getOrDefault(true)
            if (!flag.get()) jobFinished(params, pending)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { stopped.set(true); return true }
}
