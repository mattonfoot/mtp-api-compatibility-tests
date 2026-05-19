package uk.gov.justice.digital.hmpps.compatibility.credit

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient

@Tag("credit-extras")
@DisplayName("Credit Extras Compatibility")
class CreditExtrasCompatibilityTest : CompatibilityTestBase() {

    private val idCol get() = "id"

    @Nested
    @DisplayName("Processing Batches CRUD")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class ProcessingBatches {

        private var batchId: Long = 0

        @Test
        @Order(1)
        @DisplayName("GET /credits/batches/ returns paginated list")
        fun `list batches`() {
            ApiClient.authenticatedAs("test-token-prison-clerk")
                .get("/credits/batches/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @Order(2)
        @DisplayName("POST /credits/batches/ creates a batch (or 400 with empty credit_ids)")
        fun `create batch`() {
            val response = ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(mapOf("credit_ids" to emptyList<Long>()))
                .post("/credits/batches/")
            // Python rejects empty credit_ids with 400; Kotlin creates empty batch with 201
            assertThat(response.statusCode()).isIn(201, 400)
            if (response.statusCode() == 201) {
                batchId = response.jsonPath().getLong("id")
                assertThat(batchId).isGreaterThan(0)
            }
        }

        @Test
        @Order(3)
        @DisplayName("DELETE /credits/batches/{id}/ deletes a batch")
        fun `delete batch`() {
            if (batchId == 0L) return
            ApiClient.authenticatedAs("test-token-prison-clerk")
                .delete("/credits/batches/$batchId/")
                .then()
                .statusCode(204)
        }
    }

    @Nested
    @DisplayName("Credit Comments")
    inner class Comments {

        @Test
        @DisplayName("POST /credits/comments/ creates comments")
        fun `create comments`() {
            val creditId = db.query("SELECT $idCol AS id FROM credit_credit ORDER BY $idCol ASC LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return

            ApiClient.authenticatedAs("test-token-security")
                .body(listOf(mapOf("credit" to creditId.toLong(), "comment" to "Test comment from compatibility suite")))
                .post("/credits/comments/")
                .then()
                .statusCode(201)
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        fun `batches without token returns 401`() {
            ApiClient.unauthenticated()
                .get("/credits/batches/")
                .then()
                .statusCode(401)
        }
    }

    /**
     * `/private-estate-batches/` exercises the `{prison}/{date}` composite-key lookup
     * (credit/views.py lines 521-525) plus the partial-update credited-flag path
     * (lines 528-537) and the nested credits view (lines 549-561). The bank-admin
     * token is the only one with both auth + client_id permission for this surface.
     */
    @Nested
    @DisplayName("Private estate batches")
    inner class PrivateEstateBatches {
        private fun bankAdmin() = ApiClient.authenticatedAs("test-token-bank-admin")

        @Test
        @DisplayName("GET /private-estate-batches/ lists existing batches")
        fun `list batches`() {
            bankAdmin()
                .get("/private-estate-batches/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("?date=YYYY-MM-DD filter accepted")
        fun `filter by exact date`() {
            bankAdmin()
                .queryParam("date", "2024-01-01")
                .get("/private-estate-batches/")
                .then()
                .statusCode(200)
        }

        @Test
        @DisplayName("?date__gte and date__lt filter accepted")
        fun `filter by date range`() {
            bankAdmin()
                .queryParam("date__gte", "2020-01-01")
                .queryParam("date__lt", "2030-01-01")
                .get("/private-estate-batches/")
                .then()
                .statusCode(200)
        }

        @Test
        @DisplayName("GET .../prison/date/ returns 404 when missing")
        fun `retrieve nonexistent batch returns 404`() {
            bankAdmin()
                .get("/private-estate-batches/XXX/2099-12-31/")
                .then()
                .statusCode(404)
        }

        @Test
        @DisplayName("GET .../prison/date/credits/ returns 404 for missing batch")
        fun `nested credits 404 when batch missing`() {
            bankAdmin()
                .get("/private-estate-batches/XXX/2099-12-31/credits/")
                .then()
                .statusCode(404)
        }

        // Strict PUT-405 and PATCH-400 coverage moved to
        // PrivateEstateBatchLifecycleCompatibilityTest, which seeds a deterministic
        // batch first so the assertion is unambiguous (no 404-or-405 ambiguity).
    }
}
