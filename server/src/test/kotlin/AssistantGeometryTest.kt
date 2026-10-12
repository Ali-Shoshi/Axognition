package com.example

import kotlinx.serialization.json.*
import kotlin.test.*

class AssistantGeometryTest {
    private val both = GeometryQuestion(area = true, perimeter = true)
    private fun label(role: String, value: Double, unit: String = "") = VisibleMeasurement(role, value, unit)

    @Test fun `height three and a side four do not invent a base or a right angle`() {
        val reply = assistantGeometryAnswer(VisibleGeometry("triangle", listOf(label("height", 3.0), label("side", 4.0))), both, "sq")
        assertContains(reply, "Mungon baza")
        assertContains(reply, "të tria brinjët")
        assertContains(reply, "baza × 3 / 2")
        assertFalse(reply.contains("= 6") || reply.contains("= 10") || reply.contains("= 14"))
    }

    @Test fun `base and height give area but do not become perimeter sides`() {
        val reply = assistantGeometryAnswer(VisibleGeometry("triangle", listOf(label("base", 6.0, "cm"), label("height", 4.0, "cm"))), both, "en")
        assertContains(reply, "Area = 12 cm²")
        assertContains(reply, "needs all three side lengths")
        assertFalse(reply.contains("Perimeter ="))
    }

    @Test fun `triangle perimeter uses three sides and Heron works without assuming right angles`() {
        val reply = assistantGeometryAnswer(VisibleGeometry("triangle", listOf(label("side", 3.0), label("side", 4.0), label("side", 5.0))), both, "en")
        assertContains(reply, "Perimeter = 12")
        assertContains(reply, "Area = 6")
        assertContains(reply, "s = P/2")
        val nonRight = assistantGeometryAnswer(VisibleGeometry("triangle", listOf(label("base", 6.0), label("side", 5.0), label("side", 5.0), label("height", 4.0))), both, "sq")
        assertContains(nonRight, "Perimetri = 16")
        assertContains(nonRight, "Sipërfaqja = 12")
        val repeatedEdges = VisibleGeometryLabels("triangle", 6.0, 4.0, 5.0, 5.0, null, null, "cm").figure()
        assertEquals(2, repeatedEdges.measurements.count { it.role == "side" && it.value == 5.0 })
        val extracted = assistantGeometryAnswer(repeatedEdges, both, "sq")
        assertContains(extracted, "Perimetri = 16 cm")
        assertContains(extracted, "Sipërfaqja = 12 cm²")
    }

    @Test fun `other labeled figures use the correct calculations and units`() {
        val square = assistantGeometryAnswer(VisibleGeometry("square", listOf(label("side", 5.0, "cm"))), both, "sq")
        assertContains(square, "Perimetri = 20 cm")
        assertContains(square, "Sipërfaqja = 25 cm²")
        val rectangle = assistantGeometryAnswer(VisibleGeometry("rectangle", listOf(label("length", 6.0), label("width", 4.0))), both, "en")
        assertContains(rectangle, "Perimeter = 20")
        assertContains(rectangle, "Area = 24")
        val circle = assistantGeometryAnswer(VisibleGeometry("circle", listOf(label("diameter", 6.0))), both, "en")
        assertContains(circle, "Perimeter = ≈ 18.85")
        assertContains(circle, "Area = ≈ 28.274")
    }

    @Test fun `unclear impossible and conflicting measurements ask for clarification`() {
        for (figure in listOf(null, VisibleGeometry("unknown", emptyList()),
            VisibleGeometry("triangle", listOf(label("side", 1.0), label("side", 2.0), label("side", 5.0))),
            VisibleGeometry("circle", listOf(label("radius", 3.0), label("diameter", 8.0))),
            VisibleGeometry("rectangle", listOf(label("length", 6.0, "cm"), label("width", 4.0, "m"))))) {
            assertContains(assistantGeometryAnswer(figure, both, "en"), "can't read")
        }
    }

    @Test fun `only related geometry questions inspect the screen and observation requests cannot copy old chat`() {
        assertEquals(both, assistantGeometryQuestion("më trego sa është primetri dhe sipërfaqja e trekëndëshit dhe si mund ta llogaritja atë"))
        assertEquals(both, assistantGeometryQuestion("What is the perimeter and area of this figure?"))
        assertNotNull(assistantGeometryQuestion("What about the area?"))
        for (question in listOf("What is the capital of France?", "What is my favorite color?", "What is the area code for this city?", "What is the area of France?", "What is the formula for the area of a circle?")) {
            assertNull(assistantGeometryQuestion(question), question)
        }
        val messages = LmStudioClient.visibleGeometryRequestBody(AssistantScreenImage("image/png", "test"))["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        assertContains(messages[0].jsonObject["content"]!!.jsonPrimitive.content, "Do not calculate, infer missing measurements")
    }
}
