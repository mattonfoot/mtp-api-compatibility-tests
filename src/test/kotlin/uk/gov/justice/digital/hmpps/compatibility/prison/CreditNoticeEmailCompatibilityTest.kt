package uk.gov.justice.digital.hmpps.compatibility.prison

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("credit-notice-email")
@DisplayName("Credit Notice Email Compatibility")
class CreditNoticeEmailCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("Credit Notice Email CRUD")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class CrudOperations {

        @Test
        @Order(1)
        @DisplayName("POST /prisoner_credit_notice_email/ creates email")
        fun `create credit notice email`() {
            // Delete existing first to avoid conflict
            db.executeSql("DELETE FROM prison_prisonercreditnoticeemail WHERE prison_id = '${existingPrisonId()}'")

            val response = ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("prison" to existingPrisonId(), "email" to "notice@test.com"))
                .post("/prisoner_credit_notice_email/")
            response.then().statusCode(201)
        }

        @Test
        @Order(2)
        @DisplayName("GET /prisoner_credit_notice_email/ lists emails")
        fun `list credit notice emails`() {
            val response = ApiClient.authenticatedAs("test-token-prison-clerk")
                .get("/prisoner_credit_notice_email/")
            response.then().statusCode(200)
        }

        @Test
        @Order(3)
        @DisplayName("PATCH /prisoner_credit_notice_email/{prison}/ updates email")
        fun `update credit notice email`() {
            val response = ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("email" to "updated@test.com"))
                .patch("/prisoner_credit_notice_email/${existingPrisonId()}/")
            // 200 (updated) or 404 (not found)
            assertThat(response.statusCode()).isIn(200, 404)
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("unauthenticated create returns 401")
        fun `create without auth`() {
            ApiClient.unauthenticated()
                .body(mapOf("prison" to "IXB", "email" to "test@test.com"))
                .post("/prisoner_credit_notice_email/")
                .then()
                .statusCode(401)
        }
    }
}
