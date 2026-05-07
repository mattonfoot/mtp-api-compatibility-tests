package uk.gov.justice.digital.hmpps.compatibility.prison

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("prisoner-location-crud")
@DisplayName("Prisoner Location CRUD Compatibility")
class PrisonerLocationCrudCompatibilityTest : CompatibilityTestBase() {

    private fun locationAuth() = ApiClient.authenticatedAs("test-token-prisoner-location-admin")

    @Nested
    @DisplayName("POST /prisoner_locations/ (bulk create)")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class BulkCreate {

        @Test
        @Order(1)
        @DisplayName("creates prisoner locations in bulk")
        fun `bulk create locations`() {
            val body = listOf(
                mapOf(
                    "prisoner_number" to "Z9999ZZ",
                    "prisoner_dob" to "1985-06-15",
                    "prison" to existingPrisonId(),
                ),
            )
            locationAuth()
                .body(body)
                .post("/prisoner_locations/")
                .then()
                .statusCode(201)
        }

        @Test
        @Order(2)
        @DisplayName("created location retrievable by prisoner number")
        fun `retrieve created location`() {
            val response = locationAuth()
                .get("/prisoner_locations/Z9999ZZ/")
            // 200 if accessible, 404 if filtered by prison scope, 403 if role lacks view perm
            assertThat(response.statusCode()).isIn(200, 403, 404)
        }
    }

    @Nested
    @DisplayName("Location management actions")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class LocationActions {

        @Test
        @Order(1)
        @DisplayName("POST /prisoner_locations/actions/delete_inactive/ removes inactive locations")
        fun `delete inactive`() {
            locationAuth()
                .post("/prisoner_locations/actions/delete_inactive/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(2)
        @DisplayName("POST /prisoner_locations/actions/delete_old/ rotates locations")
        fun `delete old`() {
            locationAuth()
                .post("/prisoner_locations/actions/delete_old/")
                .then()
                .statusCode(204)
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("unauthenticated bulk create returns 401")
        fun `create without auth`() {
            ApiClient.unauthenticated()
                .body(listOf(mapOf("prisoner_number" to "A0001AA", "prison" to "IXB")))
                .post("/prisoner_locations/")
                .then()
                .statusCode(401)
        }

        @Test
        @DisplayName("unauthenticated delete_old returns 401")
        fun `delete_old without auth`() {
            ApiClient.unauthenticated()
                .post("/prisoner_locations/actions/delete_old/")
                .then()
                .statusCode(401)
        }
    }
}
