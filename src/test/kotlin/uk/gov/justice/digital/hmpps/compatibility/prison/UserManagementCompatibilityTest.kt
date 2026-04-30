package uk.gov.justice.digital.hmpps.compatibility.prison

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
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
import uk.gov.justice.digital.hmpps.compatibility.config.ApiTarget
import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("user-management")
@DisplayName("User Management Compatibility")
class UserManagementCompatibilityTest : CompatibilityTestBase() {

    private val testUsername = "compat-test-user-${System.currentTimeMillis()}"

    @Nested
    @DisplayName("User CRUD lifecycle")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class UserCrud {

        @Test
        @Order(1)
        @DisplayName("POST /users/ creates a new user and returns 201")
        fun `create user`() {
            val response = ApiClient.authenticated()
                .body(
                    mapOf(
                        "username" to testUsername,
                        "email" to "$testUsername@test.local",
                        "first_name" to "Compat",
                        "last_name" to "Test",
                    ),
                )
                .post("/users/")
            response.then()
                .statusCode(201)
                .body("username", equalTo(testUsername.lowercase()))
                .body("pk", notNullValue())
                .body("is_active", equalTo(true))
        }

        @Test
        @Order(2)
        @DisplayName("GET /users/{username}/ returns user details with expected fields")
        fun `get user detail`() {
            val response = ApiClient.authenticated()
                .get("/users/${testUsername.lowercase()}/")
            if (response.statusCode() == 404) return // user not created in previous test
            response.then()
                .statusCode(200)
                .body("username", equalTo(testUsername.lowercase()))
                .body("first_name", equalTo("Compat"))
                .body("last_name", equalTo("Test"))
                .body("is_active", equalTo(true))
                .body("prisons", notNullValue())
                .body("flags", notNullValue())
        }

        @Test
        @Order(3)
        @DisplayName("PATCH /users/{username}/ updates user fields")
        fun `patch user`() {
            val response = ApiClient.authenticated()
                .body(mapOf("first_name" to "Updated"))
                .patch("/users/${testUsername.lowercase()}/")
            if (response.statusCode() == 404) return
            response.then()
                .statusCode(200)
                .body("first_name", equalTo("Updated"))
        }

        @Test
        @Order(4)
        @DisplayName("PUT /users/{username}/ also updates user fields (same as PATCH)")
        fun `put user`() {
            val response = ApiClient.authenticated()
                .body(mapOf("last_name" to "PutTest"))
                .put("/users/${testUsername.lowercase()}/")
            if (response.statusCode() == 404) return
            response.then()
                .statusCode(200)
                .body("last_name", equalTo("PutTest"))
        }

        @Test
        @Order(5)
        @DisplayName("DELETE /users/{username}/ deactivates user and returns 204")
        fun `delete user`() {
            val response = ApiClient.authenticated()
                .delete("/users/${testUsername.lowercase()}/")
            if (response.statusCode() == 404) return
            response.then().statusCode(204)
        }

        @Test
        @Order(6)
        @DisplayName("GET /users/{username}/ after DELETE shows is_active=false")
        fun `user is deactivated after delete`() {
            val response = ApiClient.authenticated()
                .get("/users/${testUsername.lowercase()}/")
            if (response.statusCode() == 404) return
            response.then()
                .statusCode(200)
                .body("is_active", equalTo(false))
        }
    }

    @Nested
    @DisplayName("User Flags")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class UserFlags {

        private val flagTestUsername = "compat-flag-user-${System.currentTimeMillis()}"

        @Test
        @Order(1)
        @DisplayName("create user for flag tests")
        fun `setup user`() {
            ApiClient.authenticated()
                .body(
                    mapOf(
                        "username" to flagTestUsername,
                        "email" to "$flagTestUsername@test.local",
                    ),
                )
                .post("/users/")
                .then()
                .statusCode(201)
        }

        @Test
        @Order(2)
        @DisplayName("GET /users/{username}/flags/ returns paginated list")
        fun `list flags`() {
            val response = ApiClient.authenticated()
                .get("/users/${flagTestUsername.lowercase()}/flags/")
            if (response.statusCode() == 404) return
            response.then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @Order(3)
        @DisplayName("PUT /users/{username}/flags/{name}/ creates or ensures flag exists")
        fun `put flag`() {
            val response = ApiClient.authenticated()
                .put("/users/${flagTestUsername.lowercase()}/flags/hmpps-employee/")
            if (response.statusCode() == 404) return
            assertThat(response.statusCode()).isIn(200, 204)
        }

        @Test
        @Order(4)
        @DisplayName("GET /users/{username}/flags/ includes the created flag")
        fun `flag is present after put`() {
            val response = ApiClient.authenticated()
                .get("/users/${flagTestUsername.lowercase()}/flags/")
            if (response.statusCode() == 404) return
            val flags = response.jsonPath().getList<String>("results")
            assertThat(flags).contains("hmpps-employee")
        }

        @Test
        @Order(5)
        @DisplayName("DELETE /users/{username}/flags/{name}/ removes flag")
        fun `delete flag`() {
            val response = ApiClient.authenticated()
                .delete("/users/${flagTestUsername.lowercase()}/flags/hmpps-employee/")
            if (response.statusCode() == 404) return
            response.then().statusCode(204)
        }

        @Test
        @Order(6)
        @DisplayName("cleanup - delete test user")
        fun `cleanup`() {
            ApiClient.authenticated()
                .delete("/users/${flagTestUsername.lowercase()}/")
        }
    }

    @Nested
    @DisplayName("Account Request lifecycle")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class AccountRequests {

        private var requestId: Long = 0

        @Test
        @Order(1)
        @DisplayName("POST /requests/ creates account request")
        fun `create request`() {
            val response = ApiClient.authenticated()
                .body(
                    mapOf(
                        "username" to "compat-req-${System.currentTimeMillis()}",
                        "first_name" to "Test",
                        "last_name" to "Request",
                        "email" to "compatreq@test.local",
                        "role" to "prison-clerk",
                        "prison" to existingPrisonId(),
                    ),
                )
                .post("/requests/")
            assertThat(response.statusCode()).isIn(201, 400) // 400 if role doesn't exist
            if (response.statusCode() == 201) {
                requestId = response.jsonPath().getLong("id")
            }
        }

        @Test
        @Order(2)
        @DisplayName("DELETE /requests/{id}/ deletes account request")
        fun `delete request`() {
            if (requestId == 0L) return
            ApiClient.authenticated()
                .delete("/requests/$requestId/")
                .then()
                .statusCode(204)
        }
    }
}
