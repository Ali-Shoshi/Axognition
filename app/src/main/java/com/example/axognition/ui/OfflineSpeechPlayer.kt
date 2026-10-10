package com.example.axognition.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.Keep
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal enum class OfflineVoiceRole(val assetDirectory: String) {
    LECTURE("lecture"), ASSISTANT("assistant")
}
internal enum class SpeechQueueMode { REPLACE, ADD }

/** Shared, on-device neural speech with fixed lecture and assistant speakers. */
internal class OfflineSpeechPlayer(
    context: Context,
    private val onStatus: (String) -> Unit = {},
    private val onStarted: (String, Long) -> Unit = { _, _ -> },
    private val onEnded: (String, Boolean) -> Unit = { _, _ -> },
    private val onRange: (String, Int, Int) -> Unit = { _, _, _ -> },
    private val role: OfflineVoiceRole = OfflineVoiceRole.LECTURE
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong()
    private val prefetchGeneration = AtomicLong()
    @Volatile private var closed = false
    // Native access is serialized on the worker. Queue and playback belong to main.
    private var model: OfflineTts? = null
    private var modelLanguage: String? = null
    private var player: MediaPlayer? = null
    private val pending = ArrayDeque<SpeechRequest>()
    private var current: SpeechRequest? = null
    private val cache = File(app.cacheDir, "lecture-narration")
    private val temporary = File(app.cacheDir, "assistant-speech")

    fun warmUp(language: String) {
        if (closed || language !in LANGUAGES) return
        voiceWork {
            if (closed) return@voiceWork
            runCatching { voice(language) }
                .onSuccess { status("") }
                .onFailure {
                    Log.e(TAG, "Could not prepare offline speech", it)
                    status("Voice unavailable. You can still use captions.")
                }
        }
    }

    /** Generate a lecture cue ahead of playback, while the preceding cue is on screen. */
    fun prefetch(text: String, language: String) {
        if (closed || role != OfflineVoiceRole.LECTURE || text.isBlank() || language !in LANGUAGES) return
        val token = prefetchGeneration.incrementAndGet()
        for (part in lectureSpeechParts(naturalizeSpeechForTts(text, language))) {
            voiceWork(priority = 1) {
                if (closed || token != prefetchGeneration.get()) return@voiceWork
                runCatching {
                    val file = lectureFile(part, language)
                    if (!file.isFile || file.length() <= 44) generateFile(file, part, language) {
                        !closed && token == prefetchGeneration.get()
                    }
                    if (file.isFile) trimCache(file)
                }.onFailure { Log.w(TAG, "Could not prefetch lecture narration", it) }
            }
        }
    }

    /** Read-aloud buttons need early audio and one completion for the entire answer. */
    fun readAloud(text: String, id: String, language: String, rate: Float = 1f): Boolean =
        speak(text, id, language, rate, smallParts = true)

    /** Bound synthesis time even when a short formula expands into many spoken words. */
    fun speakStreamingChunk(text: String, id: String, language: String, rate: Float = 1f): Boolean =
        speak(text, id, language, rate, queueMode = SpeechQueueMode.ADD,
            smallParts = true, highlightWords = true)

    fun speak(text: String, id: String, language: String, rate: Float = 1f,
              queueMode: SpeechQueueMode = SpeechQueueMode.REPLACE, naturalize: Boolean = true,
              smallParts: Boolean = role == OfflineVoiceRole.LECTURE,
              highlightWords: Boolean = !smallParts || role == OfflineVoiceRole.LECTURE): Boolean {
        if (closed || text.isBlank() || language !in LANGUAGES) return false
        val lecture = role == OfflineVoiceRole.LECTURE
        val mapped = if (lecture || highlightWords) {
            if (naturalize) naturalizeSpeechWithRanges(text, language) else MappedSpeechText.original(text)
        } else null
        val parts = if (smallParts) lectureSpeechParts(
            mapped?.text ?: if (naturalize) naturalizeSpeechForTts(text, language) else text,
            firstPartLimit = if (lecture) 80 else 48)
            else listOf(text)
        if (parts.isEmpty()) return false
        val ticket = if (queueMode == SpeechQueueMode.REPLACE) generation.incrementAndGet() else generation.get()
        if (queueMode == SpeechQueueMode.REPLACE) prefetchGeneration.incrementAndGet()
        var spokenOffset = 0
        val requests = parts.mapIndexed { index, part ->
            val wordRanges = if (mapped != null && smallParts) {
                val start = mapped.text.indexOf(part, spokenOffset)
                spokenOffset = start + part.length
                mapped.words(start, spokenOffset)
            } else mapped?.words()
            SpeechRequest(ticket, id, part, language, rate.coerceIn(0.5f, 2f), naturalize && !smallParts,
                finalPart = index == parts.lastIndex,
                reportWordRanges = highlightWords, wordRanges = wordRanges).also { request ->
                if (lecture) {
                    val cached = lectureFile(part, language)
                    if (cached.isFile && cached.length() > 44) {
                        cached.setLastModified(System.currentTimeMillis())
                        request.file = cached
                        request.ready = true
                    }
                }
            }
        }
        onMain {
            if (!active(ticket)) return@onMain
            if (queueMode == SpeechQueueMode.REPLACE) clearPlayback()
            pending.addAll(requests)
            if (current == null) onStatus("Preparing narration…")
            // Prefetch queued chat sentences while the preceding sentence plays.
            requests.filterNot { it.ready }.forEach { request -> voiceWork { prepare(request) } }
            // Cached slides bypass model loading and prefetch work altogether.
            drain()
        }
        return true
    }

    /** A completion sentinel, ordered after all previously queued speech. */
    fun enqueueSilence(durationMs: Long, id: String): Boolean {
        if (closed) return false
        val request = SpeechRequest(generation.get(), id, silenceMs = durationMs.coerceIn(0, 60000), ready = true)
        onMain {
            if (active(request.ticket)) { pending.add(request); drain() }
        }
        return true
    }

    fun stop() {
        val ticket = generation.incrementAndGet()
        onMain {
            if (generation.get() == ticket) { clearPlayback(); if (!closed) onStatus("") }
        }
    }

    fun close() {
        closed = true
        prefetchGeneration.incrementAndGet()
        stop()
        voiceWork { releaseVoice() }
    }

    private fun active(ticket: Long) = !closed && generation.get() == ticket

    private fun status(message: String) = onMain {
        if (!closed && current == null && pending.isEmpty()) onStatus(message)
    }

    private fun prepare(request: SpeechRequest) {
        if (!active(request.ticket)) return
        var file: File? = null
        try {
            val spoken = if (request.naturalize) naturalizeSpeechForTts(request.text, request.language) else request.text
            val persistent = role == OfflineVoiceRole.LECTURE
            if (persistent) {
                file = lectureFile(spoken, request.language)
            } else {
                temporary.mkdirs()
                file = File(temporary, "${UUID.randomUUID()}.wav")
            }
            if (!file.isFile || file.length() <= 44) {
                generateFile(file, spoken, request.language) { active(request.ticket) }
            }
            if (!active(request.ticket)) { if (!persistent) file.delete(); return }
            file.setLastModified(System.currentTimeMillis())
            if (persistent) trimCache(file)
            val audioFile = file
            onMain {
                if (active(request.ticket)) {
                    request.file = audioFile
                    request.temporary = !persistent
                    request.ready = true
                    drain()
                } else if (!persistent) audioFile.delete()
            }
        } catch (error: Exception) {
            if (role == OfflineVoiceRole.ASSISTANT) file?.delete()
            Log.e(TAG, "Offline speech failed", error)
            onMain {
                if (active(request.ticket)) { request.failed = true; request.ready = true; drain() }
            }
        }
    }

    private fun lectureFile(spoken: String, language: String): File {
        cache.mkdirs()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$MODEL_VERSION|${role.assetDirectory}|$language|$spoken".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(Locale.ROOT, it) }
        return File(cache, "$hash.wav")
    }

    private fun generateFile(file: File, spoken: String, language: String, alive: () -> Boolean) {
        val synthesizer = voice(language)
        if (!alive()) return
        val audio = synthesizer.generateWithCallback(spoken, speed = 0.96f,
            callback = SynthesisCallback(alive))
        if (!alive()) return
        check(audio.samples.isNotEmpty()) { "The neural voice produced no audio" }
        val partial = File(file.parentFile, "${file.name}.partial")
        check(audio.save(partial.absolutePath)) { "Could not save generated speech" }
        check(partial.renameTo(file)) { "Could not finish generated speech" }
    }

    private fun drain() {
        if (closed || current != null || pending.isEmpty() || !pending.first.ready) return
        val request = pending.removeFirst()
        if (!active(request.ticket)) { disposeFile(request); drain(); return }
        current = request
        if (request.failed) { finish(request, false); return }
        if (request.silenceMs != null) {
            onStarted(request.id, request.silenceMs)
            main.postDelayed({ if (isCurrent(request)) finish(request, true) }, request.silenceMs)
        } else play(request)
    }

    private fun play(request: SpeechRequest) {
        val next = MediaPlayer()
        player = next
        try {
            next.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            next.setDataSource(checkNotNull(request.file).absolutePath)
            next.setOnPreparedListener {
                if (!isCurrent(request) || player !== next) return@setOnPreparedListener
                try {
                    next.playbackParams = PlaybackParams().setSpeed(request.rate).setPitch(1f)
                    next.start()
                    val duration = (next.duration / request.rate).toLong()
                    Log.i(TAG, "Started ${role.assetDirectory} id=${request.id} startupMs=${SystemClock.elapsedRealtime() - request.queuedAtMs}")
                    onStatus("")
                    onStarted(request.id, duration)
                    // Read-aloud buttons use normalized audio parts without word highlighting.
                    // Streaming assistant answers still report offsets in their original text.
                    if (request.reportWordRanges) reportRanges(request, duration)
                } catch (error: Exception) {
                    Log.e(TAG, "Could not start speech playback", error)
                    finish(request, false)
                }
            }
            next.setOnCompletionListener { if (isCurrent(request)) finish(request, true) }
            next.setOnErrorListener { _, _, _ -> if (isCurrent(request)) finish(request, false); true }
            next.prepareAsync()
        } catch (error: Exception) {
            Log.e(TAG, "Could not open speech audio", error)
            finish(request, false)
        }
    }

    // Piper does not supply word timestamps. Estimate progress within each audio
    // part using spoken word lengths, preserving offsets in the displayed text.
    private fun reportRanges(request: SpeechRequest, duration: Long) {
        val words = request.wordRanges ?: Regex("\\S+").findAll(request.text).map {
            SpeechWordRange(it.range.first, it.range.last + 1, it.value.length.coerceAtLeast(2))
        }.toList()
        val weight = words.sumOf { it.weight }.coerceAtLeast(1)
        var elapsedWeight = 0
        for (word in words) {
            val delay = duration * elapsedWeight / weight
            main.postDelayed({
                if (isCurrent(request)) onRange(request.id, word.start, word.end)
            }, delay)
            elapsedWeight += word.weight
        }
    }

    private fun isCurrent(request: SpeechRequest) = current === request && active(request.ticket)

    private fun finish(request: SpeechRequest, ok: Boolean) {
        if (!isCurrent(request)) return
        player?.release(); player = null
        disposeFile(request)
        current = null
        onStatus(if (ok) "" else "Voice unavailable. You can still use captions.")
        if (!ok) {
            val dropped = pending.filter { it.ticket == request.ticket && it.id == request.id }
            pending.removeAll(dropped.toSet())
            dropped.forEach(::disposeFile)
        }
        // Slides and read-aloud answers complete only after their final audio part.
        if (!ok || request.finalPart) onEnded(request.id, ok)
        drain()
    }

    private fun clearPlayback() {
        player?.release(); player = null
        current?.let(::disposeFile); current = null
        pending.forEach(::disposeFile); pending.clear()
    }

    private fun disposeFile(request: SpeechRequest) { if (request.temporary) request.file?.delete() }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    private fun voice(language: String): OfflineTts {
        require(language in LANGUAGES)
        if (modelLanguage == language) return checkNotNull(model)
        releaseVoice()
        val key = "${role.assetDirectory}/$language"
        sharedVoices[key]?.let { shared ->
            shared.users++
            model = shared.voice
            modelLanguage = language
            Log.i(TAG, "Reused bundled $key neural voice")
            return shared.voice
        }
        val data = File(app.noBackupFilesDir, "lecture-phonemes-v1/espeak-ng-data")
        val ready = File(data, ".ready")
        if (!ready.isFile) { copyAssets("tts/espeak-ng-data", data); ready.writeText("1") }
        val voicePath = "tts/${role.assetDirectory}/$language"
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(model = "$voicePath/model.onnx", tokens = "$voicePath/tokens.txt",
                    dataDir = data.absolutePath, noiseScale = 0.667f, noiseScaleW = 0.8f),
                numThreads = 2, provider = "cpu"
            ), maxNumSentences = 1, silenceScale = 0.4f
        )
        return OfflineTts(app.assets, config).also {
            model = it; modelLanguage = language
            sharedVoices[key] = SharedVoice(it)
            Log.i(TAG, "Loaded bundled ${role.assetDirectory}/$language neural voice")
        }
    }

    // Only the serialized native worker touches these references. A chat panel
    // closing must not unload a voice still used by the floating assistant.
    private fun releaseVoice() {
        val language = modelLanguage ?: return
        val key = "${role.assetDirectory}/$language"
        sharedVoices[key]?.let { shared ->
            shared.users--
            if (shared.users == 0) {
                sharedVoices.remove(key)
                shared.voice.release()
            }
        }
        model = null
        modelLanguage = null
    }

    private fun copyAssets(path: String, destination: File) {
        val children = app.assets.list(path).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            app.assets.open(path).use { input -> destination.outputStream().use { input.copyTo(it) } }
        } else { destination.mkdirs(); children.forEach { copyAssets("$path/$it", File(destination, it)) } }
    }

    private fun trimCache(keep: File) {
        val files = cache.listFiles().orEmpty().filter { it.extension == "wav" }.sortedBy { it.lastModified() }
        var bytes = files.sumOf { it.length() }
        for (file in files) {
            if (bytes <= MAX_CACHE_BYTES) break
            if (file != keep) { val length = file.length(); if (file.delete()) bytes -= length }
        }
    }

    private class SpeechRequest(val ticket: Long, val id: String, val text: String = "", val language: String = "en",
        val rate: Float = 1f, val naturalize: Boolean = true, val silenceMs: Long? = null,
        val finalPart: Boolean = true, val reportWordRanges: Boolean = true,
        val wordRanges: List<SpeechWordRange>? = null,
        val queuedAtMs: Long = SystemClock.elapsedRealtime(),
        var ready: Boolean = false, var failed: Boolean = false, var file: File? = null, var temporary: Boolean = false)

    // JNI requires this exact typed method; an invokedynamic lambda lacks it.
    @Keep
    private class SynthesisCallback(private val alive: () -> Boolean) : (FloatArray) -> Int {
        override fun invoke(samples: FloatArray): Int = if (alive()) 1 else 0
    }

    companion object {
        private const val TAG = "AXO_OFFLINE_TTS"
        private const val MODEL_VERSION = "piper-2026-10-role-voices-v1"
        private const val MAX_CACHE_BYTES = 64L * 1024 * 1024
        private val LANGUAGES = setOf("en", "sq")
        private class SharedVoice(val voice: OfflineTts, var users: Int = 1)
        private val sharedVoices = mutableMapOf<String, SharedVoice>()
        // The native phonemizer is shared: serialize loading, generating, freeing.
        private val workSequence = AtomicLong()
        private class VoiceWork(val priority: Int, val sequence: Long, val action: () -> Unit) : Runnable {
            override fun run() = action()
        }
        private val worker = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            PriorityBlockingQueue<Runnable>(11, compareBy<Runnable> { (it as VoiceWork).priority }
                .thenBy { (it as VoiceWork).sequence })) { task ->
            Thread(task, "neural-speech").apply { isDaemon = true }
        }
        private fun voiceWork(priority: Int = 0, action: () -> Unit) {
            worker.execute(VoiceWork(priority, workSequence.incrementAndGet(), action))
        }
    }
}
