package com.example

import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO

@Serializable
data class AssistantScreenImage(val mimeType: String, val base64: String) {
    fun dataUrl(): String = "data:$mimeType;base64,$base64"
}

/** Bound current-screen payloads and reject malformed images before contacting the model. */
internal fun assistantScreenImageError(image: AssistantScreenImage?): String? {
    if (image == null) return null
    if (image.mimeType !in setOf("image/jpeg", "image/png")) return "Unsupported screen image format."
    if (image.base64.length !in 1..2_000_000) return "Screen image is empty or too large."
    return runCatching {
        val bytes = Base64.getDecoder().decode(image.base64)
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull()
                ?: return "Invalid screen image."
            try {
                reader.input = input
                val format = reader.formatName.lowercase()
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (format !in setOf("jpeg", "png") ||
                    (image.mimeType == "image/png") != (format == "png")) "Invalid screen image format."
                else if (width !in 1..2048 || height !in 1..2048 || width.toLong() * height > 3_000_000) "Screen image dimensions are too large."
                else null
            } finally { reader.dispose() }
        }
    }.getOrElse { "Invalid screen image." }
}
