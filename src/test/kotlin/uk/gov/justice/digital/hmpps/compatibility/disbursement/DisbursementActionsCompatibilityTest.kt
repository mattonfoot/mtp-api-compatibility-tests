package uk.gov.justice.digital.hmpps.compatibility.disbursement

import org.assertj.core.api.Assertions.assertThat
import uk.gov.justice.digital.hmpps.compatibility.config.ApiTarget
import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("disbursement-actions")
@DisplayName("Disbursement Actions Compatibility")
class DisbursementActionsCompatibilityTest : CompatibilityTestBase() {

    private fun disbursementIdColumn(): String = "id"

    private fun seedDisbursement(prisonerNumber: String, resolution: String = "pending"): Long {
        val prison = existingPrisonId()
        val prisonCol = "prison_id"
        val extraCols = ", remittance_description"
        val extraVals = ", ''"
        db.executeSql(
            """
            INSERT INTO disbursement_disbursement (
                amount, method, $prisonCol, prisoner_number, prisoner_name,
                recipient_first_name, recipient_last_name, recipient_is_company,
                resolution, created, modified$extraCols
            ) VALUES (
                1000, 'bank_transfer', '$prison', '$prisonerNumber', 'Test Person',
                'Recipient', 'Name', false,
                '$resolution', NOW(), NOW()$extraVals
            )
            """.trimIndent(),
        )
        val rows = db.query("SELECT MAX(${disbursementIdColumn()}) AS id FROM disbursement_disbursement WHERE prisoner_number = '$prisonerNumber'")
        return (rows[0]["id"] as Number).toLong()
    }

    private fun getResolution(id: Long): String {
        val rows = db.query("SELECT resolution FROM disbursement_disbursement WHERE ${disbursementIdColumn()} = $id")
        return rows[0]["resolution"] as String
    }

    @Nested
    @DisplayName("POST /disbursements/actions/reject/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class Reject {

        private var pendingId: Long = 0

        @BeforeAll
        fun seed() {
            pendingId = seedDisbursement("RJ001AA", "pending")
        }

        @Test
        @Order(1)
        @DisplayName("transitions pending to rejected - returns 204")
        fun `reject pending disbursement`() {
            ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("disbursement_ids" to listOf(pendingId)))
                .post("/disbursements/actions/reject/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(2)
        @DisplayName("resolution is rejected in database")
        fun `resolution changed to rejected`() {
            assertThat(getResolution(pendingId)).isEqualTo("rejected")
        }
    }

    @Nested
    @DisplayName("POST /disbursements/actions/preconfirm/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class Preconfirm {

        private var pendingId: Long = 0

        @BeforeAll
        fun seed() {
            pendingId = seedDisbursement("PC001AA", "pending")
        }

        @Test
        @Order(1)
        @DisplayName("transitions pending to preconfirmed - returns 204")
        fun `preconfirm pending disbursement`() {
            ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("disbursement_ids" to listOf(pendingId)))
                .post("/disbursements/actions/preconfirm/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(2)
        @DisplayName("resolution is preconfirmed in database")
        fun `resolution changed to preconfirmed`() {
            assertThat(getResolution(pendingId)).isEqualTo("preconfirmed")
        }
    }

    @Nested
    @DisplayName("POST /disbursements/actions/confirm/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class Confirm {

        private var preconfirmedId: Long = 0

        @BeforeAll
        fun seed() {
            preconfirmedId = seedDisbursement("CF001AA", "preconfirmed")
        }

        @Test
        @Order(1)
        @DisplayName("transitions preconfirmed to confirmed - returns 204")
        fun `confirm preconfirmed disbursement`() {
            ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(listOf(mapOf("id" to preconfirmedId)))
                .post("/disbursements/actions/confirm/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(2)
        @DisplayName("resolution is confirmed in database")
        fun `resolution changed to confirmed`() {
            assertThat(getResolution(preconfirmedId)).isEqualTo("confirmed")
        }
    }

    @Nested
    @DisplayName("POST /disbursements/actions/send/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class Send {

        private var confirmedId: Long = 0

        @BeforeAll
        fun seed() {
            confirmedId = seedDisbursement("SN001AA", "confirmed")
        }

        @Test
        @Order(1)
        @DisplayName("transitions confirmed to sent - returns 204")
        fun `send confirmed disbursement`() {
            ApiClient.authenticatedAs("test-token-disbursement-admin")
                .body(mapOf("disbursement_ids" to listOf(confirmedId)))
                .post("/disbursements/actions/send/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(2)
        @DisplayName("resolution is sent in database")
        fun `resolution changed to sent`() {
            assertThat(getResolution(confirmedId)).isEqualTo("sent")
        }
    }

    @Nested
    @DisplayName("POST /disbursements/actions/reset/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class Reset {

        private var preconfirmedId: Long = 0

        @BeforeAll
        fun seed() {
            preconfirmedId = seedDisbursement("RS001AA", "preconfirmed")
        }

        @Test
        @Order(1)
        @DisplayName("transitions preconfirmed back to pending - returns 204")
        fun `reset preconfirmed disbursement`() {
            ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("disbursement_ids" to listOf(preconfirmedId)))
                .post("/disbursements/actions/reset/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(2)
        @DisplayName("resolution is pending in database")
        fun `resolution changed to pending`() {
            assertThat(getResolution(preconfirmedId)).isEqualTo("pending")
        }
    }

    @Nested
    @DisplayName("Invalid state transitions")
    inner class InvalidTransitions {

        @Test
        @DisplayName("reject on sent disbursement returns 409")
        fun `reject sent disbursement`() {
            val sentId = seedDisbursement("IT001AA", "sent")
            ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("disbursement_ids" to listOf(sentId)))
                .post("/disbursements/actions/reject/")
                .then()
                .statusCode(409)
        }

        @Test
        @DisplayName("send on pending disbursement returns 409")
        fun `send pending disbursement`() {
            val pendingId = seedDisbursement("IT002BB", "pending")
            ApiClient.authenticatedAs("test-token-disbursement-admin")
                .body(mapOf("disbursement_ids" to listOf(pendingId)))
                .post("/disbursements/actions/send/")
                .then()
                .statusCode(409)
        }
    }
}
