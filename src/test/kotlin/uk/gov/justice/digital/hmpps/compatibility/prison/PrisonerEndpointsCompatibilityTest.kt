package uk.gov.justice.digital.hmpps.compatibility.prison

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("prisoner-endpoints")
@DisplayName("Prisoner Endpoints Compatibility")
class PrisonerEndpointsCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("GET /prisoner_locations/")
    inner class PrisonerLocations {

        @Test
        @DisplayName("prisoner locations requires authentication")
        fun `locations requires auth`() {
            ApiClient.unauthenticated()
                .get("/prisoner_locations/")
                .then()
                .statusCode(401)
        }
    }

    @Nested
    @DisplayName("GET /prisoner_account_balances/")
    inner class PrisonerAccountBalances {

        @Test
        @DisplayName("prisoner balance endpoint responds (200 or 400/404 if NOMIS unavailable or not found)")
        fun `get prisoner balance`() {
            val response = ApiClient.authenticatedAs("test-token-send-money")
                .get("/prisoner_account_balances/A1409AE/")
            // 200 with balance, 400 if NOMIS unavailable, 404 if prisoner not found
            assertThat(response.statusCode()).isIn(200, 400, 404)
        }
    }

    @Nested
    @DisplayName("GET /prisoner_validity/")
    inner class PrisonerValidity {

        @Test
        @DisplayName("validates prisoner with both required params")
        fun `validate prisoner`() {
            ApiClient.authenticatedAs("test-token-send-money")
                .queryParam("prisoner_number", "A1409AE")
                .queryParam("prisoner_dob", "1990-01-15")
                .get("/prisoner_validity/")
                .then()
                .statusCode(200)
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("unauthenticated prisoner locations returns 401")
        fun `locations without token`() {
            ApiClient.unauthenticated()
                .get("/prisoner_locations/")
                .then()
                .statusCode(401)
        }

        @Test
        @DisplayName("unauthenticated prisoner validity returns 401")
        fun `validity without token`() {
            ApiClient.unauthenticated()
                .get("/prisoner_validity/")
                .then()
                .statusCode(401)
        }
    }
}
