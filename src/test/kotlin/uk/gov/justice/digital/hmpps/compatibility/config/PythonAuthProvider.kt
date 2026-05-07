package uk.gov.justice.digital.hmpps.compatibility.config

/**
 * Provides authentication for the Python Django API using a pre-seeded
 * database token. The token must be inserted into oauth2_provider_accesstoken
 * before running tests (e.g. via load_test_data + manual seed).
 */
class PythonAuthProvider : AuthProvider {

    private val token = System.getProperty("python.auth.token", "test-token-admin")

    override fun obtainToken(): String = token
}
