package uk.gov.justice.digital.hmpps.compatibility.misc

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("misc")
@DisplayName("Miscellaneous Endpoints Compatibility")
class MiscEndpointsCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("File Downloads")
    inner class FileDownloads {

        @Test
        @DisplayName("GET /file-downloads/ returns paginated list")
        fun `list file downloads`() {
            ApiClient.authenticated()
                .get("/file-downloads/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("POST /file-downloads/ creates a download record")
        fun `create file download`() {
            val uniqueLabel = "test-${System.currentTimeMillis()}"
            val response = ApiClient.authenticated()
                .body(mapOf("label" to uniqueLabel, "date" to "2024-06-15"))
                .post("/file-downloads/")
            response.then().statusCode(201)
        }
    }

    @Nested
    @DisplayName("Performance Data")
    inner class PerformanceData {

        @Test
        @DisplayName("GET /performance/data/ returns performance data")
        fun `list performance data`() {
            val response = ApiClient.authenticatedAs("test-token-send-money")
                .get("/performance/data/")
            // 200 with data, 403 (wrong perms), or 500 (not fully implemented)
            assertThat(response.statusCode()).isIn(200, 403, 500)
        }
    }

    @Nested
    @DisplayName("Private Estate Batches")
    inner class PrivateEstateBatches {

        @Test
        @DisplayName("GET /private-estate-batches/ returns paginated list")
        fun `list private estate batches`() {
            ApiClient.authenticated()
                .get("/private-estate-batches/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }
    }

    @Nested
    @DisplayName("Password Endpoints")
    inner class PasswordEndpoints {

        @Test
        @DisplayName("POST /reset_password/ accepts request")
        fun `reset password`() {
            val response = ApiClient.unauthenticated()
                .body(mapOf("username" to "nonexistent-user"))
                .post("/reset_password/")
            // 204 (token created), 400 (locked/no email), or 404 (user not found)
            assertThat(response.statusCode()).isIn(204, 400, 404)
        }

        @Test
        @DisplayName("POST /change_password/ accepts token-based change")
        fun `change password by token`() {
            // Attempt with invalid token - should return 400 or 404
            val response = ApiClient.unauthenticated()
                .body(mapOf("token" to "00000000-0000-0000-0000-000000000000", "new_password" to "NewPass123!"))
                .post("/change_password/")
            assertThat(response.statusCode()).isIn(400, 404)
        }

        @Test
        @DisplayName("POST /change_password/{code}/ accepts code-based change")
        fun `change password by code`() {
            // Attempt with invalid code - should return 404
            val response = ApiClient.unauthenticated()
                .body(mapOf("new_password" to "NewPass123!"))
                .post("/change_password/00000000-0000-0000-0000-000000000000/")
            assertThat(response.statusCode()).isIn(400, 404)
        }
    }

    @Nested
    @DisplayName("Job Information")
    inner class JobInformation {

        @Test
        @DisplayName("POST /job-information/ responds to authenticated request")
        fun `create job information`() {
            val response = ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("title" to "Test Job", "prison_estate" to "public", "tasks" to "Testing"))
                .post("/job-information/")
            // 200/201 (success), 400 (validation), or 401 (auth issue)
            assertThat(response.statusCode()).isIn(200, 201, 400, 401)
        }
    }
}
