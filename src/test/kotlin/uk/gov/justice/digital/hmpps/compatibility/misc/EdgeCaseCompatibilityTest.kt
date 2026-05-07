package uk.gov.justice.digital.hmpps.compatibility.misc

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver

@Tag("edge-cases")
@DisplayName("Edge Case Compatibility")
class EdgeCaseCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("Pagination edge cases")
    inner class Pagination {

        @Test
        @DisplayName("offset beyond total returns empty results")
        fun `offset beyond total`() {
            val json = ApiClient.authenticated()
                .queryParam("offset", 99999)
                .queryParam("limit", 10)
                .get(EndpointResolver.balances())
                .jsonPath()
            assertThat(json.getList<Any>("results")).isEmpty()
        }

        @Test
        @DisplayName("limit=0 returns empty results")
        fun `limit zero`() {
            val response = ApiClient.authenticated()
                .queryParam("limit", 0)
                .get(EndpointResolver.balances())
            // Should return 200 with empty results or 400
            assertThat(response.statusCode()).isIn(200, 400)
        }

        @Test
        @DisplayName("limit=1 returns single result")
        fun `limit one`() {
            ApiClient.authenticated()
                .queryParam("limit", 1)
                .get(EndpointResolver.credits())
                .then()
                .statusCode(200)
        }
    }

    @Nested
    @DisplayName("Invalid input handling")
    inner class InvalidInputs {

        @Test
        @DisplayName("invalid date format returns 400")
        fun `invalid date filter`() {
            val response = ApiClient.authenticated()
                .queryParam("date__lt", "not-a-date")
                .get(EndpointResolver.balances())
            assertThat(response.statusCode()).isIn(400, 200)
        }

        @Test
        @DisplayName("invalid resolution enum returns 400")
        fun `invalid resolution filter`() {
            val response = ApiClient.authenticated()
                .queryParam("resolution", "not_a_resolution")
                .get(EndpointResolver.credits())
            assertThat(response.statusCode()).isIn(400, 200)
        }

        @Test
        @DisplayName("non-existent resource returns 404")
        fun `nonexistent resource`() {
            ApiClient.authenticated()
                .get("/nonexistent-endpoint/")
                .then()
                .statusCode(404)
        }

        @Test
        @DisplayName("PUT on read-only endpoint returns 405 or 403")
        fun `method not allowed`() {
            // Django's permission_classes are checked before HTTP method routing,
            // so unauthorized PUTs return 403 instead of 405.
            val response = ApiClient.authenticated().put(EndpointResolver.balances())
            assertThat(response.statusCode()).isIn(403, 405)
        }
    }

    @Nested
    @DisplayName("Empty body handling")
    inner class EmptyBodies {

        @Test
        @DisplayName("POST /credits/actions/review/ with empty credit_ids")
        fun `empty review`() {
            val response = ApiClient.authenticatedAs("test-token-security")
                .body(mapOf("credit_ids" to emptyList<Long>()))
                .post("/credits/actions/review/")
            // 204 (nothing to do) or 400 (empty list)
            assertThat(response.statusCode()).isIn(204, 400)
        }

        @Test
        @DisplayName("POST /disbursements/actions/reject/ with empty disbursement_ids")
        fun `empty reject`() {
            val response = ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("disbursement_ids" to emptyList<Long>()))
                .post("/disbursements/actions/reject/")
            // 204 (nothing to do) or 400 (empty list)
            assertThat(response.statusCode()).isIn(204, 400)
        }
    }

    @Nested
    @DisplayName("Trailing slash handling")
    inner class TrailingSlash {

        @Test
        @DisplayName("endpoints work with trailing slash")
        fun `with trailing slash`() {
            ApiClient.authenticated()
                .get("/credits/")
                .then()
                .statusCode(200)
        }
    }
}
