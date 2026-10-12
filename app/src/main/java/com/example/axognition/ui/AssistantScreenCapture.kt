package com.example.axognition.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.WindowManager
import com.example.axognition.data.AssistantApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Captures only our app window, including hardware-rendered lesson WebViews. No files are saved. */
internal class AssistantScreenCapture(private val activity: Activity) {
    private var assistantBounds: Rect? = null
    fun setAssistantBounds(bounds: Rect?) { assistantBounds = bounds?.let(::Rect) }
    suspend fun capture(): AssistantApi.ScreenImage? = try {
        captureWindow()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        android.util.Log.w("AXO_SCREEN", "Current screen could not be captured", error)
        null
    }

    private suspend fun captureWindow(): AssistantApi.ScreenImage? = withContext(Dispatchers.Main.immediate) {
        val window = activity.window
        val view = window.decorView
        if (activity.isFinishing || activity.isDestroyed || !view.isAttachedToWindow ||
            view.width <= 0 || view.height <= 0 ||
            window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0) return@withContext null
        val bitmap = withTimeoutOrNull(700) {
            if (Build.VERSION.SDK_INT >= 26) {
                suspendCancellableCoroutine<Bitmap?> { continuation ->
                    val target = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    try {
                        PixelCopy.request(window, target, { result ->
                            if (!continuation.isActive) target.recycle()
                            else if (result == PixelCopy.SUCCESS) continuation.resume(target) { _, bitmap, _ -> bitmap?.recycle() }
                            else { target.recycle(); continuation.resume(null) }
                        }, Handler(Looper.getMainLooper()))
                    } catch (_: IllegalArgumentException) {
                        target.recycle()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            } else {
                Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
            }
        } ?: return@withContext null
        try {
            // Old assistant claims are not observations of the exercise. Mask only the overlay;
            // never reconstruct the learning content hidden behind it.
            assistantBounds?.let { bounds ->
                Canvas(bitmap).drawRect(bounds, Paint().apply { color = Color.BLACK })
            }
            withContext(Dispatchers.Default) {
                val scale = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
                val resized = if (scale < 1f) Bitmap.createScaledBitmap(bitmap,
                    (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                    (bitmap.height * scale).roundToInt().coerceAtLeast(1), true) else bitmap
                try {
                    val bytes = ByteArrayOutputStream().use { output ->
                        check(resized.compress(Bitmap.CompressFormat.JPEG, 80, output))
                        output.toByteArray()
                    }
                    android.util.Log.i("AXO_SCREEN", "Captured ${resized.width}x${resized.height} bytes=${bytes.size}")
                    AssistantApi.ScreenImage("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
                } finally { if (resized !== bitmap) resized.recycle() }
            }
        } finally { bitmap.recycle() }
    }

    companion object {
        fun from(context: Context): AssistantScreenCapture? {
            var current = context
            while (current is ContextWrapper) {
                if (current is Activity) return AssistantScreenCapture(current)
                val base = current.baseContext
                if (base === current) break
                current = base
            }
            return null
        }
    }
}
