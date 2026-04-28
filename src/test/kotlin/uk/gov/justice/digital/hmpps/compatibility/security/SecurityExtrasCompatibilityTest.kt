package uk.gov.justice.digital.hmpps.compatibility.security

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("security-extras")
@DisplayName("Security Extras API Compatibility")
class SecurityExtrasCompatibilityTest : CompatibilityTestBase() {

    private fun securityAuth() = ApiClient.authenticatedAs("test-token-security")
    private fun fiuAuth() = ApiClient.authenticatedAs("test-token-fiu")

    @Nested
    @DisplayName("GET /security/checks/auto-accept/")
    inner class AutoAcceptRules {

        @Test
        @DisplayName("returns paginated list of auto-accept rules")
        fun `list auto-accept rules`() {
            fiuAuth()
                .get("/security/checks/auto-accept/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("response has pagination envelope")
        fun `response has count and results`() {
            val json = fiuAuth().get("/security/checks/auto-accept/").jsonPath()
            assertThat(json.getMap<String, Any>("")).containsKeys("count", "results")
        }
    }

    @Nested
    @DisplayName("GET /security/monitored-email-addresses/")
    inner class MonitoredEmailAddresses {

        @Test
        @DisplayName("returns paginated list of monitored email addresses")
        fun `list monitored emails`() {
            fiuAuth()
                .get("/security/monitored-email-addresses/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }
    }

    @Nested
    @DisplayName("GET /monitored/")
    inner class MonitoredCount {

        @Test
        @DisplayName("returns count of monitored profiles")
        fun `monitored count`() {
            val json = securityAuth().get("/monitored/").jsonPath()
            val body = json.getMap<String, Any>("")
            assertThat(body).containsKey("count")
        }
    }

    @Nested
    @DisplayName("Authentication for security extras")
    inner class Auth {

        @Test
        @DisplayName("unauthenticated auto-accept returns 401")
        fun `auto-accept without token`() {
            ApiClient.unauthenticated()
                .get("/security/checks/auto-accept/")
                .then()
                .statusCode(401)
        }

        @Test
        @DisplayName("unauthenticated monitored-email returns 401")
        fun `monitored-email without token`() {
            ApiClient.unauthenticated()
                .get("/security/monitored-email-addresses/")
                .then()
                .statusCode(401)
        }
    }
}
