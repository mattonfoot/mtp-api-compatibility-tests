package uk.gov.justice.digital.hmpps.compatibility.support

import io.restassured.RestAssured
import io.restassured.http.ContentType
import io.restassured.specification.RequestSpecification
import uk.gov.justice.digital.hmpps.compatibility.config.ApiTarget
import uk.gov.justice.digital.hmpps.compatibility.config.AuthProvider
import uk.gov.justice.digital.hmpps.compatibility.config.KotlinAuthProvider
import uk.gov.justice.digital.hmpps.compatibility.config.PythonAuthProvider
import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig

object ApiClient {

    private val authProvider: AuthProvider = when (TestConfig.apiTarget) {
        ApiTarget.PYTHON -> PythonAuthProvider()
        ApiTarget.KOTLIN -> KotlinAuthProvider()
    }

    private var cachedToken: String? = null

    fun token(): String {
        if (cachedToken == null) {
            cachedToken = authProvider.obtainToken()
        }
        return cachedToken!!
    }

    fun resetToken() {
        cachedToken = null
    }

    /** Returns a RestAssured request spec with the default auth token. */
    fun authenticated(): RequestSpecification = authenticatedAs(token())

    /** Returns a RestAssured request spec with a specific token (for role-specific tests). */
    fun authenticatedAs(bearerToken: String): RequestSpecification =
        RestAssured.given()
            .baseUri(TestConfig.apiBaseUrl)
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
            .header("Authorization", "Bearer $bearerToken")

    /** Returns a RestAssured request spec WITHOUT auth (for testing 401 responses). */
    fun unauthenticated(): RequestSpecification =
        RestAssured.given()
            .baseUri(TestConfig.apiBaseUrl)
            .contentType(ContentType.JSON)
            .accept(ContentType.JSON)
}
