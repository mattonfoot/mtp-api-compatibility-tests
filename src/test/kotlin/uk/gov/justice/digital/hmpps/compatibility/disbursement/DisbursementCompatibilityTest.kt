package uk.gov.justice.digital.hmpps.compatibility.disbursement

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
import uk.gov.justice.digital.hmpps.compatibility.config.ApiTarget
import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver

@Tag("disbursements")
@DisplayName("Disbursement API Compatibility")
class DisbursementCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("GET /disbursements/")
    inner class ListDisbursements {

        @Test
        @DisplayName("returns paginated list of disbursements")
        fun `list disbursements`() {
            ApiClient.authenticated()
                .get(EndpointResolver.disbursements())
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("disbursement response has expected field names")
        fun `response fields`() {
            val json = ApiClient.authenticated()
                .get(EndpointResolver.disbursements())
                .jsonPath()
            val count = json.getInt("count")
            if (count > 0) {
                val disbursement = json.getMap<String, Any>("results[0]")
                assertThat(disbursement).containsKeys(
                    "id", "amount", "method", "prisoner_number", "prisoner_name", "resolution",
                )
            }
        }
    }

    @Nested
    @DisplayName("POST /disbursements/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class CreateDisbursement {

        @Test
        @Order(1)
        @DisplayName("creates a disbursement and returns 201")
        fun `create disbursement`() {
            // Django validates against prison_prisonerlocation. Earlier mutating
            // runs may have wiped the seed *or* left duplicate rows; ensure
            // exactly one active row exists so Python's single-record lookup
            // doesn't 500.
            db.executeSql("DELETE FROM prison_prisonerlocation WHERE prisoner_number = 'A1409AE'")
            db.executeSql(
                """
                INSERT INTO prison_prisonerlocation
                  (created, modified, prisoner_number, prisoner_dob, prisoner_name, prison_id, active)
                VALUES
                  (NOW(), NOW(), 'A1409AE', '1990-01-15', 'Compat Disbursement', '${existingPrisonId()}', true)
                """.trimIndent(),
            )
            val prisonerNumber = db.query(
                "SELECT prisoner_number FROM prison_prisonerlocation WHERE prison_id = '${existingPrisonId()}' AND active = true LIMIT 1",
            ).firstOrNull()?.get("prisoner_number") as? String ?: "A1409AE"

            ApiClient.authenticatedAs("test-token-prison-clerk")
                .body(
                    mapOf(
                        "amount" to 5000,
                        "method" to "bank_transfer",
                        "prison" to existingPrisonId(),
                        "prisoner_number" to prisonerNumber,
                        "prisoner_name" to "Test Prisoner",
                        "recipient_first_name" to "Bob",
                        "recipient_last_name" to "Wilson",
                        "sort_code" to "112233",
                        "account_number" to "12345678",
                        "address_line1" to "2 Main St",
                        "city" to "Leeds",
                        "postcode" to "LS1 1AA",
                        "country" to "UK",
                    ),
                )
                .post(EndpointResolver.disbursements())
                .then()
                .statusCode(201)
                .body("amount", equalTo(5000))
                .body("prisoner_number", equalTo(prisonerNumber))
                .body("resolution", equalTo("pending"))
                .body("id", notNullValue())
        }

        @Test
        @Order(2)
        @DisplayName("created disbursement exists in database")
        fun `disbursement persisted to database`() {
            val rows = db.query("SELECT * FROM disbursement_disbursement WHERE amount = 5000 ORDER BY created DESC LIMIT 1")
            assertThat(rows).hasSize(1)
            assertThat((rows[0]["amount"] as Number).toLong()).isEqualTo(5000)
        }
    }

    @Nested
    @DisplayName("GET /disbursements/{id}/")
    inner class GetDisbursement {

        @Test
        @DisplayName("retrieve a single disbursement by ID")
        fun `get disbursement`() {
            val idCol = "id"
            val rows = db.query("SELECT $idCol AS id FROM disbursement_disbursement LIMIT 1")
            if (rows.isNotEmpty()) {
                val id = (rows[0]["id"] as Number).toLong()
                ApiClient.authenticated()
                    .get("${EndpointResolver.disbursements()}$id/")
                    .then()
                    .statusCode(200)
            }
        }
    }

    @Nested
    @DisplayName("POST /disbursements/comments/")
    inner class Comments {

        @Test
        @DisplayName("create a comment on a disbursement returns 201")
        fun `create comment`() {
            val idCol = "id"
            val rows = db.query("SELECT $idCol AS id FROM disbursement_disbursement LIMIT 1")
            if (rows.isNotEmpty()) {
                val id = (rows[0]["id"] as Number).toLong()
                ApiClient.authenticatedAs("test-token-prison-clerk")
                    .body(listOf(mapOf("disbursement" to id, "comment" to "Test comment")))
                    .post("${EndpointResolver.disbursements()}comments/")
                    .then()
                    .statusCode(201)
            }
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        fun `unauthenticated request returns 401`() {
            ApiClient.unauthenticated()
                .get(EndpointResolver.disbursements())
                .then()
                .statusCode(401)
        }
    }
}
