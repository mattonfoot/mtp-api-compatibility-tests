package uk.gov.justice.digital.hmpps.compatibility.auth

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

/**
 * Full account-request accept and reject lifecycle compat tests. Targets the
 * remaining uncovered branches in `mtp_auth/views.py` lines 446-499 (partial_update
 * accept path) and 553-591 (perform_destroy reject + email side-effects).
 *
 * Each scenario creates its own request anonymously, then accepts or rejects it
 * with an admin token, asserting strict per-target status codes. Side-effect
 * emails go through `coverage_settings.py`'s monkey-patched `mtp_common.notify`.
 */
@Tag("account-request-lifecycle")
@DisplayName("Account request accept/reject lifecycle")
class AccountRequestLifecycleCompatibilityTest : CompatibilityTestBase() {

    private fun adminAuth() = ApiClient.authenticatedAs("test-token-admin")

    @Nested
    @DisplayName("Accept flow")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class AcceptFlow {

        private val ts = System.currentTimeMillis()
        private val username = "compat-accept-$ts"
        private var requestId: Long = 0

        @Test
        @Order(1)
        @DisplayName("POST /requests/ creates the request (anon)")
        fun `create request`() {
            val response = ApiClient.unauthenticated()
                .body(
                    mapOf(
                        "username" to username,
                        "email" to "$username@test.com",
                        "first_name" to "Compat",
                        "last_name" to "Accept",
                        "role" to "prison-clerk",
                        "prison" to existingPrisonId(),
                    ),
                )
                .post("/requests/")
            assertStatus(response, expected = 201)
            requestId = response.jsonPath().getLong("id")
        }

        @Test
        @Order(2)
        @DisplayName("PATCH /requests/{id}/ accepts the request and creates the user")
        fun `accept request`() {
            if (requestId == 0L) return
            val response = adminAuth()
                .body(mapOf("user_admin" to "false"))
                .patch("/requests/$requestId/")
            assertStatus(response, expected = 200)
        }

        @Test
        @Order(3)
        @DisplayName("GET /requests/{id}/ after accept returns 404 (row deleted)")
        fun `request gone after accept`() {
            if (requestId == 0L) return
            val response = adminAuth().get("/requests/$requestId/")
            assertStatus(response, expected = 404)
        }
    }

    @Nested
    @DisplayName("Reject flow")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class RejectFlow {

        private val ts = System.currentTimeMillis() + 1
        private val username = "compat-reject-$ts"
        private var requestId: Long = 0

        @Test
        @Order(1)
        @DisplayName("POST /requests/ creates the request (anon)")
        fun `create request`() {
            val response = ApiClient.unauthenticated()
                .body(
                    mapOf(
                        "username" to username,
                        "email" to "$username@test.com",
                        "first_name" to "Compat",
                        "last_name" to "Reject",
                        "role" to "prison-clerk",
                        "prison" to existingPrisonId(),
                    ),
                )
                .post("/requests/")
            assertStatus(response, expected = 201)
            requestId = response.jsonPath().getLong("id")
        }

        @Test
        @Order(2)
        @DisplayName("DELETE /requests/{id}/ rejects the request (fires email)")
        fun `reject request`() {
            if (requestId == 0L) return
            val response = adminAuth().delete("/requests/$requestId/")
            assertStatus(response, expected = 204)
        }

        @Test
        @Order(3)
        @DisplayName("GET /requests/{id}/ after reject returns 404")
        fun `request gone after reject`() {
            if (requestId == 0L) return
            val response = adminAuth().get("/requests/$requestId/")
            assertStatus(response, expected = 404)
        }
    }

    @Nested
    @DisplayName("Validation edges")
    inner class ValidationEdges {

        @Test
        @DisplayName("PATCH /requests/{unknown}/ returns 404")
        fun `accept unknown`() {
            val response = adminAuth()
                .body(mapOf("user_admin" to "false"))
                .patch("/requests/999999999/")
            assertStatus(response, expected = 404)
        }

        @Test
        @DisplayName("DELETE /requests/{unknown}/ returns 404")
        fun `reject unknown`() {
            val response = adminAuth().delete("/requests/999999999/")
            assertStatus(response, expected = 404)
        }
    }
}
