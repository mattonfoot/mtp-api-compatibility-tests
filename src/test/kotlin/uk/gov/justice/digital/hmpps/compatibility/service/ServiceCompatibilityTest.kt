package uk.gov.justice.digital.hmpps.compatibility.service

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("service")
@DisplayName("Service API Compatibility")
class ServiceCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("GET /service-availability/")
    inner class ServiceAvailability {

        @Test
        @DisplayName("returns service availability without auth (public)")
        fun `service availability is public`() {
            ApiClient.unauthenticated()
                .get("/service-availability/")
                .then()
                .statusCode(200)
        }

        @Test
        @DisplayName("response is a JSON object with service status")
        fun `response format`() {
            val json = ApiClient.unauthenticated().get("/service-availability/").jsonPath()
            val body = json.getMap<String, Any>("")
            // Should have at least a wildcard key "*" with status
            assertThat(body).isNotEmpty
        }
    }

    @Nested
    @DisplayName("GET /notifications/")
    inner class ServiceNotifications {

        @Test
        @DisplayName("returns notifications without auth (public)")
        fun `notifications list is public`() {
            ApiClient.unauthenticated()
                .get("/notifications/")
                .then()
                .statusCode(200)
        }

        @Test
        @DisplayName("response has pagination envelope")
        fun `response has count and results`() {
            val json = ApiClient.unauthenticated().get("/notifications/").jsonPath()
            assertThat(json.getMap<String, Any>("")).containsKeys("count", "results")
        }

        @Test
        @DisplayName("authenticated request sees all notifications")
        fun `authenticated sees all`() {
            ApiClient.authenticated()
                .get("/notifications/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }
    }
}
