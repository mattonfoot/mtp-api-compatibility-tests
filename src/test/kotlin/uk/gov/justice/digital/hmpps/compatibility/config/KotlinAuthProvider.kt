package uk.gov.justice.digital.hmpps.compatibility.config

/**
 * Provides authentication for the Kotlin API using pre-seeded database tokens.
 *
 * The Kotlin API now validates opaque tokens against the oauth2_provider_accesstoken
 * table (Django-compatible). Test tokens are seeded by V3__Seed_Test_Auth_Data.sql.
 */
class KotlinAuthProvider : AuthProvider {

    private val token = System.getProperty("kotlin.auth.token", "test-token-admin")

    override fun obtainToken(): String = token
}
