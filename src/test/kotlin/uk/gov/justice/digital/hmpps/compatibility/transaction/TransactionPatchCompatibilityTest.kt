package uk.gov.justice.digital.hmpps.compatibility.transaction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.config.ApiTarget
import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver

@Tag("transaction-patch")
@DisplayName("Transaction PATCH Compatibility")
class TransactionPatchCompatibilityTest : CompatibilityTestBase() {

    private fun bankAdminAuth() = ApiClient.authenticatedAs("test-token-bank-admin")
    private val txnIdCol get() = "id"

    @Nested
    @DisplayName("PATCH /transactions/ (bulk refund)")
    inner class BulkRefund {

        @Test
        @DisplayName("PATCH with empty array returns 200")
        fun `empty patch`() {
            bankAdminAuth()
                .body(emptyList<Any>())
                .patch(EndpointResolver.transactions())
                .then()
                .statusCode(200)
        }

        @Test
        @DisplayName("PATCH with refund flag on valid transaction")
        fun `refund transaction`() {
            val rows = db.query("SELECT $txnIdCol AS id FROM transaction_transaction LIMIT 1")
            if (rows.isEmpty()) return
            val id = (rows[0]["id"] as Number).toLong()

            val response = bankAdminAuth()
                .body(listOf(mapOf("id" to id, "refunded" to true)))
                .patch(EndpointResolver.transactions())

            // 200 (success with updated list) or 409 (conflict if credit not refundable)
            assertThat(response.statusCode()).isIn(200, 409)
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("unauthenticated PATCH returns 401")
        fun `patch without auth`() {
            ApiClient.unauthenticated()
                .body(emptyList<Any>())
                .patch(EndpointResolver.transactions())
                .then()
                .statusCode(401)
        }
    }
}
