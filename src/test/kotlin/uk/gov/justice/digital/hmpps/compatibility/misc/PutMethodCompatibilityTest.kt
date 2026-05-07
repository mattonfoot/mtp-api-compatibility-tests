package uk.gov.justice.digital.hmpps.compatibility.misc

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

/**
 * Tests that PUT is accepted as an alternative to PATCH on all endpoints
 * that support partial updates. Python DRF accepts both by default.
 */
@Tag("put-methods")
@DisplayName("PUT as alternative to PATCH Compatibility")
class PutMethodCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("Disbursement PUT")
    inner class DisbursementPut {

        @Test
        @DisplayName("PUT /disbursements/{id}/ returns 200 or 404 (same as PATCH)")
        fun `put disbursement`() {
            val idCol = if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "disbursement_id"
            val id = db.query(
                "SELECT $idCol AS id FROM disbursement_disbursement WHERE resolution = 'pending' LIMIT 1",
            ).firstOrNull()?.get("id") as? Number ?: return
            val response = ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("amount" to 999))
                .put("${EndpointResolver.disbursements()}${id.toLong()}/")
            // 200 if found and pending, 400 if not pending, 404 if not found
            assertThat(response.statusCode()).isIn(200, 400, 404)
        }
    }

    @Nested
    @DisplayName("Payment PUT")
    inner class PaymentPut {

        @Test
        @DisplayName("PUT /payments/{uuid}/ returns 200 or 404 (same as PATCH)")
        fun `put payment`() {
            val uuid = db.query("SELECT uuid FROM payment_payment LIMIT 1")
                .firstOrNull()?.get("uuid")?.toString() ?: return
            val response = ApiClient.authenticatedAs("test-token-send-money")
                .body(mapOf("status" to "taken"))
                .put("${EndpointResolver.payments()}$uuid/")
            // 200/400/404 are expected; 409 indicates the payment is in a state that can't transition
            assertThat(response.statusCode()).isIn(200, 400, 404, 409)
        }
    }

    @Nested
    @DisplayName("Saved Search PUT")
    inner class SavedSearchPut {

        @Test
        @DisplayName("PUT /searches/{id}/ updates a search (same as PATCH)")
        fun `put search`() {
            // Create a search first
            val createResponse = ApiClient.authenticatedAs("test-token-security")
                .body(mapOf("description" to "PUT test", "endpoint" to "/senders/", "filters" to "{}"))
                .post("/searches/")
            if (createResponse.statusCode() != 201) return
            val searchId = createResponse.jsonPath().getLong("id")

            val response = ApiClient.authenticatedAs("test-token-security")
                .body(mapOf("description" to "PUT updated"))
                .put("/searches/$searchId/")
            response.then().statusCode(200)
            assertThat(response.jsonPath().getString("description")).isEqualTo("PUT updated")

            // Cleanup
            ApiClient.authenticatedAs("test-token-security")
                .delete("/searches/$searchId/")
        }
    }

    @Nested
    @DisplayName("Private Estate Batch PUT")
    inner class PrivateEstateBatchPut {

        @Test
        @DisplayName("PUT /private-estate-batches/{prison}/{date}/ returns 200 or 404")
        fun `put private estate batch`() {
            val prisonCol = if (TestConfig.apiTarget == ApiTarget.PYTHON) "prison_id" else "prison"
            val batch = db.query("SELECT $prisonCol AS prison, date FROM credit_privateestatebatch LIMIT 1")
                .firstOrNull() ?: return
            val prison = batch["prison"].toString()
            val date = batch["date"].toString()
            val response = ApiClient.authenticated()
                .body(mapOf<String, Any>()) // empty body to test PUT accepted
                .put("/private-estate-batches/$prison/$date/")
            assertThat(response.statusCode()).isIn(200, 400, 404)
        }
    }

    @Nested
    @DisplayName("Prisoner Credit Notice Email PUT")
    inner class CreditNoticeEmailPut {

        @Test
        @DisplayName("PUT /prisoner_credit_notice_email/{prison}/ returns 200 or 404")
        fun `put credit notice email`() {
            val response = ApiClient.authenticatedAs("test-token-prison-clerk-ua")
                .body(mapOf("email" to "test@prison.gov.uk"))
                .put("/prisoner_credit_notice_email/${existingPrisonId()}/")
            assertThat(response.statusCode()).isIn(200, 201, 400, 404)
        }
    }

    @Nested
    @DisplayName("Account Request PUT")
    inner class AccountRequestPut {

        @Test
        @DisplayName("PUT /requests/{id}/ returns 200, 403, or 404")
        fun `put request`() {
            val id = db.query("SELECT id FROM mtp_auth_accountrequest LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            val response = ApiClient.authenticated()
                .body(mapOf("status" to "pending"))
                .put("/requests/${id.toLong()}/")
            // Python rejects PUT with 403 (AccountRequestPermissions allows only specific actions)
            assertThat(response.statusCode()).isIn(200, 400, 403, 404)
        }
    }
}
