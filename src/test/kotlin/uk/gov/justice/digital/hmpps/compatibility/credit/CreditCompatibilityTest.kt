package uk.gov.justice.digital.hmpps.compatibility.credit

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver

@Tag("credits")
@DisplayName("Credit API Compatibility")
class CreditCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("GET /credits/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class ListCredits {

        @BeforeAll
        fun seedData() {
            db.executeSql(
                EndpointResolver.creditInsertSql(
                    amount = 5000,
                    prisonerNumber = "A1234BC",
                    prisonerName = "John Smith",
                    prisonId = existingPrisonId(),
                ),
            )
        }

        @Test
        @Order(1)
        @DisplayName("returns paginated response with credits")
        fun `list credits returns paginated envelope`() {
            ApiClient.authenticated()
                .get(EndpointResolver.credits())
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
                .body("results", hasSize<Any>(greaterThanOrEqualTo(1)))
        }

        @Test
        @Order(2)
        @DisplayName("credit response has expected field names")
        fun `credit response fields`() {
            val credit = ApiClient.authenticated()
                .get(EndpointResolver.credits())
                .jsonPath()
                .getMap<String, Any>("results[0]")

            assertThat(credit).containsKeys("id", "amount", "prisoner_number", "prisoner_name", "resolution")
        }

        @Test
        @Order(3)
        @DisplayName("filter by prisoner_number")
        fun `filter by prisoner number`() {
            // Get a prisoner number from existing credits visible to this user
            val existingCredits = ApiClient.authenticated()
                .get(EndpointResolver.credits())
                .jsonPath()
                .getList<Map<String, Any>>("results")
            assertThat(existingCredits).isNotEmpty

            val prisonerNumber = existingCredits[0]["prisoner_number"] as String
            val filtered = ApiClient.authenticated()
                .queryParam("prisoner_number", prisonerNumber)
                .get(EndpointResolver.credits())
                .jsonPath()
            assertThat(filtered.getInt("count")).isGreaterThanOrEqualTo(1)
        }

        @Test
        @Order(4)
        @DisplayName("filter by resolution")
        fun `filter by resolution`() {
            val json = ApiClient.authenticated()
                .queryParam("resolution", "credited")
                .get(EndpointResolver.credits())
                .jsonPath()
            assertThat(json.getInt("count")).isGreaterThanOrEqualTo(1)
        }

        @Test
        @Order(5)
        @DisplayName("filter by prison")
        fun `filter by prison`() {
            val json = ApiClient.authenticated()
                .queryParam("prison", existingPrisonId())
                .get(EndpointResolver.credits())
                .jsonPath()
            assertThat(json.getInt("count")).isGreaterThanOrEqualTo(1)
        }
    }

    @Nested
    @DisplayName("GET /credits/processed/")
    inner class ProcessedCredits {

        @Test
        fun `processed credits returns 200`() {
            ApiClient.authenticated()
                .get("/credits/processed/")
                .then()
                .statusCode(200)
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        fun `unauthenticated request returns 401`() {
            ApiClient.unauthenticated()
                .get(EndpointResolver.credits())
                .then()
                .statusCode(401)
        }
    }
}
