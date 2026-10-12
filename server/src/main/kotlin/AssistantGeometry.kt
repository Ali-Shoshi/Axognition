package com.example

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sqrt

@Serializable internal data class VisibleMeasurement(val role: String, val value: Double, val unit: String)
@Serializable internal data class VisibleGeometry(val shape: String, val measurements: List<VisibleMeasurement>)
@Serializable internal data class VisibleGeometryLabels(
    val shape: String, val base: Double?, val height: Double?, val leftSide: Double?, val rightSide: Double?,
    val radius: Double?, val diameter: Double?, val unit: String
) {
    fun figure(): VisibleGeometry = VisibleGeometry(shape, buildList {
        base?.let { add(VisibleMeasurement("base", it, unit)) }
        height?.let { add(VisibleMeasurement("height", it, unit)) }
        // Each boundary keeps its own value, including equal but separately labelled sides.
        leftSide?.let { add(VisibleMeasurement(if (shape == "rectangle") "width" else "side", it, unit)) }
        rightSide?.let { add(VisibleMeasurement(if (shape == "rectangle") "width" else "side", it, unit)) }
        radius?.let { add(VisibleMeasurement("radius", it, unit)) }
        diameter?.let { add(VisibleMeasurement("diameter", it, unit)) }
    })
}
internal data class GeometryQuestion(val area: Boolean, val perimeter: Boolean)

/** Only calculations about the current figure; unrelated questions must ignore the screen. */
internal fun assistantGeometryQuestion(question: String): GeometryQuestion? {
    val text = question.lowercase(Locale.ROOT).replace('ë', 'e').replace('ç', 'c')
    if (Regex("""\b(?:area code|france|frances|shqiperise|favorite|colour|color|ngjyra)\b""").containsMatchIn(text)) return null
    // Explicit dimensions in the question belong to that described problem, not an unrelated screen.
    if (Regex("""\d""").containsMatchIn(text)) return null
    val area = Regex("""\barea\b|siperfaq|surface area""").containsMatchIn(text)
    val perimeter = Regex("""perimet|primetr|per metrin|circumference|boundary length""").containsMatchIn(text)
    if (!area && !perimeter) return null
    val currentFigure = Regex("""\b(?:this|that|my|shown|image|screen|figure|here|ky|kjo|kete|ketij|kesaj|figura|figures|trekendesh\w*|triangle|square|rectangle|circle)\b""")
    val bareFollowUp = Regex("""^(?:what about|and|po|sa eshte|sa jane|what is|what's|calculate|find|llogarit|gjej)?\s*(?:the |its |e |saj |tij )?(?:area|perimeter|circumference|siperfaqja|perimetri|primetri)[\s?!.,]*$""")
    if (!currentFigure.containsMatchIn(text) && !bareFollowUp.matches(text)) return null
    if (Regex("""\b(?:a|an|nje)\s+(?:triangle|square|rectangle|circle|trekendesh\w*)\b""").containsMatchIn(text) &&
        !Regex("""\b(?:this|that|my|shown|image|screen|ky|kjo|kete|ketij|kesaj)\b""").containsMatchIn(text)) return null
    // A purely general formula question does not require interpreting a potentially unrelated image.
    if (text.contains("formula") && !Regex("""\b(?:this|that|my|shown|image|screen|ky|kjo|kete|ketij|kesaj)\b""").containsMatchIn(text)) return null
    return GeometryQuestion(area, perimeter)
}

private fun geometryNumber(value: Double): String = BigDecimal.valueOf(value)
    .setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Compute only from explicit labels. A rough drawing never proves a right angle or equal sides. */
