package com.example

import java.time.Instant
import java.util.UUID
import kotlin.test.*

class LectureProgressTest {
    private val definition = LectureCatalog.find("fractions")!!
    private fun event(type: String, chapter: Int = 0, question: Int? = null, answer: Double? = null) = LectureEvent(
        id = UUID.randomUUID().toString(), sessionId = UUID.randomUUID().toString(), type = type,
        occurredAt = Instant.now().toString(), lessonVersion = definition.version, sequence = 0,
        chapter = chapter, question = question, answer = answer, checkpointRevision = definition.revision(chapter))

    @Test fun `each attempt is graded by the server and reset retains lifetime history`() {
        var state = LectureEvents.apply(definition, LectureState(), event("lecture_started"), true)
        val correct = definition.questions(0)[0].let { kotlinx.serialization.json.Json.decodeFromString<kotlinx.serialization.json.JsonObject>(it.toString()) }["answer"].toString().toDouble()
        state = LectureEvents.apply(definition, state, event("answer_submitted", question = 0, answer = correct + 1), false)
        state = LectureEvents.apply(definition, state, event("answer_submitted", question = 0, answer = correct), false)
        state = LectureEvents.apply(definition, state, event("slide_next"), false)
        state = LectureEvents.apply(definition, state, event("slide_back"), false)
        assertEquals(listOf(false, false), state.questionAnswers["0"])
        assertEquals(listOf(true, false), state.attemptedAnswers["0"], "A wrong first answer is locked")
        assertEquals(setOf("0:0"), state.completedSlides)
        assertEquals(1, state.startCount); assertEquals(1, state.wrongAnswerCount); assertEquals(1, state.backCount)
        state = LectureEvents.apply(definition, state, event("reset"), false)
        assertEquals(1, state.generation); assertTrue(state.completedSlides.isEmpty())
        assertEquals(1, state.startCount); assertEquals(1, state.wrongAnswerCount)
        state = LectureEvents.apply(definition, state, event("answer_submitted", question = 0, answer = correct), false)
        assertEquals(listOf(false, false), state.questionAnswers["0"], "Stale offline work cannot undo reset")
    }

    @Test fun `completed lecture survives review but revised questions invalidate completion`() {
        var state = LectureState()
        for (chapter in definition.chapters.indices) for (question in definition.questions(chapter).indices) {
            val answer = definition.questions(chapter)[question].toString().let {
                kotlinx.serialization.json.Json.decodeFromString<kotlinx.serialization.json.JsonObject>(it)["answer"].toString().toDouble()
            }
            state = LectureEvents.apply(definition, state, event("answer_submitted", chapter, question, answer), false)
        }
        assertNull(state.completedAt, "The result is recorded at the end, not on the last answer")
        state = LectureEvents.apply(definition, state, event("lecture_finished"), false)
        assertNotNull(state.completedAt)
        state = LectureEvents.apply(definition, state, event("slide_entered"), false)
        assertNotNull(state.completedAt)
        val revisions = state.checkpointRevisions.toMutableList().apply { this[lastIndex] = 1 }
        state = definition.normalize(state.copy(checkpointRevisions = revisions))
        assertNull(state.completedAt); assertEquals(listOf(false, false), state.questionAnswers["9"])
        assertEquals(listOf(true, true), state.questionAnswers["0"])
    }

    @Test fun `bounded validation accepts historical events without awarding revised content`() {
        assertFailsWith<IllegalArgumentException> { LectureEvents.validate(definition, listOf(event("slide_next").copy(occurredAt = "bad"))) }
        assertFailsWith<IllegalArgumentException> { LectureEvents.validate(definition, List(101) { event("slide_next") }) }
        assertFailsWith<IllegalArgumentException> { LectureEvents.validate(definition, listOf(event("answer_submitted", question = 0, answer = Double.NaN))) }
        val old = event("answer_submitted", question = 0, answer = 0.0).copy(lessonVersion = definition.version - 1, chapter = 999)
        LectureEvents.validate(definition, listOf(old))
        assertNull(definition.correct(old))
        assertEquals(0, LectureEvents.apply(definition, LectureState(), old, false).wrongAnswerCount)
        assertFailsWith<IllegalArgumentException> { LectureEvents.validate(definition, listOf(old.copy(lessonVersion = definition.version))) }
    }
}
