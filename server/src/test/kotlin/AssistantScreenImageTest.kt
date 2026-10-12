package com.example

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.*

class AssistantScreenImageTest {
    private fun image(width: Int = 32, height: Int = 32): AssistantScreenImage {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        return AssistantScreenImage("image/png", Base64.getEncoder().encodeToString(bytes))
    }

    @Test fun `only the current question carries the current screen and all text history remains intact`() {
        val history = (1..50).flatMap { listOf(AssistantHistoryMessage("user", "Question $it"), AssistantHistoryMessage("assistant", "Answer $it")) }
        val screenshot = image()
        assertNull(assistantScreenImageError(screenshot))
        for (stream in listOf(true, false)) {
            val messages = LmStudioClient.requestBody("Help me solve this.", history, "en", stream, screenshot)["messages"]!!.jsonArray
            assertEquals(history.map { it.content }, messages.drop(1).dropLast(1).map { it.jsonObject["content"]!!.jsonPrimitive.content })
            val content = messages.last().jsonObject["content"]!!.jsonArray
            assertEquals("Help me solve this.", content[0].jsonObject["text"]!!.jsonPrimitive.content)
            assertEquals(screenshot.dataUrl(), content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
            assertEquals(2, content.size)
        }
    }

    @Test fun `invalid oversized and disguised images are rejected`() {
        assertNull(assistantScreenImageError(null))
        assertNotNull(assistantScreenImageError(AssistantScreenImage("image/jpeg", "not base64!")))
        assertNotNull(assistantScreenImageError(AssistantScreenImage("image/svg+xml", "AAAA")))
        assertNotNull(assistantScreenImageError(image().copy(mimeType = "image/jpeg")))
        assertNotNull(assistantScreenImageError(AssistantScreenImage("image/png", "a".repeat(2_000_001))))
        assertNotNull(assistantScreenImageError(image(2049, 1)))
    }
}
