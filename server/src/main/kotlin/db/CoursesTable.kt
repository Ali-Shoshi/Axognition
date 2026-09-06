package com.example.db

import org.jetbrains.exposed.v1.core.Table

object CoursesTable : Table("courses") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 200)
    val description = text("description").nullable()

    override val primaryKey = PrimaryKey(id)
}
