package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

internal val progressJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class LectureEvent(
    val id: String, val sessionId: String, val type: String, val occurredAt: String,
    val generation: Long = 0, val lessonVersion: Int, val sequence: Long,
    val chapter: Int = 0, val cue: Int = 0, val question: Int? = null,
    val checkpointRevision: Int = 1, val seconds: Double = 0.0,
    val elapsedMs: Long = 0, val activeMs: Long = 0, val sinceAttemptMs: Long = 0,
    val visitId: String? = null, val answer: Double? = null,
    val targetChapter: Int? = null, val targetCue: Int? = null,
    val revisit: Boolean = false, val value: String? = null,
    val importedAnswers: Map<String, List<Boolean>> = emptyMap(),
    val importedAttempts: Map<String, List<Boolean>> = emptyMap(),
    val importedSlides: Set<String> = emptySet()
)

@Serializable
data class LectureEventBatch(val events: List<LectureEvent>)

@Serializable
data class LectureState(
    val scoringVersion: Int = 0,
    val generation: Long = 0, val lessonVersion: Int = 0,
    val chapter: Int = 0, val cue: Int = 0, val seconds: Double = 0.0,
    val questionIndex: Int = 0, val inCheckpoint: Boolean = false,
    val completedSlides: Set<String> = emptySet(),
    val questionAnswers: Map<String, List<Boolean>> = emptyMap(),
    val attemptedAnswers: Map<String, List<Boolean>> = emptyMap(),
    val lectureResults: List<LectureResult> = emptyList(),
    val passed: Boolean = false, val legacyImported: Boolean = false,
    val checkpointRevisions: List<Int> = emptyList(),
    val completedAt: String? = null, val firstStartedAt: String? = null,
    val startCount: Long = 0, val wrongAnswerCount: Long = 0, val backCount: Long = 0,
    val cursorAt: String? = null, val updatedAt: String? = null
)

@Serializable
data class LectureUnitResult(val correct: Int, val total: Int, val percent: Int)

@Serializable
data class LectureResult(
    val generation: Long, val lessonVersion: Int, val checkpointRevisions: List<Int>,
    val correct: Int, val total: Int, val percent: Int, val passed: Boolean,
    val completedAt: String, val retryAt: String? = null, val units: List<LectureUnitResult>
)

@Serializable
data class LectureSyncResponse(val acceptedIds: List<String>, val state: LectureState)

data class LectureDefinition(val id: String, val version: Int, val chapters: List<JsonObject>) {
    fun revision(chapter: Int) = chapters[chapter]["checkpointRevision"]?.jsonPrimitive?.int ?: 1
    fun questions(chapter: Int) = chapters[chapter].getValue("questions").jsonArray
    fun cues(chapter: Int) = chapters[chapter].getValue("cues").jsonArray
    fun correct(event: LectureEvent): Boolean? {
        if (event.type != "answer_submitted") return null
        if (event.lessonVersion != version || event.checkpointRevision != revision(event.chapter)) return null
        return event.answer == questions(event.chapter)[event.question!!].jsonObject.getValue("answer").jsonPrimitive.double
    }
    fun normalize(state: LectureState): LectureState {
        val answers = chapters.indices.associate { i ->
            i.toString() to questions(i).indices.map { j ->
                state.checkpointRevisions.getOrNull(i) == revision(i) && state.questionAnswers[i.toString()]?.getOrNull(j) == true
            }
        }
        val attempted = chapters.indices.associate { i ->
            i.toString() to questions(i).indices.map { j ->
                state.checkpointRevisions.getOrNull(i) == revision(i) &&
                    (state.attemptedAnswers[i.toString()]?.getOrNull(j) == true || answers[i.toString()]!![j])
            }
        }
        var normalized = state.copy(scoringVersion = 1, lessonVersion = version,
            questionAnswers = answers, attemptedAnswers = attempted,
            checkpointRevisions = chapters.indices.map(::revision),
            chapter = state.chapter.coerceIn(chapters.indices), cue = state.cue.coerceIn(0, cues(state.chapter.coerceIn(chapters.indices)).lastIndex),
            questionIndex = state.questionIndex.coerceIn(questions(state.chapter.coerceIn(chapters.indices)).indices),
            completedSlides = state.completedSlides.filter(::validSlide).toSet())
        // Keep pre-scoring completions reviewable. Earlier first-answer scores were not stored.
        if (state.scoringVersion == 0 && state.completedAt != null && answers.values.all { it.all { right -> right } }) {
            normalized = normalized.copy(lectureResults = listOf(result(normalized, Instant.parse(state.completedAt))))
        }
        return normalized.copy(completedAt = currentResult(normalized)?.completedAt,
            passed = normalized.lectureResults.lastOrNull()?.let {
                it.passed && it.checkpointRevisions == normalized.checkpointRevisions &&
                    (it.generation != normalized.generation || allAttempted(normalized))
            } == true)
    }
    fun currentResult(state: LectureState) = state.lectureResults.lastOrNull()?.takeIf {
        it.generation == state.generation && it.checkpointRevisions == state.checkpointRevisions && allAttempted(state) &&
            it.total == chapters.indices.sumOf { i -> questions(i).size } && it.correct == state.questionAnswers.values.sumOf { answers -> answers.count { right -> right } }
    }
    fun allAttempted(state: LectureState) = chapters.indices.all { i ->
        questions(i).indices.all { state.attemptedAnswers[i.toString()]?.getOrNull(it) == true }
    }
    fun result(state: LectureState, at: Instant): LectureResult {
        val units = chapters.indices.map { i ->
            val total = questions(i).size
            val correct = state.questionAnswers[i.toString()]!!.count { it }
            LectureUnitResult(correct, total, (100.0 * correct / total).roundToInt())
        }
        val correct = units.sumOf { it.correct }; val total = units.sumOf { it.total }
        val passed = correct * 4L > total * 3L
        val wait = if (correct == total) 0L else if (!passed && state.lectureResults.none { !it.passed }) 3600L else 86400L
        return LectureResult(state.generation, version, state.checkpointRevisions, correct, total,
            (100.0 * correct / total).roundToInt(), passed, at.toString(),
            if (wait == 0L) null else at.plusSeconds(wait).toString(), units)
    }
    fun validSlide(key: String): Boolean {
        val parts = key.split(':').map { it.toIntOrNull() }
        return parts.size == 2 && parts[0] in chapters.indices && parts[1] in cues(parts[0]!!).indices
    }
}

