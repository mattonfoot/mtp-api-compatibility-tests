package uk.gov.justice.digital.hmpps.compatibility.auth

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

/**
 * Compat coverage for the user-management scope filter (Python's
 * `mtp_auth/views.py::get_managed_user_queryset` + `UserViewSet.get_object`).
 *
 * In Python a requester can only GET/PATCH/DELETE another user if:
 *   * the target is themselves, OR
 *   * the requester has a single role's key_group, is not a superuser, and the
 *     target shares the same key_group and at least one prison in the
 *     requester's `PrisonUserMapping`.
 *
 * Out-of-scope targets 404 before any permission check.
 */
@Tag("user-scope")
@DisplayName("User-scope filter compatibility")
class UserScopeCompatibilityTest : CompatibilityTestBase() {

    private val testUsername = "compat-scope-${System.currentTimeMillis()}"

    @BeforeAll
    fun seedTarget() {
        // Insert a bare auth_user row with no prison mapping. Both APIs should
        // 404 for any requester whose managed queryset doesn't include this user.
        db.executeSql(
            """
            INSERT INTO auth_user
              (password, is_superuser, username, first_name, last_name, email,
               is_staff, is_active, date_joined)
            VALUES
              ('!', false, '$testUsername', 'Compat', 'Scope',
               '$testUsername@x.com', false, true, NOW())
            """.trimIndent(),
        )
    }

    @AfterAll
    fun cleanup() {
        db.executeSql("DELETE FROM auth_user WHERE username = '$testUsername'")
    }

    @Nested
    @DisplayName("Requester targeting an out-of-scope user")
    inner class OutOfScope {

        @Test
        @DisplayName("admin → /users/{out-of-scope}/ GET returns 404 (managed queryset is self only)")
        fun `admin GET other`() {
            val response = ApiClient.authenticatedAs("test-token-admin")
                .get("/users/$testUsername/")
            assertStatus(response, expected = 404)
        }

        @Test
        @DisplayName("admin → PATCH /users/{out-of-scope}/ returns 404")
        fun `admin PATCH other`() {
            val response = ApiClient.authenticatedAs("test-token-admin")
                .body(mapOf("first_name" to "Renamed"))
                .patch("/users/$testUsername/")
            assertStatus(response, expected = 404)
        }

        @Test
        @DisplayName("admin → DELETE /users/{out-of-scope}/ returns 404")
        fun `admin DELETE other`() {
            val response = ApiClient.authenticatedAs("test-token-admin")
                .delete("/users/$testUsername/")
            assertStatus(response, expected = 404)
        }

        @Test
        @DisplayName("prison-clerk → /users/{out-of-scope}/ GET returns 404")
        fun `prison-clerk GET other`() {
            val response = ApiClient.authenticatedAs("test-token-prison-clerk")
                .get("/users/$testUsername/")
            assertStatus(response, expected = 404)
        }

        @Test
        @DisplayName("prison-clerk → DELETE /users/{out-of-scope}/ returns 404")
        fun `prison-clerk DELETE other`() {
            val response = ApiClient.authenticatedAs("test-token-prison-clerk")
                .delete("/users/$testUsername/")
            assertStatus(response, expected = 404)
        }
    }

    @Nested
    @DisplayName("Requester targeting themselves")
    inner class Self {

        @Test
        @DisplayName("admin → GET /users/admin/ returns 200")
        fun `admin GET self`() {
            val response = ApiClient.authenticatedAs("test-token-admin")
                .get("/users/admin/")
            assertStatus(response, expected = 200)
        }
    }
}
