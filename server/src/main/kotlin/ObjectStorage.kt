package com.example

import io.ktor.server.application.Application
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.StatObjectArgs
import io.minio.http.Method
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class StoredObjectMetadata(
    val sizeBytes: Long,
    val contentType: String
)

/** Connects Ktor to private MinIO. The public client makes tablet-safe signed URLs. */
object ObjectStorage {
    private lateinit var privateClient: MinioClient
    private lateinit var publicUrlClient: MinioClient
    private lateinit var booksBucket: String

    fun connect() {
        booksBucket = requiredSetting("MINIO_BOOKS_BUCKET")
        val accessKey = requiredSetting("MINIO_ACCESS_KEY")
        val secretKey = requiredSetting("MINIO_SECRET_KEY")

        privateClient = MinioClient.builder()
            .endpoint(requiredSetting("MINIO_ENDPOINT"))
            .credentials(accessKey, secretKey)
            .build()
        publicUrlClient = MinioClient.builder()
            .endpoint(requiredSetting("MINIO_PUBLIC_ENDPOINT"))
            .credentials(accessKey, secretKey)
            .build()
    }

    fun statBook(objectKey: String): StoredObjectMetadata {
        check(::privateClient.isInitialized) { "MinIO is not configured." }
        val result = privateClient.statObject(
            StatObjectArgs.builder().bucket(booksBucket).`object`(objectKey).build()
        )
        return StoredObjectMetadata(result.size(), result.contentType() ?: "application/octet-stream")
    }

    fun createBookDownloadUrl(objectKey: String): String {
        check(::publicUrlClient.isInitialized) { "MinIO is not configured." }
        return publicUrlClient.getPresignedObjectUrl(
            GetPresignedObjectUrlArgs.builder()
                .method(Method.GET)
                .bucket(booksBucket)
                .`object`(objectKey)
                .expiry(10, TimeUnit.MINUTES)
                .build()
        )
    }

    fun uploadBookCover(objectKey: String, inputStream: InputStream, contentType: String) {
        check(::privateClient.isInitialized) { "MinIO is not configured." }
        privateClient.putObject(
            PutObjectArgs.builder()
                .bucket(booksBucket)
                .`object`(objectKey)
                .stream(inputStream, -1, 5L * 1024 * 1024)
                .contentType(contentType)
                .build()
        )
    }
}

fun Application.configureObjectStorage() {
    ObjectStorage.connect()
}
