package uk.gov.justice.digital.hmpps.compatibility

import io.restassured.response.Response
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import uk.gov.justice.digital.hmpps.compatibility.config.ApiTarget
import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig
import uk.gov.justice.digital.hmpps.compatibility.support.DatabaseHelper
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class CompatibilityTestBase {

    protected val db = DatabaseHelper()

    @BeforeAll
    fun waitForApi() {
        await atMost Duration.ofSeconds(120) untilAsserted {
            val response = io.restassured.RestAssured.given()
                .baseUri(TestConfig.apiBaseUrl)
                .get(EndpointResolver.healthCheck())
            assert(response.statusCode() in listOf(200, 501)) {
                "API not healthy: ${response.statusCode()}"
            }
        }
        seedTokens()
    }

    /**
     * Seeds pre-created access tokens for testing. Both APIs share the same
     * Django-shaped database, so the same INSERT works against either target.
     * Token checksums match what django-oauth-toolkit's middleware computes
     * (SHA256 of the raw token).
     */
    private fun seedTokens() {
        db.executeSql(
            """
            INSERT INTO oauth2_provider_accesstoken (token, token_checksum, expires, scope, application_id, user_id, created, updated)
            VALUES
              ('test-token-admin', encode(sha256('test-token-admin'::bytea), 'hex'), '2030-12-31 23:59:59+00', 'read write', 1, 1, NOW(), NOW()),
              ('test-token-bank-admin', encode(sha256('test-token-bank-admin'::bytea), 'hex'), '2030-12-31 23:59:59+00', 'read write', 3, 10, NOW(), NOW()),
              ('test-token-security', encode(sha256('test-token-security'::bytea), 'hex'), '2030-12-31 23:59:59+00', 'read write', 2, 8, NOW(), NOW()),
              ('test-token-fiu', encode(sha256('test-token-fiu'::bytea), 'hex'), '2030-12-31 23:59:59+00', 'read write', 2, 7, NOW(), NOW()),
              ('test-token-prison-clerk', encode(sha256('test-token-prison-clerk'::bytea), 'hex'), '2030-12-31 23:59:59+00', 'read write', 1, 2, NOW(), NOW()),
              ('test-token-no-roles', encode(sha256('test-token-no-roles'::bytea), 'hex'), '2030-12-31 23:59:59+00', 'read write', 1, 1, NOW(), NOW()),
              ('test-token-disbursement-admin', encode(sha256('test-token-disbursement-admin'::bytea), 'hex'), '2030-12-31 23:59:59+00', 'read write', 3, 12, NOW(), NOW()),
              ('test-token-send-money', encode(sha256('test-token-send-money'::bytea), 'hex'), '2030-12-31 23:59:59+00', 'read write', 4, 13, NOW(), NOW()),
              ('test-token-prisoner-location-admin', encode(sha256('test-token-prisoner-location-admin'::bytea), 'hex'), '2030-12-31 23:59:59+00', 'read write', 2, 6, NOW(), NOW())
            ON CONFLICT DO NOTHING
            """.trimIndent(),
        )
    }

    /** Get a prison NOMIS ID that exists in both Python and Kotlin test data. */
    protected fun existingPrisonId(): String = "IXB"  // Seeded in both Python load_test_data and Kotlin V3

    /**
     * Compat-test status assertion. Pass the **Python-spec** status code as [expected]
     * — that's the source of truth. The test then enforces that the API under test
     * (whichever target is selected) returns the same code.
     *
     * When the Kotlin port currently diverges from the Python spec, pass that
     * known-divergent code as [kotlinDivergence] *and* explain the bug in [reason].
     * The assertion then:
     *   * against Python: asserts [expected] (so a Python-side change to the
     *     "spec" still trips the suite — the divergence record must be re-validated).
     *   * against Kotlin: asserts [kotlinDivergence] *exactly* — so when the
     *     Kotlin bug is fixed, the test fails loudly and prompts removal of the
     *     divergence record, restoring proper compat.
     *
     * Use this only when the divergence is a known follow-up. For genuine
     * data-dependent ambiguity (e.g. 200-or-404 based on which seeded row a
     * filter picks), seed deterministically instead.
     */
    protected fun assertStatus(
        response: Response,
        expected: Int,
        kotlinDivergence: Int? = null,
        reason: String? = null,
    ) {
        val effective = when {
            TestConfig.apiTarget == ApiTarget.KOTLIN && kotlinDivergence != null -> kotlinDivergence
            else -> expected
        }
        assertThat(response.statusCode())
            .withFailMessage(
                "Compat status mismatch: target=%s, expected=%d%s, got=%d, body=%s",
                TestConfig.apiTarget,
                effective,
                reason?.let { " (divergence reason: $it)" } ?: "",
                response.statusCode(),
                response.body().asString(),
            )
            .isEqualTo(effective)
    }
}
