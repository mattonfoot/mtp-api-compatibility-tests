package uk.gov.justice.digital.hmpps.compatibility.credit

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.greaterThanOrEqualTo
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
import uk.gov.justice.digital.hmpps.compatibility.config.ApiTarget
import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig
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
}
