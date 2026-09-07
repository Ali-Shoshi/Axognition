package com.example.db

import com.example.DatabaseFactory
import java.time.OffsetDateTime
import java.util.UUID

data class ChildAccount(
    val childId: UUID,
    val username: String,
    val passwordHash: String,
    val displayName: String,
    val grade: Int?
)

data class ChildProfile(
    val childId: UUID,
    val displayName: String,
    val grade: Int?
)

object ChildAccountRepository {
    fun findByUsername(username: String): ChildAccount? = DatabaseFactory.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT c.child_id, a.username, a.password_hash,
                   COALESCE(NULLIF(c.preferred_name, ''), c.first_name) AS display_name,
                   c.grade_level
            FROM child_credentials a
            JOIN children c ON c.child_id = a.child_id
            WHERE LOWER(a.username) = LOWER(?) AND a.is_active = TRUE AND c.is_active = TRUE
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, username)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else ChildAccount(
                    childId = rows.getObject("child_id", UUID::class.java),
                    username = rows.getString("username"),
                    passwordHash = rows.getString("password_hash"),
                    displayName = rows.getString("display_name"),
                    grade = (rows.getObject("grade_level") as? Number)?.toInt()
                )
            }
        }
    }

    fun findProfile(childId: UUID): ChildProfile? = DatabaseFactory.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT c.child_id, COALESCE(NULLIF(c.preferred_name, ''), c.first_name) AS display_name, c.grade_level
            FROM child_credentials a
            JOIN children c ON c.child_id = a.child_id
            WHERE c.child_id = ? AND a.is_active = TRUE AND c.is_active = TRUE
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, childId)
            statement.executeQuery().use { rows ->
                if (!rows.next()) null else ChildProfile(
                    childId = rows.getObject("child_id", UUID::class.java),
                    displayName = rows.getString("display_name"),
                    grade = (rows.getObject("grade_level") as? Number)?.toInt()
                )
            }
        }
    }

    fun recordSuccessfulLogin(childId: UUID) = DatabaseFactory.withConnection { connection ->
        connection.prepareStatement(
            "UPDATE child_credentials SET failed_login_attempts = 0, locked_until = NULL, last_login_at = ?, updated_at = CURRENT_TIMESTAMP WHERE child_id = ?"
        ).use { statement ->
            statement.setObject(1, OffsetDateTime.now())
            statement.setObject(2, childId)
            statement.executeUpdate()
        }
    }
}