object LectureCatalog {
    private val cache = object : LinkedHashMap<String, LectureDefinition>(32, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LectureDefinition>?) = size > 256
    }
    @Synchronized fun find(id: String): LectureDefinition? {
        if (!id.matches(Regex("[a-z0-9][a-z0-9-]{0,79}"))) return null
        return cache[id] ?: javaClass.classLoader.getResourceAsStream("lessons/$id.json")?.use { stream ->
            val json = progressJson.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
            LectureDefinition(id, json.getValue("version").jsonPrimitive.int, json.getValue("chapters").jsonArray.map { it.jsonObject })
                .also { cache[id] = it }
        }
    }
}

object LectureEvents {
    val types = setOf("lecture_started", "slide_entered", "slide_next", "slide_auto", "slide_back",
        "chapter_selected", "question_shown", "answer_submitted", "question_next", "lecture_finished",
        "lecture_exit", "pause", "resume", "voice_replay", "voice_speed", "mute", "explorer_changed",
        "hidden", "visible", "reset", "legacy_import")
    fun validate(definition: LectureDefinition, events: List<LectureEvent>) {
        require(events.size in 1..100) { "Send between 1 and 100 events." }
        require(events.map { it.id }.distinct().size == events.size) { "Duplicate event IDs in batch." }
        events.forEach { e ->
            UUID.fromString(e.id); UUID.fromString(e.sessionId); e.visitId?.let(UUID::fromString)
            require(runCatching { Instant.parse(e.occurredAt) }.isSuccess) { "Invalid event timestamp." }
            require(e.type in types && e.generation >= 0 && e.sequence >= 0 && e.lessonVersion > 0)
            require(e.chapter in 0..9999 && e.cue in 0..9999 && (e.question == null || e.question in 0..9999))
            if (e.lessonVersion == definition.version) {
                require(e.chapter in definition.chapters.indices && e.cue in definition.cues(e.chapter).indices)
                require(e.question == null || e.question in definition.questions(e.chapter).indices)
            }
            require(e.type != "answer_submitted" || e.question != null && e.answer?.isFinite() == true)
            require(e.answer == null || e.answer.isFinite())
            require(e.seconds.isFinite() && e.seconds in 0.0..86400.0)
            require(listOf(e.elapsedMs, e.activeMs, e.sinceAttemptMs).all { it in 0..2_592_000_000L })
            require(e.activeMs <= e.elapsedMs && e.sinceAttemptMs <= e.elapsedMs)
            require(e.value == null || e.value.length <= 80)
            require(e.targetChapter == null || e.targetChapter in 0..9999)
            require(e.targetCue == null || e.targetCue in 0..9999)
            require(e.importedAnswers.size <= definition.chapters.size && e.importedAttempts.size <= definition.chapters.size && e.importedSlides.size <= 10000)
            (e.importedAnswers.entries + e.importedAttempts.entries).forEach { (chapter, answers) ->
                require(chapter.toIntOrNull() in 0..9999 && answers.size <= 10000)
                if (e.lessonVersion == definition.version) {
                    require(chapter.toIntOrNull() in definition.chapters.indices)
                    require(answers.size == definition.questions(chapter.toInt()).size)
                }
            }
            if (e.lessonVersion == definition.version) require(e.importedSlides.all(definition::validSlide))
        }
    }

