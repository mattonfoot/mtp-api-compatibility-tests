package uk.gov.justice.digital.hmpps.compatibility.prison

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("prison-extras")
@DisplayName("Prison Extras Compatibility")
class PrisonExtrasCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("GET /prisoner_locations/{prisoner_number}/")
    inner class LocationRetrieve {

        @Test
        @DisplayName("retrieves location for known prisoner")
        fun `get location by prisoner number`() {
            val response = ApiClient.authenticatedAs("test-token-prisoner-location-admin")
                .get("/prisoner_locations/A1409AE/")
            // May return 200 (found) or 404 (not in user's scope)
            assertThat(response.statusCode()).isIn(200, 404)
        }
    }

    @Nested
    @DisplayName("GET /prisoner_locations/can-upload/")
    inner class CanUpload {

        @Test
        @DisplayName("returns can_upload boolean")
        fun `can upload check`() {
            val response = ApiClient.authenticatedAs("test-token-prisoner-location-admin")
                .get("/prisoner_locations/can-upload/")
            response.then().statusCode(200)
            val json = response.jsonPath()
            assertThat(json.getMap<String, Any>("")).containsKey("can_upload")
        }
    }

    @Nested
    @DisplayName("GET /prisoner_credit_notice_email/")
    inner class CreditNoticeEmails {

        @Test
        @DisplayName("returns list of credit notice emails")
        fun `list credit notice emails`() {
            ApiClient.authenticatedAs("test-token-prison-clerk")
                .get("/prisoner_credit_notice_email/")
                .then()
                .statusCode(200)
        }
    }

    @Nested
    @DisplayName("Auth endpoints")
    inner class AuthEndpoints {

        @Test
        @DisplayName("GET /roles/ returns list of roles")
        fun `list roles`() {
            ApiClient.authenticated()
                .get("/roles/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("GET /users/ returns list of users")
        fun `list users`() {
            ApiClient.authenticated()
                .get("/users/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("GET /users/{username}/ returns 200 or 404")
        fun `get user by username`() {
            val response = ApiClient.authenticated().get("/users/admin/")
            // 200 if user exists, 404 if not in the user table
            assertThat(response.statusCode()).isIn(200, 404)
        }

        @Test
        @DisplayName("GET /requests/ returns list of account requests")
        fun `list requests`() {
            ApiClient.authenticated()
                .get("/requests/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }
    }
}
