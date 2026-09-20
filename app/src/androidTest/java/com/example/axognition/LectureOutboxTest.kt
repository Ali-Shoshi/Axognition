package com.example.axognition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.axognition.data.LectureOutbox
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LectureOutboxTest {
    @Test fun durableQueueIsIsolatedBoundedAndAcknowledgedAtomically() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = LectureOutbox.get(context)
        val child = UUID.randomUUID().toString()
        val other = UUID.randomUUID().toString()
        val origin = "https://lecture-sync-test.invalid"
        val id = UUID.randomUUID().toString()
        val event = JSONObject().put("id", id).put("type", "slide_next").toString()
        try {
            assertTrue(store.enqueue(child, origin, "geometry", event))
            assertTrue(store.enqueue(child, origin, "geometry", event))
            store.close() // Reopen the actual on-disk SQLite database, not an in-memory mock.
            assertTrue(store.pending(child, origin))
            assertFalse(store.pending(other, origin))
            assertFalse(store.pending(child, "https://another-server.invalid"))
            assertEquals(1, store.batch(child, origin)!!.second.length())
            val accepted = JSONArray().put(id)
            try {
                store.acknowledge(child, origin, "geometry", JSONObject().put("acceptedIds", accepted))
                fail("An incomplete server response must not delete queued activity")
            } catch (_: org.json.JSONException) { }
            assertTrue(store.pending(child, origin))
            store.acknowledge(child, origin, "geometry", JSONObject().put("acceptedIds", accepted).put("state", JSONObject().put("generation", 1)))
            assertFalse(store.pending(child, origin))
            assertEquals(1, JSONObject(store.snapshot(child, origin, "geometry")!!).getInt("generation"))
            repeat(101) { index ->
                assertTrue(store.enqueue(child, origin, "geometry", JSONObject().put("id", UUID.randomUUID().toString()).put("sequence", index).toString()))
            }
            val batch = store.batch(child, origin)!!.second
            assertEquals(100, batch.length()); assertEquals(0, batch.getJSONObject(0).getInt("sequence"))
            assertEquals(99, batch.getJSONObject(99).getInt("sequence"))
        } finally {
            // Only this test's random child/server fixtures are removed.
            store.writableDatabase.delete("outbox", "child=? AND origin=?", arrayOf(child, origin))
            store.writableDatabase.delete("snapshots", "child=? AND origin=?", arrayOf(child, origin))
        }
    }
}
