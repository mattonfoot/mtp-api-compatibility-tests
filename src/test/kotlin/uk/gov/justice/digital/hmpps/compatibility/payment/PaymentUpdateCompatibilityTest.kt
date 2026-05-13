package uk.gov.justice.digital.hmpps.compatibility.payment

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.notNullValue
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
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver

@Tag("payment-update")
@DisplayName("Payment Update Compatibility")
class PaymentUpdateCompatibilityTest : CompatibilityTestBase() {

    private fun sendMoneyAuth() = ApiClient.authenticatedAs("test-token-send-money")

    @Nested
    @DisplayName("Payment retrieve and update")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class PaymentLifecycle {

        private var paymentUuid: String? = null

        @Test
        @Order(1)
        @DisplayName("create a payment for update tests")
        fun `create payment`() {
            val response = sendMoneyAuth()
                .body(mapOf("amount" to 3000, "prisoner_number" to "A1409AE", "prisoner_dob" to "1990-01-15"))
                .post(EndpointResolver.payments())
            response.then().statusCode(201)
            paymentUuid = response.jsonPath().getString("uuid")
            assertThat(paymentUuid).isNotNull
        }

        @Test
        @Order(2)
        @DisplayName("GET /payments/{uuid}/ retrieves the payment")
        fun `get payment by uuid`() {
            if (paymentUuid == null) return
            sendMoneyAuth()
                .get("${EndpointResolver.payments()}$paymentUuid/")
                .then()
                .statusCode(200)
                .body("uuid", notNullValue())
        }

        @Test
        @Order(3)
        @DisplayName("PATCH /payments/{uuid}/ updates the payment")
        fun `update payment`() {
            if (paymentUuid == null) return
            sendMoneyAuth()
                .body(mapOf("email" to "updated@example.com"))
                .patch("${EndpointResolver.payments()}$paymentUuid/")
                .then()
                .statusCode(200)
        }

        @Test
        @Order(4)
        @DisplayName("PATCH /payments/{uuid}/ on a non-pending payment returns 409 conflict")
        fun `patch non-pending returns 409`() {
            if (paymentUuid == null) return
            // Transition the payment out of pending state (uses the same PATCH endpoint).
            // `taken` is the post-pending state — once set, further mutations should 409.
            sendMoneyAuth()
                .body(
                    mapOf(
                        "status" to "taken",
                        "received_at" to "2024-01-01T00:00:00Z",
                        "email" to "moved@example.com",
                        "cardholder_name" to "Moved Payer",
                    ),
                )
                .patch("${EndpointResolver.payments()}$paymentUuid/")
            // Second PATCH on a non-pending payment must conflict.
            val response = sendMoneyAuth()
                .body(mapOf("email" to "later@example.com"))
                .patch("${EndpointResolver.payments()}$paymentUuid/")
            assertThat(response.statusCode())
                .withFailMessage("non-pending PATCH → %d: %s", response.statusCode(), response.body().asString())
                .isEqualTo(409)
        }
    }

    @Nested
    @DisplayName("PATCH not-found")
    inner class PatchNotFound {
        @Test
        @DisplayName("PATCH /payments/{unknown-uuid}/ returns 404")
        fun `patch unknown payment returns 404`() {
            sendMoneyAuth()
                .body(mapOf("email" to "noone@example.com"))
                .patch("${EndpointResolver.payments()}00000000-0000-0000-0000-000000000000/")
                .then()
                .statusCode(404)
        }
    }
}
