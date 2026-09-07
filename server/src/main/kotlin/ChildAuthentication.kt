package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import de.mkammerer.argon2.Argon2Factory
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import java.time.Instant
import java.util.Date

const val ChildAuthProvider = "child-auth"

object PasswordHasher {
    // OWASP's baseline Argon2id configuration: 19 MiB, 2 iterations, 1 lane.
    private const val MEMORY_KIB = 19 * 1024
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1

    fun hash(password: String): String {
        val characters = password.toCharArray()
        return try {
            Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
                .hash(ITERATIONS, MEMORY_KIB, PARALLELISM, characters)
        } finally {
            characters.fill('\u0000')
        }
    }

    fun matches(password: String, encodedHash: String): Boolean {
        val characters = password.toCharArray()
        return try {
            Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)
                .verify(encodedHash, characters)
        } finally {
            characters.fill('\u0000')
        }
    }
}

object ChildJwt {
    private val issuer: String get() = settingOrDefault("JWT_ISSUER", "axognition-server")
    private val audience: String get() = settingOrDefault("JWT_AUDIENCE", "axognition-tablet")
    private val algorithm: Algorithm
        get() {
            val secret = requiredSetting("JWT_SECRET")
            require(secret.length >= 32) { "JWT_SECRET must be at least 32 characters long." }
            return Algorithm.HMAC256(secret)
        }

    fun createToken(childId: String): String = JWT.create()
        .withIssuer(issuer)
        .withAudience(audience)
        .withClaim("childId", childId)
        .withExpiresAt(Date.from(Instant.now().plusSeconds(60L * 60 * 24 * 30)))
        .sign(algorithm)

    fun verifier(): JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()
}

fun Application.configureChildAuthentication() {
    install(Authentication) {
        jwt(ChildAuthProvider) {
            realm = "Axognition"
            verifier(ChildJwt.verifier())
            validate { credential ->
                credential.payload.getClaim("childId").asString()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { JWTPrincipal(credential.payload) }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "A valid child session is required."))
            }
        }
    }
}

fun childIdFrom(principal: JWTPrincipal): String? = principal.payload.getClaim("childId").asString()
