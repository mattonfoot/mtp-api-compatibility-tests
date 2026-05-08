package uk.gov.justice.digital.hmpps.compatibility.security

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

@Tag("security-actions")
@DisplayName("Security Actions Compatibility")
class SecurityActionsCompatibilityTest : CompatibilityTestBase() {

    private fun fiuAuth() = ApiClient.authenticatedAs("test-token-fiu")
    private fun securityAuth() = ApiClient.authenticatedAs("test-token-security")

    private val checkIdCol get() = "id"

    private fun findPendingCheckId(): Long? {
        val rows = db.query("SELECT $checkIdCol AS id FROM security_check WHERE status = 'pending' LIMIT 1")
        return if (rows.isNotEmpty()) (rows[0]["id"] as Number).toLong() else null
    }

    @Nested
    @DisplayName("POST /security/checks/{id}/accept/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class AcceptCheck {

        private var checkId: Long = 0

        @BeforeAll
        fun findCheck() {
            checkId = findPendingCheckId() ?: return
        }

        @Test
        @Order(1)
        @DisplayName("accept a pending check returns 204")
        fun `accept check`() {
            if (checkId == 0L) return // Skip if no pending checks
            fiuAuth()
                .body(mapOf("decision_reason" to "Looks fine"))
                .post("/security/checks/$checkId/accept/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(2)
        @DisplayName("check status is accepted in database")
        fun `status changed`() {
            if (checkId == 0L) return
            val rows = db.query("SELECT status FROM security_check WHERE $checkIdCol = $checkId")
            assertThat(rows[0]["status"]).isEqualTo("accepted")
        }
    }

    @Nested
    @DisplayName("POST /security/checks/{id}/reject/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class RejectCheck {

        private var checkId: Long = 0

        @BeforeAll
        fun findCheck() {
            checkId = findPendingCheckId() ?: return
        }

        @Test
        @Order(1)
        @DisplayName("reject a pending check returns 204")
        fun `reject check`() {
            if (checkId == 0L) return
            fiuAuth()
                .body(
                    mapOf(
                        "decision_reason" to "Suspicious activity",
                        "rejection_reasons" to mapOf("payment_source_linked_other_prisoners" to true),
                    ),
                )
                .post("/security/checks/$checkId/reject/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(2)
        @DisplayName("check status is rejected in database")
        fun `status changed`() {
            if (checkId == 0L) return
            val rows = db.query("SELECT status FROM security_check WHERE $checkIdCol = $checkId")
            assertThat(rows[0]["status"]).isEqualTo("rejected")
        }
    }

    @Nested
    @DisplayName("Monitor/Unmonitor sender profiles")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class MonitorSender {

        private var senderId: Long = 0

        @BeforeAll
        fun findSender() {
            val idCol = "id"
            val rows = db.query("SELECT $idCol AS id FROM security_senderprofile LIMIT 1")
            senderId = if (rows.isNotEmpty()) (rows[0]["id"] as Number).toLong() else return
        }

        @Test
        @Order(1)
        @DisplayName("monitor a sender profile returns 204")
        fun `monitor sender`() {
            if (senderId == 0L) return
            securityAuth()
                .post("${EndpointResolver.senders()}$senderId/monitor/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(2)
        @DisplayName("unmonitor a sender profile returns 204")
        fun `unmonitor sender`() {
            if (senderId == 0L) return
            securityAuth()
                .post("${EndpointResolver.senders()}$senderId/unmonitor/")
                .then()
                .statusCode(204)
        }
    }

    @Nested
    @DisplayName("Monitor/Unmonitor prisoner profiles")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class MonitorPrisoner {

        private var prisonerId: Long = 0

        @BeforeAll
        fun findPrisoner() {
            val idCol = "id"
            val rows = db.query("SELECT $idCol AS id FROM security_prisonerprofile LIMIT 1")
            prisonerId = if (rows.isNotEmpty()) (rows[0]["id"] as Number).toLong() else return
        }

        @Test
        @Order(1)
        @DisplayName("monitor a prisoner profile returns 204")
        fun `monitor prisoner`() {
            if (prisonerId == 0L) return
            securityAuth()
                .post("${EndpointResolver.prisoners()}$prisonerId/monitor/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(2)
        @DisplayName("unmonitor a prisoner profile returns 204")
        fun `unmonitor prisoner`() {
            if (prisonerId == 0L) return
            securityAuth()
                .post("${EndpointResolver.prisoners()}$prisonerId/unmonitor/")
                .then()
                .statusCode(204)
        }
    }
}
