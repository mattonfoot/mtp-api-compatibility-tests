package uk.gov.justice.digital.hmpps.compatibility.credit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
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

/**
 * Lifecycle compat tests for `PrivateEstateBatchView` — covers `credit/views.py`
 * lines 504-561:
 *
 *   * `get_object`: composite `{prison}/{date}` lookup (lines 520-525)
 *   * `update`: partial-update credited flag (lines 527-537), including the
 *     405-for-PUT branch and the 400-for-missing-credited branch
 *   * `PrivateEstateBatchCreditsView.list`: nested credits endpoint (lines 548-561)
 *
 * Seeds a deterministic batch with a credit_pending credit so the success path
 * can be exercised end-to-end and verified at the DB level (resolution flips to
 * 'credited' and a Log row is created).
 */
@Tag("private-estate-batch-lifecycle")
@DisplayName("Private estate batch retrieve + PATCH credited lifecycle")
class PrivateEstateBatchLifecycleCompatibilityTest : CompatibilityTestBase() {

    private val prison = "IXB"
    private val batchDate = "2030-06-15"
    private val ref = "$prison/$batchDate"
    private var batchId: Long = 0
    private var creditId: Long = 0

    // The `update` action on PrivateEstateBatchView requires
    // `credit.change_privateestatebatch`, which no group has — only superuser
    // bypasses it. So the lifecycle test must use the admin-bank-admin token
    // (admin superuser running through the bank-admin OAuth application to also
    // satisfy `BankAdminClientIDPermissions`). The plain `test-token-bank-admin`
    // user only has `view_privateestatebatch`, so it can list but not PATCH.
    private fun bankAdmin() = ApiClient.authenticatedAs("test-token-admin-bank-admin")

    @BeforeAll
    fun seedBatch() {
        // Remove any pre-existing fixture from a prior run
        db.executeSql(
            """
            DELETE FROM credit_log WHERE credit_id IN (
              SELECT id FROM credit_credit WHERE prisoner_number = 'COMPAT01'
            )
            """.trimIndent(),
        )
        db.executeSql("DELETE FROM credit_credit WHERE prisoner_number = 'COMPAT01'")
        db.executeSql(
            """
            DELETE FROM credit_privateestatebatch
              WHERE prison_id = '$prison' AND date = '$batchDate'
            """.trimIndent(),
        )

        // Insert batch (created/modified NOT NULL on the Django dump)
        db.executeSql(
            """
            INSERT INTO credit_privateestatebatch (prison_id, date, created, modified)
              VALUES ('$prison', '$batchDate', NOW(), NOW())
            """.trimIndent(),
        )
        batchId = (
            db.query(
                "SELECT id FROM credit_privateestatebatch " +
                    "WHERE prison_id = '$prison' AND date = '$batchDate'",
            ).first()["id"] as Number
            ).toLong()

        // Insert a credit_pending credit attached to the batch.
        // credit_pending requires: blocked=false, prison IS NOT NULL,
        //                         resolution IN ('pending','manual').
        db.executeSql(
            """
            INSERT INTO credit_credit
              (created, modified, amount, prisoner_number, prisoner_name, prisoner_dob, received_at,
               prison_id, resolution, blocked, owner_id, reconciled,
               reviewed, is_counted_in_sender_profile_total,
               is_counted_in_prisoner_profile_total, private_estate_batch_id)
            VALUES
              (NOW(), NOW(), 1234, 'COMPAT01', 'Compat Test', '1980-01-01', '$batchDate 12:00:00+00',
               '$prison', 'pending', false, NULL, false,
               false, false, false, $batchId)
            """.trimIndent(),
        )
        creditId = (
            db.query("SELECT id FROM credit_credit WHERE prisoner_number = 'COMPAT01'")
                .first()["id"] as Number
            ).toLong()
    }

    @AfterAll
    fun tearDownBatch() {
        db.executeSql("DELETE FROM credit_log WHERE credit_id = $creditId")
        db.executeSql("DELETE FROM credit_credit WHERE id = $creditId")
        db.executeSql("DELETE FROM credit_privateestatebatch WHERE id = $batchId")
    }

