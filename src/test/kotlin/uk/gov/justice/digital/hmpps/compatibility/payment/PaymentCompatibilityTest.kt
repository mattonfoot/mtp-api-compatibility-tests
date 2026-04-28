package uk.gov.justice.digital.hmpps.compatibility.payment

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.notNullValue
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

@Tag("payments")
@DisplayName("Payment API Compatibility")
class PaymentCompatibilityTest : CompatibilityTestBase() {

    private fun sendMoneyAuth() = ApiClient.authenticatedAs("test-token-send-money")

    @Nested
    @DisplayName("POST /payments/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class CreatePayment {

        private var paymentUuid: String? = null

        @Test
        @Order(1)
        @DisplayName("creates a payment with associated credit - returns 201")
        fun `create payment`() {
            val body = mapOf(
                "amount" to 2500,
                "prisoner_number" to "A1409AE",
                "prisoner_dob" to "1990-01-15",
                "recipient_name" to "Test Recipient",
                "email" to "test@example.com",
            )

            val response = sendMoneyAuth()
                .body(body)
                .post(EndpointResolver.payments())

            response.then()
                .statusCode(201)
                .body("uuid", notNullValue())
                .body("amount", equalTo(2500))

            paymentUuid = response.jsonPath().getString("uuid")
        }

        @Test
        @Order(2)
        @DisplayName("response includes expected payment fields")
        fun `response has correct fields`() {
            val payment = sendMoneyAuth()
                .get(EndpointResolver.payments())
                .jsonPath()

            if (payment.getInt("count") > 0) {
                val result = payment.getMap<String, Any>("results[0]")
                assertThat(result).containsKeys("uuid", "amount", "status")
            }
        }
    }

    @Nested
    @DisplayName("GET /payments/")
    inner class ListPayments {

        @Test
        @DisplayName("returns paginated list of pending payments")
        fun `list payments`() {
            sendMoneyAuth()
                .get(EndpointResolver.payments())
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("response has pagination envelope")
        fun `response has count and results`() {
            val json = sendMoneyAuth()
                .get(EndpointResolver.payments())
                .jsonPath()
            assertThat(json.getMap<String, Any>("")).containsKeys("count", "results")
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("unauthenticated request returns 401")
        fun `no token returns 401`() {
            ApiClient.unauthenticated()
                .get(EndpointResolver.payments())
                .then()
                .statusCode(401)
        }
    }
}
