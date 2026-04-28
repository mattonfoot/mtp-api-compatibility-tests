package uk.gov.justice.digital.hmpps.compatibility.security

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver

@Tag("security")
@DisplayName("Security API Compatibility")
class SecurityCheckCompatibilityTest : CompatibilityTestBase() {

    /** Security profile endpoints require Security group */
    private fun securityAuth() = ApiClient.authenticatedAs("test-token-security")

    /** Security check endpoints require FIU group in Django */
    private fun fiuAuth() = ApiClient.authenticatedAs("test-token-fiu")

    @Nested
    @DisplayName("GET /security/checks/")
    inner class ListChecks {

        @Test
        @DisplayName("returns paginated list of security checks")
        fun `list checks returns paginated response`() {
            fiuAuth()
                .get(EndpointResolver.securityChecks())
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("response has pagination envelope")
        fun `response has count and results`() {
            val json = fiuAuth()
                .get(EndpointResolver.securityChecks())
                .jsonPath()
            assertThat(json.getMap<String, Any>("")).containsKeys("count", "results")
        }
    }

    @Nested
    @DisplayName("GET /senders/ (sender profiles)")
    inner class ListSenders {

        @Test
        @DisplayName("returns paginated list of sender profiles")
        fun `list senders returns paginated response`() {
            securityAuth()
                .get(EndpointResolver.senders())
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }
    }

    @Nested
    @DisplayName("GET /prisoners/ (prisoner profiles)")
    inner class ListPrisoners {

        @Test
        @DisplayName("returns paginated list of prisoner profiles")
        fun `list prisoners returns paginated response`() {
            securityAuth()
                .get(EndpointResolver.prisoners())
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }
    }

    @Nested
    @DisplayName("GET /recipients/ (recipient profiles)")
    inner class ListRecipients {

        @Test
        @DisplayName("returns paginated list of recipient profiles")
        fun `list recipients returns paginated response`() {
            securityAuth()
                .get(EndpointResolver.recipients())
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("unauthenticated check list returns 401")
        fun `no token returns 401`() {
            ApiClient.unauthenticated()
                .get(EndpointResolver.securityChecks())
                .then()
                .statusCode(401)
        }

        @Test
        @DisplayName("unauthenticated sender list returns 401")
        fun `senders without token returns 401`() {
            ApiClient.unauthenticated()
                .get(EndpointResolver.senders())
                .then()
                .statusCode(401)
        }
    }
}