    /** Pure reducer: correctness comes from the server's lesson, never a client boolean. */
    fun apply(definition: LectureDefinition, original: LectureState, event: LectureEvent, newSession: Boolean,
              receivedAt: Instant = Instant.now()): LectureState {
        var state = definition.normalize(original)
        val at = minOf(Instant.parse(event.occurredAt), receivedAt)
        if (newSession) state = state.copy(startCount = state.startCount + 1,
            firstStartedAt = listOfNotNull(state.firstStartedAt, event.occurredAt).minOrNull())
        if (event.type == "slide_back") state = state.copy(backCount = state.backCount + 1)
        // Stale offline events remain in history, but cannot undo a reset or award revised questions.
        if (event.generation != state.generation || event.lessonVersion != definition.version) return state
        val result = definition.currentResult(state)
        if (event.type == "reset") {
            if (result?.retryAt?.let { at < Instant.parse(it) } == true) return state
            return definition.normalize(LectureState(scoringVersion = 1, generation = state.generation + 1,
                lectureResults = state.lectureResults, legacyImported = true,
                firstStartedAt = state.firstStartedAt, startCount = state.startCount, wrongAnswerCount = state.wrongAnswerCount, backCount = state.backCount))
        }
        // Completed attempts cannot be changed. Non-perfect attempts must be reset after their cooldown.
        if (result != null && result.correct != result.total) return state
        if (event.type in setOf("slide_next", "slide_auto")) state = state.copy(completedSlides = state.completedSlides + "${event.chapter}:${event.cue}")
        val correct = definition.correct(event)
        if (correct != null && result == null && state.attemptedAnswers[event.chapter.toString()]?.get(event.question!!) != true) {
            val answers = state.questionAnswers[event.chapter.toString()]!!.toMutableList()
            val attempted = state.attemptedAnswers[event.chapter.toString()]!!.toMutableList()
            answers[event.question!!] = correct; attempted[event.question] = true
            state = state.copy(questionAnswers = state.questionAnswers + (event.chapter.toString() to answers),
                attemptedAnswers = state.attemptedAnswers + (event.chapter.toString() to attempted),
                wrongAnswerCount = state.wrongAnswerCount + if (correct) 0 else 1)
        }
        // Legacy data has no raw responses; retain it explicitly as imported evidence, not new attempts.
        if (event.type == "legacy_import" && state.generation == 0L && !state.legacyImported && result == null) {
            val merged = state.questionAnswers.toMutableMap()
            val attempted = state.attemptedAnswers.toMutableMap()
            event.importedAnswers.forEach { (key, values) ->
                merged[key] = merged[key]!!.mapIndexed { i, old -> if (attempted[key]!![i]) old else values[i] }
                attempted[key] = attempted[key]!!.mapIndexed { i, old -> old || values[i] || event.importedAttempts[key]?.getOrNull(i) == true }
            }
            state = state.copy(questionAnswers = merged, attemptedAnswers = attempted, legacyImported = true,
                completedSlides = state.completedSlides + event.importedSlides)
        }
        if (event.type == "lecture_finished" && result == null && definition.allAttempted(state)) {
            val finished = definition.result(state, at)
            state = state.copy(lectureResults = state.lectureResults + finished, completedAt = finished.completedAt,
                passed = finished.passed, inCheckpoint = false)
        }
        if (state.cursorAt == null || Instant.parse(event.occurredAt) >= Instant.parse(state.cursorAt)) {
            if (event.type == "slide_entered") state = state.copy(chapter = event.chapter, cue = event.cue, seconds = event.seconds, inCheckpoint = false)
            if (event.type in setOf("question_shown", "answer_submitted")) state = state.copy(chapter = event.chapter, cue = event.cue, questionIndex = event.question ?: 0, inCheckpoint = true)
            if (event.type in setOf("pause", "hidden", "lecture_exit") && !state.inCheckpoint) state = state.copy(chapter = event.chapter, cue = event.cue, seconds = event.seconds)
            state = state.copy(cursorAt = event.occurredAt)
        }
        return state
    }
}
