package com.example.db

import com.example.CourseResponse
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object CourseRepository {
    fun findAll(): List<CourseResponse> = transaction {
        CoursesTable
            .selectAll()
            .map { row ->
                CourseResponse(
                    id = row[CoursesTable.id],
                    title = row[CoursesTable.title],
                    description = row[CoursesTable.description]
                )
            }
    }

    fun create(title: String, description: String?): CourseResponse = transaction {
        val statement = CoursesTable.insert {
            it[CoursesTable.title] = title
            it[CoursesTable.description] = description
        }

        CourseResponse(
            id = statement[CoursesTable.id],
            title = title,
            description = description
        )
    }
}
