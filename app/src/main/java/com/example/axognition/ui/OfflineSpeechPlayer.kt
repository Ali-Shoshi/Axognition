package com.example.axognition.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Handler
import android.os.Looper
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
import java.util.concurrent.Executors
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
        worker.execute {
            if (closed) return@execute
            runCatching { voice(language) }
                .onSuccess { status("") }
                .onFailure {
                    Log.e(TAG, "Could not prepare offline speech", it)
                    status("Voice unavailable. You can still use captions.")
                }
        }
    }

    fun speak(text: String, id: String, language: String, rate: Float = 1f,
              queueMode: SpeechQueueMode = SpeechQueueMode.REPLACE, naturalize: Boolean = true): Boolean {
        if (closed || text.isBlank() || language !in LANGUAGES) return false
        val ticket = if (queueMode == SpeechQueueMode.REPLACE) generation.incrementAndGet() else generation.get()
        val request = SpeechRequest(ticket, id, text, language, rate.coerceIn(0.5f, 2f), naturalize)
        onMain {
            if (!active(ticket)) return@onMain
            if (queueMode == SpeechQueueMode.REPLACE) clearPlayback()
            pending.add(request)
            if (current == null) onStatus("Preparing narration…")
            // Prefetch queued chat sentences while the preceding sentence plays.
            worker.execute { prepare(request) }
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
        stop()
        worker.execute { model?.release(); model = null; modelLanguage = null }
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
                cache.mkdirs()
                val hash = MessageDigest.getInstance("SHA-256")
                    .digest("$MODEL_VERSION|${role.assetDirectory}|${request.language}|$spoken".toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(Locale.ROOT, it) }
                file = File(cache, "$hash.wav")
            } else {
                temporary.mkdirs()
                file = File(temporary, "${UUID.randomUUID()}.wav")
            }
            if (!file.isFile || file.length() <= 44) {
                val synthesizer = voice(request.language)
                if (!active(request.ticket)) return
                val audio = synthesizer.generateWithCallback(spoken, speed = 0.96f,
                    callback = SynthesisCallback { active(request.ticket) })
                if (!active(request.ticket)) return
                check(audio.samples.isNotEmpty()) { "The neural voice produced no audio" }
                val partial = File(file.parentFile, "${file.name}.partial")
                check(audio.save(partial.absolutePath)) { "Could not save generated speech" }
                check(partial.renameTo(file)) { "Could not finish generated speech" }
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
                    onStatus("")
                    onStarted(request.id, duration)
                    reportRanges(request, duration)
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

    // Piper does not supply word timestamps. Spread original-text word ranges
    // over the audio duration for read-along, preserving offsets used by chat.
    private fun reportRanges(request: SpeechRequest, duration: Long) {
        val words = Regex("\\S+").findAll(request.text).toList()
        val weight = words.sumOf { it.value.length.coerceAtLeast(2) }.coerceAtLeast(1)
        var elapsedWeight = 0
        for (word in words) {
            val delay = duration * elapsedWeight / weight
            main.postDelayed({
                if (isCurrent(request)) onRange(request.id, word.range.first, word.range.last + 1)
            }, delay)
            elapsedWeight += word.value.length.coerceAtLeast(2)
        }
    }

    private fun isCurrent(request: SpeechRequest) = current === request && active(request.ticket)

    private fun finish(request: SpeechRequest, ok: Boolean) {
        if (!isCurrent(request)) return
        player?.release(); player = null
        disposeFile(request)
        current = null
        onStatus(if (ok) "" else "Voice unavailable. You can still use captions.")
        onEnded(request.id, ok)
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
        model?.release(); model = null; modelLanguage = null
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
            Log.i(TAG, "Loaded bundled ${role.assetDirectory}/$language neural voice")
        }
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
        // The native phonemizer is shared: serialize loading, generating, freeing.
        private val worker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "neural-speech").apply { isDaemon = true }
        }
    }
}
