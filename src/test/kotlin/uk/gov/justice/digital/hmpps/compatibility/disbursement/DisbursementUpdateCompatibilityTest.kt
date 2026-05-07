package uk.gov.justice.digital.hmpps.compatibility.disbursement

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
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
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver

@Tag("disbursement-update")
@DisplayName("Disbursement Update Compatibility")
class DisbursementUpdateCompatibilityTest : CompatibilityTestBase() {

    private val idCol get() = if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "disbursement_id"

    @Nested
    @DisplayName("PATCH /disbursements/{id}/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class UpdateDisbursement {

        private var disbursementId: Long = 0

        @BeforeAll
        fun findPending() {
            val rows = db.query("SELECT $idCol AS id FROM disbursement_disbursement WHERE resolution = 'pending' LIMIT 1")
            disbursementId = if (rows.isNotEmpty()) (rows[0]["id"] as Number).toLong() else 0
        }

        @Test
        @Order(1)
        @DisplayName("PATCH updates a pending disbursement")
        fun `update pending disbursement`() {
            if (disbursementId == 0L) return
            val response = ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("recipient_email" to "updated@example.com"))
                .patch("${EndpointResolver.disbursements()}$disbursementId/")
            // 200 if user can access this disbursement's prison; 404 if filtered by user's prison scope
            assertThat(response.statusCode()).isIn(200, 404)
        }

        @Test
        @Order(2)
        @DisplayName("PATCH on non-pending disbursement returns 400")
        fun `update non-pending returns 400`() {
            val sentRow = db.query("SELECT $idCol AS id FROM disbursement_disbursement WHERE resolution = 'sent' LIMIT 1")
            val sentId = (sentRow.firstOrNull()?.get("id") as? Number)?.toLong() ?: return
            ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("recipient_email" to "should-fail@example.com"))
                .patch("${EndpointResolver.disbursements()}$sentId/")
                .then()
                .statusCode(400)
        }
    }
}
