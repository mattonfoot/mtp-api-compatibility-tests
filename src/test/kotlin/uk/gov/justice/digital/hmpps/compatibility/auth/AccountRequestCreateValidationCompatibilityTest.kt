package uk.gov.justice.digital.hmpps.compatibility.auth

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

/**
 * Compat coverage for `POST /requests/` validation branches that Python's
 * `AccountRequestViewSet.perform_create` enforces. Targets `mtp_auth/views.py`
 * lines 410-444 (the user-exists / super-user guards) which the Kotlin port
 * currently doesn't enforce.
 */
@Tag("account-request-create-validation")
@DisplayName("POST /requests/ create-validation compatibility")
class AccountRequestCreateValidationCompatibilityTest : CompatibilityTestBase() {

    @BeforeEach
    fun clearPendingForFixtures() {
        // Each test posts a request that may collide with a leftover row from
        // a previous run — Python rejects with "already requested access".
        // Clear those rows so the test exercises the intended branch.
        db.executeSql(
            "DELETE FROM mtp_auth_accountrequest WHERE username IN ('admin', 'test-prison-1', 'bank-admin')",
        )
    }

    @Nested
    @DisplayName("Existing user already in a role")
    inner class ExistingUserInRole {

        @Test
        @DisplayName("POST /requests/ returns 400 with __mtp__.user-exists for existing user with role")
        fun `user with role rejected`() {
            // test-prison-1 is a real user in the prison-clerk role's key_group.
            val response = ApiClient.unauthenticated()
                .body(
                    mapOf(
                        "username" to "test-prison-1",
                        "email" to "compat-userexists@test.com",
                        "first_name" to "X",
                        "last_name" to "Y",
                        "role" to "prison-clerk",
                        "prison" to existingPrisonId(),
                    ),
                )
                .post("/requests/")
            assertStatus(response, expected = 400)
            // Python's body shape includes a structured __mtp__.user-exists condition
            val condition = response.jsonPath().getString("__mtp__.condition")
            assertThat(condition)
                .withFailMessage("body=%s", response.body().asString())
                .isEqualTo("user-exists")
        }

        @Test
        @DisplayName("POST /requests/ with change-role=true bypasses the rejection")
        fun `change-role bypasses user-exists`() {
            val response = ApiClient.unauthenticated()
                .body(
                    mapOf(
                        "username" to "test-prison-1",
                        "email" to "compat-changerole@test.com",
                        "first_name" to "X",
                        "last_name" to "Y",
                        "role" to "prison-clerk",
                        "prison" to existingPrisonId(),
                        "change-role" to "true",
                    ),
                )
                .post("/requests/")
            assertStatus(response, expected = 201)
        }
    }

    @Nested
    @DisplayName("Superuser usernames")
    inner class SuperUser {

        @Test
        @DisplayName("POST /requests/ for superuser returns 400")
        fun `superuser rejected`() {
            // `admin` is a seeded superuser. Python rejects with "Super users cannot be edited".
            val response = ApiClient.unauthenticated()
                .body(
                    mapOf(
                        "username" to "admin",
                        "email" to "compat-su@test.com",
                        "first_name" to "X",
                        "last_name" to "Y",
                        "role" to "prison-clerk",
                        "prison" to existingPrisonId(),
                    ),
                )
                .post("/requests/")
            assertStatus(response, expected = 400)
        }
    }

    @Nested
    @DisplayName("Existing user without a role")
    inner class ExistingUserNoRole {

        @Test
        @DisplayName("POST /requests/ for existing user with no role creates the request")
        fun `no role creates request`() {
            // bank-admin user exists but isn't in any role's key_group, so the
            // user-exists guard doesn't fire and the request is created.
            val response = ApiClient.unauthenticated()
                .body(
                    mapOf(
                        "username" to "bank-admin",
                        "email" to "compat-noroles@test.com",
                        "first_name" to "X",
                        "last_name" to "Y",
                        "role" to "prison-clerk",
                        "prison" to existingPrisonId(),
                    ),
                )
                .post("/requests/")
            assertStatus(response, expected = 201)
        }
    }

    @Nested
    @DisplayName("Role-specific validation")
    inner class RoleSpecificValidation {

        @Test
        @DisplayName("POST /requests/ for non-security role without prison returns 400")
        fun `non security role requires prison`() {
            val response = ApiClient.unauthenticated()
                .body(
                    mapOf(
                        "username" to "compat-no-prison-${System.currentTimeMillis()}",
                        "email" to "compat-no-prison@test.com",
                        "first_name" to "No",
                        "last_name" to "Prison",
                        "role" to "prison-clerk",
                    ),
                )
                .post("/requests/")
            assertStatus(response, expected = 400)
            assertThat(response.jsonPath().getList("prison", String::class.java))
                .withFailMessage("body=%s", response.body().asString())
                .containsExactly("Prison must be specified")
        }

        @Test
        @DisplayName("POST /requests/ for security role without manager_email returns 400")
        fun `security role requires manager email`() {
            val response = ApiClient.unauthenticated()
                .body(
                    mapOf(
                        "username" to "compat-no-manager-${System.currentTimeMillis()}",
                        "email" to "compat-no-manager@test.com",
                        "first_name" to "No",
                        "last_name" to "Manager",
                        "role" to "security",
                    ),
                )
                .post("/requests/")
            assertStatus(response, expected = 400)
            assertThat(response.jsonPath().getList("manager_email", String::class.java))
                .withFailMessage("body=%s", response.body().asString())
                .containsExactly("Manager's email must be specified")
        }
    }
}
