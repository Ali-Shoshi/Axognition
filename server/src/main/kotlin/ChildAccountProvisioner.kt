package com.example

import java.sql.Connection

/**
 * Local development/admin command. It deliberately accepts input only through
 * environment variables so credentials never need to be put in source code.
 */
fun main() {
    val firstName = requiredProvisioningSetting("CHILD_FIRST_NAME")
    val lastName = requiredProvisioningSetting("CHILD_LAST_NAME")
    val username = requiredProvisioningSetting("CHILD_USERNAME")
    val password = requiredProvisioningSetting("CHILD_PASSWORD")
    val grade = requiredProvisioningSetting("CHILD_GRADE").toIntOrNull()
        ?.takeIf { it in 1..9 }
        ?: error("CHILD_GRADE must be a number from 1 to 9.")
    val dateOfBirth = requiredProvisioningSetting("CHILD_DATE_OF_BIRTH")
    val gender = requiredProvisioningSetting("CHILD_GENDER").uppercase()
        .takeIf { it == "MALE" || it == "FEMALE" }
        ?: error("CHILD_GENDER must be MALE or FEMALE.")

    require(username.length in 3..50) { "CHILD_USERNAME must be 3 to 50 characters." }
    require(password.isNotBlank()) { "CHILD_PASSWORD must not be blank." }

    DatabaseFactory.connect()
    try {
        DatabaseFactory.withConnection { connection ->
            connection.autoCommit = false
            try {
                requireUsernameAvailable(connection, username)
                val childId = insertChild(
                    connection = connection,
                    firstName = firstName,
                    lastName = lastName,
                    grade = grade,
                    dateOfBirth = dateOfBirth,
                    gender = gender
                )
                insertCredential(connection, childId, username, PasswordHasher.hash(password))
                connection.commit()
                println("Child account created for $firstName $lastName (username: $username).")
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    } finally {
        DatabaseFactory.close()
    }
}

private fun requiredProvisioningSetting(name: String): String =
    System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("$name is missing. Set it before running createChildAccount.")

private fun requireUsernameAvailable(connection: Connection, username: String) {
    connection.prepareStatement(
        "SELECT 1 FROM child_credentials WHERE LOWER(username) = LOWER(?)"
    ).use { statement ->
        statement.setString(1, username)
        statement.executeQuery().use { result ->
            require(!result.next()) { "That username is already in use." }
        }
    }
}

private fun insertChild(
    connection: Connection,
    firstName: String,
    lastName: String,
    grade: Int,
    dateOfBirth: String,
    gender: String
): String = connection.prepareStatement(
    """
    INSERT INTO children (first_name, last_name, preferred_name, date_of_birth, gender, grade_level, enrollment_date)
    VALUES (?, ?, ?, ?::date, ?::gender_type, ?, CURRENT_DATE)
    RETURNING child_id
    """.trimIndent()
).use { statement ->
    statement.setString(1, firstName)
    statement.setString(2, lastName)
    statement.setString(3, firstName)
    statement.setString(4, dateOfBirth)
    statement.setString(5, gender)
    statement.setInt(6, grade)
    statement.executeQuery().use { result ->
        check(result.next()) { "Could not create the child profile." }
        result.getString("child_id")
    }
}

private fun insertCredential(connection: Connection, childId: String, username: String, passwordHash: String) {
    connection.prepareStatement(
        "INSERT INTO child_credentials (child_id, username, password_hash) VALUES (?::uuid, ?, ?)"
    ).use { statement ->
        statement.setString(1, childId)
        statement.setString(2, username)
        statement.setString(3, passwordHash)
        statement.executeUpdate()
    }
}