internal fun assistantGeometryAnswer(figure: VisibleGeometry?, asked: GeometryQuestion, language: String): String {
    val sq = language == "sq"
    val unreadable = if (sq) "Nuk i dalloj qartë masat e figurës. Shkruaji masat ose dërgo një pamje më të qartë."
        else "I can't read the figure's measurements clearly. Please write them or show a clearer view."
    if (figure == null || figure.shape !in setOf("triangle", "square", "rectangle", "circle") ||
        figure.measurements.size > 8 || figure.measurements.any { !it.value.isFinite() || it.value <= 0 || it.value > 1e9 ||
            it.role !in setOf("base", "height", "side", "length", "width", "radius", "diameter") }) return unreadable
    val labels = figure.measurements
    val units = labels.map { it.unit.trim().lowercase(Locale.ROOT) }.filter { it.isNotBlank() }.distinct()
    if (units.size > 1 || units.any { it !in setOf("mm", "cm", "m", "km", "in", "ft", "yd") }) return unreadable
    val unit = units.singleOrNull().orEmpty()
    fun one(vararg roles: String): Double? = labels.filter { it.role in roles }.map { it.value }.distinct().singleOrNull()
    var area: Double? = null
    var perimeter: Double? = null
    var areaFormula = ""
    var perimeterFormula = ""
    var missingArea = ""
    var missingPerimeter = ""
    when (figure.shape) {
        "triangle" -> {
            val base = one("base")
            val height = one("height")
            val sides = labels.filter { it.role == "base" || it.role == "side" }.map { it.value }
            if (sides.size > 3 || (sides.size == 3 && sides.max() >= sides.sum() - sides.max())) return unreadable
            if (height != null && labels.any { it.role == "side" && it.value < height }) return unreadable
            if (sides.size == 3) perimeter = sides.sum()
            if (base != null && height != null) area = base * height / 2
            else if (sides.size == 3) {
                val semiperimeter = sides.sum() / 2
                area = sqrt(semiperimeter * sides.fold(1.0) { product, side -> product * (semiperimeter - side) })
            }
            areaFormula = "S = ${if (sq) "baza" else "base"} × ${height?.let(::geometryNumber) ?: if (sq) "lartësia" else "height"} / 2"
            if (area != null && (base == null || height == null)) areaFormula = "S = √[s(s−a)(s−b)(s−c)], s = P/2"
            perimeterFormula = "P = a + b + c"
            missingArea = if (base == null && height != null) {
                if (sq) "Mungon baza për sipërfaqen" else "The base is missing for the area"
            } else if (base != null && height == null) {
                if (sq) "Mungon lartësia për sipërfaqen" else "The height is missing for the area"
            } else if (sq) "Duhen baza dhe lartësia për sipërfaqen" else "The area needs the base and height"
            missingPerimeter = if (sq) "Perimetri kërkon të tria brinjët" else "The perimeter needs all three side lengths"
        }
        "square" -> {
            val side = one("side", "base", "length", "width")
            if (side != null) { area = side * side; perimeter = 4 * side }
            areaFormula = "S = a²"; perimeterFormula = "P = 4 × a"
            missingArea = if (sq) "Mungon brinja për sipërfaqen" else "The side length is missing for the area"
            missingPerimeter = if (sq) "Mungon brinja për perimetrin" else "The side length is missing for the perimeter"
        }
        "rectangle" -> {
            val length = one("length", "base")
            val width = one("width", "height")
            if (length != null && width != null) { area = length * width; perimeter = 2 * (length + width) }
            areaFormula = "S = a × b"; perimeterFormula = "P = 2 × (a + b)"
            missingArea = if (sq) "Sipërfaqja kërkon gjatësinë dhe gjerësinë" else "The area needs the length and width"
            missingPerimeter = if (sq) "Perimetri kërkon gjatësinë dhe gjerësinë" else "The perimeter needs the length and width"
        }
        "circle" -> {
            val labeledRadius = one("radius")
            val diameter = one("diameter")
            if (labeledRadius != null && diameter != null && kotlin.math.abs(2 * labeledRadius - diameter) > 0.001) return unreadable
            val radius = labeledRadius ?: diameter?.div(2)
            if (radius != null) { area = PI * radius * radius; perimeter = 2 * PI * radius }
            areaFormula = "S = π × r²"; perimeterFormula = "P = 2 × π × r"
            missingArea = if (sq) "Mungon rrezja ose diametri për sipërfaqen" else "The radius or diameter is missing for the area"
            missingPerimeter = if (sq) "Mungon rrezja ose diametri për perimetrin" else "The radius or diameter is missing for the circumference"
        }
    }
    fun result(value: Double, squared: Boolean) =
        (if (figure.shape == "circle") "≈ " else "") + geometryNumber(value) +
            (if (unit.isEmpty()) "" else " $unit${if (squared) "²" else ""}")
    val answers = mutableListOf<String>()
    if (asked.perimeter) answers += if (perimeter != null) {
        "${if (sq) "Perimetri" else "Perimeter"} = ${result(perimeter, false)} ($perimeterFormula)"
    } else "$missingPerimeter ($perimeterFormula)"
    if (asked.area) answers += if (area != null) {
        "${if (sq) "Sipërfaqja" else "Area"} = ${result(area, true)} ($areaFormula)"
    } else "$missingArea ($areaFormula)"
    return answers.joinToString(". ", postfix = ".")
}
