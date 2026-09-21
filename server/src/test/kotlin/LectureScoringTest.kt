package com.example

import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class LectureScoringTest {
    private val now = Instant.parse("2026-09-20T12:00:00Z")
    private fun apply(def: LectureDefinition, state: LectureState, type: String, at: Instant = now,
                      chapter: Int = 0, question: Int? = null, correct: Boolean = true,
                      generation: Long = state.generation): LectureState {
        val answer = question?.let { def.questions(chapter)[it].jsonObject.getValue("answer").jsonPrimitive.double + if (correct) 0.0 else 1.0 }
        val event = LectureEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), type, at.toString(),
            generation = generation, lessonVersion = def.version, sequence = 0, chapter = chapter,
            question = question, answer = answer, checkpointRevision = def.revision(chapter))
        LectureEvents.validate(def, listOf(event))
        return LectureEvents.apply(def, state, event, type == "lecture_started", receivedAt = at)
    }
    private fun finish(def: LectureDefinition, initial: LectureState, right: Int, at: Instant = now): LectureState {
        var state = apply(def, initial, "lecture_started", at)
        var index = 0
        for (ch in def.chapters.indices) for (q in def.questions(ch).indices) {
            state = apply(def, state, "answer_submitted", at, ch, q, index++ < right)
        }
        return apply(def, state, "lecture_finished", at)
    }

    @Test fun `both lectures enforce scores cooldowns reset and immutable first answers`() {
        for (id in listOf("geometry", "fractions")) {
            val def = LectureCatalog.find(id)!!
            var state = finish(def, LectureState(), 15)
            assertEquals(75, state.lectureResults.single().percent)
            assertFalse(state.passed)
            assertEquals(now.plusSeconds(3600).toString(), state.lectureResults.single().retryAt)
            state = apply(def, state, "answer_submitted", question = 0, chapter = 9)
            assertEquals(15, state.lectureResults.single().correct)
            assertFalse(state.questionAnswers["9"]!![0])
            assertEquals(0L, apply(def, state, "reset", now.plusSeconds(3599)).generation)
            state = apply(def, state, "reset", now.plusSeconds(3600))
            assertEquals(1L, state.generation)
            assertTrue(state.attemptedAnswers.values.flatten().none { it })
            assertNull(state.completedAt)
            assertEquals(1, state.lectureResults.size)
            val stale = apply(def, state, "answer_submitted", now.plusSeconds(3600), question = 0, generation = 0)
            assertFalse(stale.attemptedAnswers["0"]!![0])
            state = finish(def, state, 0, now.plusSeconds(3600))
            assertEquals(now.plusSeconds(3600 + 86400).toString(), state.lectureResults.last().retryAt)
            state = apply(def, state, "reset", now.plusSeconds(3600 + 86400))
            state = finish(def, state, 16, now.plusSeconds(3600 + 86400))
            assertTrue(state.passed)
            assertEquals(80, state.lectureResults.last().percent)
            assertEquals(3, state.lectureResults.size)
            assertEquals(10, state.lectureResults.last().units.size)
            state = apply(def, state, "reset", now.plusSeconds(3600 + 2 * 86400))
            state = finish(def, state, 20, now.plusSeconds(3600 + 2 * 86400))
            assertNull(state.lectureResults.last().retryAt)
            val reviewed = apply(def, state, "lecture_finished", now.plusSeconds(3601 + 2 * 86400))
            assertEquals(state.lectureResults, reviewed.lectureResults, "Review must not add a try")
            val restored = progressJson.decodeFromString<LectureState>(progressJson.encodeToString(LectureState.serializer(), reviewed))
            assertEquals(reviewed, def.normalize(restored), "Scores, answer locks and retry times survive storage")
        }
    }

    @Test fun `partial lecture cannot finish and wrong answers cannot be corrected`() {
        val def = LectureCatalog.find("geometry")!!
        var state = apply(def, LectureState(), "answer_submitted", chapter = 9, question = 0, correct = false)
        state = apply(def, state, "answer_submitted", chapter = 9, question = 0, correct = true)
        state = apply(def, state, "answer_submitted", chapter = 9, question = 1)
        state = apply(def, state, "lecture_finished")
        assertNull(state.completedAt); assertTrue(state.lectureResults.isEmpty())
        assertEquals(listOf(false, true), state.questionAnswers["9"])
        assertEquals(listOf(true, true), state.attemptedAnswers["9"])
        assertEquals(1L, state.wrongAnswerCount)
    }

    @Test fun `variable question counts and first failure after an earlier pass`() {
        val base = LectureCatalog.find("fractions")!!
        val chapters = base.chapters.toMutableList()
        chapters[0] = JsonObject(chapters[0] + ("questions" to JsonArray(base.questions(0) + base.questions(0)[1])))
        val def = base.copy(chapters = chapters)
        val failed = finish(def, LectureState(), 15)
        assertEquals(21, failed.lectureResults.single().total); assertFalse(failed.passed)
        var state = finish(def, LectureState(), 16)
        assertTrue(state.passed); assertEquals(76, state.lectureResults.single().percent)
        assertEquals(now.plusSeconds(86400).toString(), state.lectureResults.single().retryAt)
        state = apply(def, state, "reset", now.plusSeconds(86400))
        state = finish(def, state, 0, now.plusSeconds(86400))
        assertEquals(now.plusSeconds(86400 + 3600).toString(), state.lectureResults.last().retryAt)
    }

    @Test fun `future client timestamps cannot bypass server cooldown`() {
        val def = LectureCatalog.find("geometry")!!
        val state = finish(def, LectureState(), 0)
        val event = LectureEvent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "reset",
            now.plusSeconds(86400).toString(), lessonVersion = def.version, sequence = 1)
        assertEquals(0L, LectureEvents.apply(def, state, event, false, receivedAt = now).generation)
    }
}
