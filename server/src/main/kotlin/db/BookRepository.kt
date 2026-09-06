package com.example.db

import com.example.DatabaseFactory
import java.sql.Types
import java.util.UUID

data class NewBook(
    val title: String,
    val author: String?,
    val description: String?,
    val category: String,
    val format: String,
    val objectKey: String,
    val coverObjectKey: String?,
    val contentType: String,
    val fileSizeBytes: Long,
    val isDownloadable: Boolean,
    val lectureUnitId: UUID?,
    val courseId: Long?
)

data class StoredBook(
    val id: UUID,
    val title: String,
    val author: String?,
    val description: String?,
    val category: String,
    val format: String,
    val objectKey: String,
    val coverObjectKey: String?,
    val contentType: String,
    val fileSizeBytes: Long,
    val isDownloadable: Boolean
)

object BookRepository {
    fun findAll(): List<StoredBook> = DatabaseFactory.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT book_id, title, author, description, category::text AS category,
                   format::text AS format, storage_object_key, cover_object_key, content_type,
                   file_size_bytes, is_downloadable
            FROM books WHERE is_active = TRUE ORDER BY title
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.toStoredBook()) }
            }
        }
    }

    fun findById(id: UUID): StoredBook? = DatabaseFactory.withConnection { connection ->
        connection.prepareStatement(
            """
            SELECT book_id, title, author, description, category::text AS category,
                   format::text AS format, storage_object_key, cover_object_key, content_type,
                   file_size_bytes, is_downloadable
            FROM books WHERE book_id = ? AND is_active = TRUE
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, id)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toStoredBook() else null }
        }
    }

    fun create(book: NewBook): StoredBook = DatabaseFactory.withConnection { connection ->
        connection.prepareStatement(
            """
            INSERT INTO books (
                title, author, description, category, format, storage_object_key,
                cover_object_key, content_type, file_size_bytes, is_downloadable,
                lecture_unit_id, course_id
            ) VALUES (?, ?, ?, ?::book_category, ?::book_format, ?, ?, ?, ?, ?, ?, ?)
            RETURNING book_id, title, author, description, category::text AS category,
                      format::text AS format, storage_object_key, content_type,
                      file_size_bytes, is_downloadable
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, book.title)
            statement.setString(2, book.author)
            statement.setString(3, book.description)
            statement.setString(4, book.category)
            statement.setString(5, book.format)
            statement.setString(6, book.objectKey)
            statement.setString(7, book.coverObjectKey)
            statement.setString(8, book.contentType)
            statement.setLong(9, book.fileSizeBytes)
            statement.setBoolean(10, book.isDownloadable)
            if (book.lectureUnitId == null) statement.setNull(11, Types.OTHER) else statement.setObject(11, book.lectureUnitId)
            if (book.courseId == null) statement.setNull(12, Types.BIGINT) else statement.setLong(12, book.courseId)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "Book metadata was not created." }
                rows.toStoredBook()
            }
        }
    }

    fun updateCover(id: UUID, coverObjectKey: String): StoredBook? = DatabaseFactory.withConnection { connection ->
        connection.prepareStatement(
            "UPDATE books SET cover_object_key = ?, updated_at = CURRENT_TIMESTAMP WHERE book_id = ?"
        ).use { statement ->
            statement.setString(1, coverObjectKey)
            statement.setObject(2, id)
            if (statement.executeUpdate() == 0) null else findById(id)
        }
    }

    private fun java.sql.ResultSet.toStoredBook() = StoredBook(
        id = getObject("book_id", UUID::class.java),
        title = getString("title"),
        author = getString("author"),
        description = getString("description"),
        category = getString("category"),
        format = getString("format"),
        objectKey = getString("storage_object_key"),
        coverObjectKey = getString("cover_object_key"),
        contentType = getString("content_type"),
        fileSizeBytes = getLong("file_size_bytes"),
        isDownloadable = getBoolean("is_downloadable")
    )
}
