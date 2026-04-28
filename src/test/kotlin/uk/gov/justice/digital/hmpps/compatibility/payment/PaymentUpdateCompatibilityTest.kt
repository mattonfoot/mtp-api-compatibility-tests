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
    }
}
