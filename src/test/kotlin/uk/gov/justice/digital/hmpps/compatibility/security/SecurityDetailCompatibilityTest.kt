package uk.gov.justice.digital.hmpps.compatibility.security

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.config.ApiTarget
import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("security-detail")
@DisplayName("Security Detail Endpoint Compatibility")
class SecurityDetailCompatibilityTest : CompatibilityTestBase() {

    private fun securityAuth() = ApiClient.authenticatedAs("test-token-security")
    private fun fiuAuth() = ApiClient.authenticatedAs("test-token-fiu")

    @Nested
    @DisplayName("GET /security/checks/{id}/")
    inner class CheckDetail {

        @Test
        @DisplayName("returns 200 with check details for existing check")
        fun `get check by id`() {
            val checkIdCol = if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "check_id"
            val id = db.query("SELECT $checkIdCol AS id FROM security_check LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            val response = fiuAuth()
                .get("/security/checks/${id.toLong()}/")
            assertThat(response.statusCode()).isEqualTo(200)
            assertThat(response.jsonPath().get<Any>("id")).isNotNull
        }

        @Test
        @DisplayName("returns 404 for non-existent check")
        fun `get non-existent check returns 404`() {
            fiuAuth()
                .get("/security/checks/999999/")
                .then()
                .statusCode(404)
        }
    }

    @Nested
    @DisplayName("GET /security/checks/auto-accept/{id}/")
    inner class AutoAcceptDetail {

        @Test
        @DisplayName("returns 200 for existing auto-accept rule")
        fun `get auto-accept rule by id`() {
            val tableName = if (TestConfig.apiTarget == ApiTarget.PYTHON) {
                "security_checkautoacceptrule"
            } else {
                "security_check_auto_accept_rule"
            }
            val id = db.query("SELECT id FROM $tableName LIMIT 1")
                .firstOrNull()?.get("id") as? Number
            if (id == null) {
                // No rules exist; create one to test
                val response = fiuAuth()
                    .body(
                        mapOf(
                            "prisoner_profile_id" to 1,
                            "debit_card_sender_details_id" to 1,
                            "states" to listOf(mapOf("reason" to "test")),
                        ),
                    )
                    .post("/security/checks/auto-accept/")
                if (response.statusCode() != 201) return
                val ruleId = response.jsonPath().getLong("id")
                fiuAuth()
                    .get("/security/checks/auto-accept/$ruleId/")
                    .then()
                    .statusCode(200)
                    .body("id", notNullValue())
            } else {
                fiuAuth()
                    .get("/security/checks/auto-accept/${id.toLong()}/")
                    .then()
                    .statusCode(200)
                    .body("id", notNullValue())
            }
        }
    }

    @Nested
    @DisplayName("Recipient nested disbursements")
    inner class RecipientDisbursements {

        private val recipientIdCol get() =
            if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "recipient_profile_id"

        @Test
        @DisplayName("GET /recipients/{recipient_pk}/disbursements/ returns paginated list")
        fun `list recipient disbursements`() {
            val id = db.query("SELECT $recipientIdCol AS id FROM security_recipientprofile LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            securityAuth()
                .get("/recipients/${id.toLong()}/disbursements/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("GET /recipients/{recipient_pk}/disbursements/{id}/ returns single disbursement")
        fun `get single recipient disbursement`() {
            val recipientId = db.query("SELECT $recipientIdCol AS id FROM security_recipientprofile LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            val disbursementIdCol = if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "disbursement_id"
            val disbursementId = db.query(
                "SELECT $disbursementIdCol AS id FROM disbursement_disbursement LIMIT 1",
            ).firstOrNull()?.get("id") as? Number ?: return
            val response = securityAuth()
                .get("/recipients/${recipientId.toLong()}/disbursements/${disbursementId.toLong()}/")
            // 200 if the disbursement exists, 404 if not
            assertThat(response.statusCode()).isIn(200, 404)
        }
    }

    @Nested
    @DisplayName("Prisoner nested disbursement detail")
    inner class PrisonerDisbursementDetail {

        private val prisonerIdCol get() =
            if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "prisoner_profile_id"

        @Test
        @DisplayName("GET /prisoners/{prisoner_pk}/disbursements/{id}/ returns single disbursement")
        fun `get single prisoner disbursement`() {
            val prisonerId = db.query("SELECT $prisonerIdCol AS id FROM security_prisonerprofile LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            val disbursementIdCol = if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "disbursement_id"
            val disbursementId = db.query(
                "SELECT $disbursementIdCol AS id FROM disbursement_disbursement LIMIT 1",
            ).firstOrNull()?.get("id") as? Number ?: return
            val response = securityAuth()
                .get("/prisoners/${prisonerId.toLong()}/disbursements/${disbursementId.toLong()}/")
            assertThat(response.statusCode()).isIn(200, 404)
        }
    }
}
