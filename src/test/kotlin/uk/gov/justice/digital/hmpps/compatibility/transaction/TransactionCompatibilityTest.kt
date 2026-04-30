package uk.gov.justice.digital.hmpps.compatibility.transaction

import org.assertj.core.api.Assertions.assertThat
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

@Tag("transactions")
@DisplayName("Transaction API Compatibility")
class TransactionCompatibilityTest : CompatibilityTestBase() {

    private fun bankAdminAuth() = ApiClient.authenticatedAs("test-token-bank-admin")

    @Nested
    @DisplayName("GET /transactions/")
    inner class ListTransactions {

        @Test
        @DisplayName("returns paginated list of transactions")
        fun `list transactions`() {
            bankAdminAuth()
                .get(EndpointResolver.transactions())
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("response has pagination envelope")
        fun `response has count and results`() {
            val json = bankAdminAuth()
                .get(EndpointResolver.transactions())
                .jsonPath()
            assertThat(json.getMap<String, Any>("")).containsKeys("count", "results")
        }
    }

    @Nested
    @DisplayName("POST /transactions/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class CreateTransaction {

        @Test
        @Order(1)
        @DisplayName("creates a bank transfer transaction with credit - returns 201")
        fun `create transaction`() {
            val body = listOf(
                mapOf(
                    "amount" to 5000,
                    "category" to "credit",
                    "source" to "bank_transfer",
                    "received_at" to "2024-06-15T10:00:00Z",
                    "sender_sort_code" to "112233",
                    "sender_account_number" to "12345678",
                    "sender_name" to "Test Sender",
                    "prisoner_number" to "A1409AE",
                    "prisoner_dob" to "1990-01-15",
                    "ref_code" to "REF001",
                ),
            )

            bankAdminAuth()
                .body(body)
                .post(EndpointResolver.transactions())
                .then()
                .statusCode(201)
        }

        @Test
        @Order(2)
        @DisplayName("created transaction has expected fields")
        fun `transaction has correct fields`() {
            val json = bankAdminAuth()
                .get(EndpointResolver.transactions())
                .jsonPath()

            if (json.getInt("count") > 0) {
                val txn = json.getMap<String, Any>("results[0]")
                assertThat(txn).containsKeys("id", "amount", "category", "source", "received_at")
            }
        }
    }

    @Nested
    @DisplayName("POST /transactions/reconcile/")
    inner class Reconcile {

        @Test
        @DisplayName("reconcile with valid date range returns 201")
        fun `reconcile transactions`() {
            bankAdminAuth()
                .body(
                    mapOf(
                        "received_at__gte" to "2024-01-01T00:00:00Z",
                        "received_at__lt" to "2024-12-31T23:59:59Z",
                    ),
                )
                .post("/transactions/reconcile/")
                .then()
                .statusCode(201)
        }

        @Test
        @DisplayName("reconcile without required fields returns 400")
        fun `reconcile missing fields`() {
            bankAdminAuth()
                .body(mapOf("received_at__gte" to "2024-01-01T00:00:00Z"))
                .post("/transactions/reconcile/")
                .then()
                .statusCode(400)
        }
    }

    @Nested
    @DisplayName("GET /batches/")
    inner class ListBatches {

        @Test
        @DisplayName("returns paginated list of batches")
        fun `list batches`() {
            bankAdminAuth()
                .get("/batches/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("unauthenticated transaction list returns 401")
        fun `no token returns 401`() {
            ApiClient.unauthenticated()
                .get(EndpointResolver.transactions())
                .then()
                .statusCode(401)
        }

        @Test
        @DisplayName("unauthenticated payment list returns 401")
        fun `payments without token returns 401`() {
            ApiClient.unauthenticated()
                .get(EndpointResolver.payments())
                .then()
                .statusCode(401)
        }
    }
}