    @Nested
    @DisplayName("Retrieve + Credits list")
    inner class RetrieveAndCredits {

        @Test
        @DisplayName("GET /private-estate-batches/{prison}/{date}/ returns the batch")
        fun `get batch by composite key`() {
            val response = bankAdmin().get("/private-estate-batches/$ref/")
            assertStatus(response, expected = 200)
            assertThat(response.jsonPath().getString("prison")).isEqualTo(prison)
            assertThat(response.jsonPath().getString("date")).isEqualTo(batchDate)
        }

        @Test
        @DisplayName("GET /private-estate-batches/{unknown_prison}/{date}/ returns 404")
        fun `unknown prison`() {
            val response = bankAdmin().get("/private-estate-batches/ZZZ/$batchDate/")
            assertStatus(response, expected = 404)
        }

        @Test
        @DisplayName("GET /private-estate-batches/{prison}/{unknown_date}/ returns 404")
        fun `unknown date`() {
            val response = bankAdmin().get("/private-estate-batches/$prison/2099-12-31/")
            assertStatus(response, expected = 404)
        }

        @Test
        @DisplayName("GET nested credits returns the credits in the batch")
        fun `list credits in batch`() {
            val response = bankAdmin().get("/private-estate-batches/$ref/credits/")
            assertStatus(response, expected = 200)
            // Response shape can be either a paginated `{count, results}` envelope (Python's
            // default DRF pagination) or a plain list (Kotlin's current shape). Assert only
            // that at least one credit is present in either form — body-shape parity is a
            // separate cross-cutting concern tracked elsewhere.
            val body = response.body.asString()
            val hasCredit = body.contains("\"prisoner_number\"") || body.contains("\"prisonerNumber\"")
            assertThat(hasCredit)
                .withFailMessage("Expected at least one credit in batch response, got: %s", body)
                .isTrue()
        }
    }

    @Nested
    @DisplayName("PATCH lifecycle")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class PatchLifecycle {

        @Test
        @Order(1)
        @DisplayName("PATCH without credited body returns 400")
        fun `patch missing credited`() {
            val response = bankAdmin()
                .body(emptyMap<String, Any>())
                .patch("/private-estate-batches/$ref/")
            assertStatus(response, expected = 400)
        }

        @Test
        @Order(2)
        @DisplayName("PATCH with credited=false returns 400")
        fun `patch credited false`() {
            val response = bankAdmin()
                .body(mapOf("credited" to false))
                .patch("/private-estate-batches/$ref/")
            assertStatus(response, expected = 400)
        }

        @Test
        @Order(3)
        @DisplayName("PUT returns 405 (partial-only)")
        fun `put method not allowed`() {
            val response = bankAdmin()
                .body(mapOf("credited" to true))
                .put("/private-estate-batches/$ref/")
            assertStatus(response, expected = 405)
        }

        @Test
        @Order(4)
        @DisplayName("PATCH with credited=true returns 204 and credits the pending credits")
        fun `patch credited true`() {
            val response = bankAdmin()
                .body(mapOf("credited" to true))
                .patch("/private-estate-batches/$ref/")
            assertStatus(response, expected = 204)

            // DB-level verification: pending credit should now be credited.
            val row = db.query("SELECT resolution FROM credit_credit WHERE id = $creditId").first()
            assertThat(row["resolution"]).isEqualTo("credited")

            // A CREDITED log entry should now exist
            val logs = db.query(
                "SELECT action FROM credit_log WHERE credit_id = $creditId AND action = 'credited'",
            )
            assertThat(logs).isNotEmpty()
        }

        @Test
        @Order(5)
        @DisplayName("PATCH credited=true again is a no-op but still returns 204")
        fun `patch credited true idempotent`() {
            val response = bankAdmin()
                .body(mapOf("credited" to true))
                .patch("/private-estate-batches/$ref/")
            assertStatus(response, expected = 204)
        }
    }
}
