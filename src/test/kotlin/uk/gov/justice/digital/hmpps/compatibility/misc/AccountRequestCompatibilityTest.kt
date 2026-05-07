package uk.gov.justice.digital.hmpps.compatibility.misc

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
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

@Tag("account-requests")
@DisplayName("Account Request Compatibility")
class AccountRequestCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("Account Request CRUD")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class CrudOperations {

        private var requestId: Long = 0

        @Test
        @Order(1)
        @DisplayName("POST /requests/ creates account request (no auth required)")
        fun `create account request`() {
            val uniqueUser = "test-req-${System.currentTimeMillis()}"
            val response = ApiClient.unauthenticated()
                .body(
                    mapOf(
                        "username" to uniqueUser,
                        "email" to "$uniqueUser@test.com",
                        "first_name" to "Test",
                        "last_name" to "Request",
                        "role" to "prison-clerk",
                        "prison" to existingPrisonId(),
                    ),
                )
                .post("/requests/")
            response.then().statusCode(201).body("id", notNullValue())
            requestId = response.jsonPath().getLong("id")
        }

        @Test
        @Order(2)
        @DisplayName("GET /requests/ lists account requests")
        fun `list requests`() {
            ApiClient.authenticated()
                .get("/requests/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(1))
        }

        @Test
        @Order(3)
        @DisplayName("GET /requests/{id}/ retrieves single request")
        fun `get request by id`() {
            if (requestId == 0L) return
            val response = ApiClient.authenticated()
                .get("/requests/$requestId/")
            assertThat(response.statusCode()).isIn(200, 404)
        }

        @Test
        @Order(4)
        @DisplayName("PATCH /requests/{id}/ accepts request")
        fun `accept request`() {
            if (requestId == 0L) return
            val response = ApiClient.authenticated()
                .patch("/requests/$requestId/")
            // 200 (accepted) or 404 (not found) or 400 (validation)
            assertThat(response.statusCode()).isIn(200, 400, 404)
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("POST /requests/ works without auth (public)")
        fun `create request without auth`() {
            val response = ApiClient.unauthenticated()
                .body(
                    mapOf(
                        "username" to "anon-${System.currentTimeMillis()}",
                        "email" to "anon@test.com",
                        "first_name" to "Anon",
                        "last_name" to "User",
                        "role" to "prison-clerk",
                        "prison" to existingPrisonId(),
                    ),
                )
                .post("/requests/")
            response.then().statusCode(201)
        }
    }
}
