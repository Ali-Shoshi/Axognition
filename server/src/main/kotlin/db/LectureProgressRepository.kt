package com.example.db

import com.example.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.sql.Connection
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

object LectureProgressRepository {
    fun get(child: UUID, definition: LectureDefinition): LectureState = DatabaseFactory.withConnection { c ->
        c.prepareStatement("SELECT state::text FROM child_lecture_progress WHERE child_id=? AND lecture_id=?").use { s ->
            s.setObject(1, child); s.setString(2, definition.id)
            s.executeQuery().use { rows -> definition.normalize(if (rows.next()) progressJson.decodeFromString<LectureState>(rows.getString(1)) else LectureState()) }
        }
    }

    fun list(child: UUID, after: String, limit: Int): JsonObject = DatabaseFactory.withConnection { c ->
        val items = mutableListOf<JsonElement>()
        var lastId = after
        var rowCount = 0
        c.prepareStatement("SELECT lecture_id,state::text FROM child_lecture_progress WHERE child_id=? AND lecture_id>? ORDER BY lecture_id LIMIT ?").use { s ->
            s.setObject(1, child); s.setString(2, after); s.setInt(3, limit + 1)
            s.executeQuery().use { rows -> while (rows.next()) {
                val id = rows.getString(1)
                rowCount++
                if (rowCount > limit) break
                lastId = id
                val definition = LectureCatalog.find(id) ?: continue
                val state = definition.normalize(progressJson.decodeFromString<LectureState>(rows.getString(2)))
                items += buildJsonObject { put("lectureId", id); put("state", progressJson.encodeToJsonElement(state)) }
            } }
        }
        buildJsonObject {
            put("items", JsonArray(items.take(limit)))
            put("nextCursor", if (rowCount > limit) JsonPrimitive(lastId) else JsonNull)
        }
    }

    fun ingest(child: UUID, definition: LectureDefinition, events: List<LectureEvent>): LectureSyncResponse = DatabaseFactory.withConnection { c ->
        c.autoCommit = false
        try {
            val initial = definition.normalize(LectureState())
            c.prepareStatement("INSERT INTO child_lecture_progress(child_id,lecture_id,state) VALUES (?,?,?::jsonb) ON CONFLICT DO NOTHING").use { s ->
                s.setObject(1, child); s.setString(2, definition.id); s.setString(3, progressJson.encodeToString(initial)); s.executeUpdate()
            }
            var state = c.prepareStatement("SELECT state::text FROM child_lecture_progress WHERE child_id=? AND lecture_id=? FOR UPDATE").use { s ->
                s.setObject(1, child); s.setString(2, definition.id)
                s.executeQuery().use { rows -> check(rows.next()); definition.normalize(progressJson.decodeFromString<LectureState>(rows.getString(1))) }
            }
            // One bulk INSERT per bounded batch. UUID deduplication and progress updates share a transaction.
            val slots = events.joinToString(",") { "(?,?,?,?,?,?,?,?::jsonb)" }
            val inserted = c.prepareStatement("INSERT INTO child_lecture_events(child_id,event_id,lecture_id,session_id,event_type,occurred_at,correct,event) VALUES $slots ON CONFLICT DO NOTHING RETURNING event_id").use { s ->
                var p = 1
                events.forEach { e ->
                    s.setObject(p++, child); s.setObject(p++, UUID.fromString(e.id)); s.setString(p++, definition.id)
                    s.setObject(p++, UUID.fromString(e.sessionId)); s.setString(p++, e.type); s.setObject(p++, OffsetDateTime.parse(e.occurredAt))
                    s.setObject(p++, definition.correct(e)); s.setString(p++, progressJson.encodeToString(e))
                }
                s.executeQuery().use { rows -> buildSet { while (rows.next()) add(rows.getObject(1).toString()) } }
            }
            events.filter { it.id in inserted }.forEach { event ->
                val newSession = event.type == "lecture_started" && insertSession(c, child, definition.id, event)
                state = LectureEvents.apply(definition, state, event, newSession)
            }
            state = state.copy(updatedAt = Instant.now().toString())
            c.prepareStatement("UPDATE child_lecture_progress SET state=?::jsonb,updated_at=CURRENT_TIMESTAMP WHERE child_id=? AND lecture_id=?").use { s ->
                s.setString(1, progressJson.encodeToString(state)); s.setObject(2, child); s.setString(3, definition.id); s.executeUpdate()
            }
            c.commit()
            LectureSyncResponse(events.map { it.id }, state)
        } catch (e: Exception) { c.rollback(); throw e }
        finally { c.autoCommit = true }
    }

    private fun insertSession(c: Connection, child: UUID, lecture: String, event: LectureEvent): Boolean =
        c.prepareStatement("INSERT INTO child_lecture_sessions(child_id,lecture_id,session_id,started_at) VALUES (?,?,?,?) ON CONFLICT DO NOTHING").use { s ->
            s.setObject(1, child); s.setString(2, lecture); s.setObject(3, UUID.fromString(event.sessionId)); s.setObject(4, OffsetDateTime.parse(event.occurredAt))
            s.executeUpdate() == 1
        }

    fun history(child: UUID, lecture: String, afterTime: String, afterId: UUID, limit: Int): JsonObject = DatabaseFactory.withConnection { c ->
        c.prepareStatement("SELECT event::text,correct,received_at,event_id FROM child_lecture_events WHERE child_id=? AND lecture_id=? AND (received_at,event_id) > (?::timestamptz,?) ORDER BY received_at,event_id LIMIT ?").use { s ->
            s.setObject(1, child); s.setString(2, lecture); s.setString(3, afterTime); s.setObject(4, afterId); s.setInt(5, limit + 1)
            s.executeQuery().use { rows ->
                val items = mutableListOf<JsonElement>()
                while (rows.next()) items += buildJsonObject {
                    put("event", progressJson.parseToJsonElement(rows.getString(1)))
                    val correct = rows.getObject(2) as Boolean?
                    put("correct", correct?.let(::JsonPrimitive) ?: JsonNull)
                    put("receivedAt", rows.getObject(3, OffsetDateTime::class.java).toString())
                    put("eventId", rows.getObject(4).toString())
                }
                buildJsonObject {
                    put("items", JsonArray(items.take(limit)))
                    put("nextCursor", if (items.size > limit) buildJsonObject {
                        put("afterTime", items[limit-1].jsonObject.getValue("receivedAt")); put("afterId", items[limit-1].jsonObject.getValue("eventId"))
                    } else JsonNull)
                }
            }
        }
    }
}
