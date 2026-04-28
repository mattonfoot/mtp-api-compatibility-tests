package uk.gov.justice.digital.hmpps.compatibility

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
        seedTokensIfNeeded()
    }

    /**
     * Seeds pre-created access tokens for testing.
     * For Python: tokens must include token_checksum (SHA256).
     * For Kotlin: tokens are seeded by V3 flyway migration.
     */
    private fun seedTokensIfNeeded() {
        if (TestConfig.apiTarget == ApiTarget.PYTHON) {
            // Seed tokens for different user roles using existing load_test_data users
            // admin(1)->cashbook(1), bank-admin(10)->bank-admin(3), security-fiu-0(7)->noms-ops(2), test-prison-1(2)->cashbook(1)
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
    }

    /** Get a prison NOMIS ID that exists in both Python and Kotlin test data. */
    protected fun existingPrisonId(): String = "IXB"  // Seeded in both Python load_test_data and Kotlin V3
}
