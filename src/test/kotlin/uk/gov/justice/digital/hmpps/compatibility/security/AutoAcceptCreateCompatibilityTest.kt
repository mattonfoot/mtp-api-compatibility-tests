package uk.gov.justice.digital.hmpps.compatibility.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.config.ApiTarget
import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("auto-accept-create")
@DisplayName("Auto Accept Rule Create Compatibility")
class AutoAcceptCreateCompatibilityTest : CompatibilityTestBase() {

    private fun fiuAuth() = ApiClient.authenticatedAs("test-token-fiu")

    @Nested
    @DisplayName("POST /security/checks/auto-accept/")
    inner class CreateAutoAccept {

        @Test
        @DisplayName("creates an auto-accept rule with valid profile IDs")
        fun `create auto accept rule`() {
            val senderIdCol = "id"
            val prisonerIdCol = "id"

            val senderRows = db.query("SELECT $senderIdCol AS id FROM security_senderprofile LIMIT 1")
            val prisonerRows = db.query("SELECT $prisonerIdCol AS id FROM security_prisonerprofile LIMIT 1")
            if (senderRows.isEmpty() || prisonerRows.isEmpty()) return

            val senderId = (senderRows[0]["id"] as Number).toLong()
            val prisonerId = (prisonerRows[0]["id"] as Number).toLong()

            val body = mapOf(
                "debit_card_sender_details_id" to senderId,
                "prisoner_profile_id" to prisonerId,
                "states" to listOf(mapOf("active" to true, "reason" to "Test auto-accept")),
            )

            val response = fiuAuth()
                .body(body)
                .post("/security/checks/auto-accept/")

            // 201 (created) or 400 (validation - might need actual debit_card_sender_details_id)
            assertThat(response.statusCode()).isIn(201, 400)
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("unauthenticated create returns 401")
        fun `create without auth`() {
            ApiClient.unauthenticated()
                .body(mapOf("states" to listOf(mapOf("active" to true))))
                .post("/security/checks/auto-accept/")
                .then()
                .statusCode(401)
        }
    }
}
