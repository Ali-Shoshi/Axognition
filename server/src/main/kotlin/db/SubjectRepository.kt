package com.example.db

import com.example.DatabaseFactory
import kotlinx.serialization.Serializable

@Serializable
data class MandatorySubjectResponse(
    val id: String,
    val nameEnglish: String,
    val nameAlbanian: String?,
    val nameSerbian: String?,
    val grade: Int
)

object SubjectRepository {
    fun forGrade(grade: Int): List<MandatorySubjectResponse> = DatabaseFactory.withConnection { connection ->
        connection.prepareStatement("""
            SELECT s.mandatory_subject_id, s.name_english, s.name_albanian, s.name_serbian
            FROM mandatory_subjects s
            JOIN mandatory_subject_grade_levels g USING (mandatory_subject_id)
            WHERE g.grade_level = ? AND s.is_active = TRUE
            ORDER BY s.name_english
        """.trimIndent()).use { statement ->
            statement.setInt(1, grade)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(MandatorySubjectResponse(
                        rows.getString("mandatory_subject_id"), rows.getString("name_english"),
                        rows.getString("name_albanian"), rows.getString("name_serbian"), grade
                    ))
                }
            }
        }
    }
}
