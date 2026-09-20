package com.example

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import java.io.File
import java.util.Properties

private fun loadLocalEnv(): Properties = Properties().apply {
    val envFile = File(".env")
    if (envFile.isFile) {
        envFile.inputStream().use { load(it) }
    }
}

private val localEnv = loadLocalEnv()

fun requiredSetting(name: String): String =
    System.getenv(name) ?: localEnv.getProperty(name)
    ?: error("$name is missing. Add it to .env.")

fun settingOrDefault(name: String, defaultValue: String): String =
    System.getenv(name) ?: localEnv.getProperty(name) ?: defaultValue

object DatabaseFactory {
    private lateinit var dataSource: HikariDataSource

    fun connect(schema: String? = null) {
        val config = HikariConfig().apply {
            jdbcUrl = requiredSetting("DB_URL")
            username = requiredSetting("DB_USER")
            password = requiredSetting("DB_PASSWORD")
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = settingOrDefault("DB_POOL_SIZE", "10").toInt().coerceIn(1, 100)
            connectionTimeout = 5000
            if (schema != null) this.schema = schema
        }

        dataSource = HikariDataSource(config)

        Flyway.configure()
            .dataSource(dataSource)
            .apply { if (schema != null) schemas(schema) }
            .locations("classpath:db/migration")
            .load()
            .migrate()

        Database.connect(dataSource)
    }

    fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    fun <T> withConnection(block: (java.sql.Connection) -> T): T {
        check(::dataSource.isInitialized) { "Database is not connected." }
        return dataSource.connection.use(block)
    }
}

fun Application.configureDatabase() {
    DatabaseFactory.connect()

    monitor.subscribe(ApplicationStopped) {
        DatabaseFactory.close()
    }
}
