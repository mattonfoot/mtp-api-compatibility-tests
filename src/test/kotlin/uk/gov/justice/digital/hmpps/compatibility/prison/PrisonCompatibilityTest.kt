package uk.gov.justice.digital.hmpps.compatibility.prison

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver

@Tag("prisons")
@DisplayName("Prison API Compatibility")
class PrisonCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("GET /prisons/")
    inner class ListPrisons {

        @Test
        @DisplayName("returns paginated list of prisons")
        fun `list prisons returns paginated response`() {
            ApiClient.authenticated()
                .get(EndpointResolver.prisons())
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
        }

        @Test
        @DisplayName("response has correct field names (snake_case)")
        fun `prison response field names`() {
            val prison = ApiClient.authenticated()
                .get(EndpointResolver.prisons())
                .jsonPath()
                .getMap<String, Any>("results[0]")

            assertThat(prison).containsKeys("nomis_id", "name")
        }
    }

    @Nested
    @DisplayName("GET /prison_categories/")
    inner class ListCategories {

        @Test
        @DisplayName("returns paginated list of prison categories")
        fun `list categories`() {
            ApiClient.authenticated()
                .get(EndpointResolver.prisonCategories())
                .then()
                .statusCode(200)
                .body("results", hasSize<Any>(greaterThanOrEqualTo(1)))
        }
    }

    @Nested
    @DisplayName("GET /prison_populations/")
    inner class ListPopulations {

        @Test
        @DisplayName("returns paginated list of prison populations")
        fun `list populations`() {
            ApiClient.authenticated()
                .get(EndpointResolver.prisonPopulations())
                .then()
                .statusCode(200)
                .body("results", hasSize<Any>(greaterThanOrEqualTo(1)))
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("prisons list is public (no auth required)")
        fun `prisons accessible without auth`() {
            // Both Python and Kotlin allow unauthenticated access to /prisons/
            ApiClient.unauthenticated()
                .get(EndpointResolver.prisons())
                .then()
                .statusCode(200)
        }
    }
}
