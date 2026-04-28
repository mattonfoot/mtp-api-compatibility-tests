package uk.gov.justice.digital.hmpps.compatibility.security

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
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver

@Tag("security-crud")
@DisplayName("Security CRUD Compatibility")
class SecurityCrudCompatibilityTest : CompatibilityTestBase() {

    private fun securityAuth() = ApiClient.authenticatedAs("test-token-security")
    private fun fiuAuth() = ApiClient.authenticatedAs("test-token-fiu")
    private val senderIdCol get() = if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "sender_profile_id"
    private val prisonerIdCol get() = if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "prisoner_profile_id"
    private val recipientIdCol get() = if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "recipient_profile_id"

    @Nested
    @DisplayName("Sender Profile detail + nested")
    inner class SenderDetail {

        @Test
        @DisplayName("GET /senders/{id}/ returns a single sender profile")
        fun `get sender by id`() {
            val id = db.query("SELECT $senderIdCol AS id FROM security_senderprofile LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            securityAuth()
                .get("${EndpointResolver.senders()}${id.toLong()}/")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
        }

        @Test
        @DisplayName("GET /senders/{sender_pk}/credits/ returns sender's credits")
        fun `get sender credits`() {
            val id = db.query("SELECT $senderIdCol AS id FROM security_senderprofile LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            securityAuth()
                .get("${EndpointResolver.senders()}${id.toLong()}/credits/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }
    }

    @Nested
    @DisplayName("Prisoner Profile detail + nested")
    inner class PrisonerDetail {

        @Test
        @DisplayName("GET /prisoners/{id}/ returns a single prisoner profile")
        fun `get prisoner by id`() {
            val id = db.query("SELECT $prisonerIdCol AS id FROM security_prisonerprofile LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            securityAuth()
                .get("${EndpointResolver.prisoners()}${id.toLong()}/")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
        }

        @Test
        @DisplayName("GET /prisoners/{prisoner_pk}/credits/ returns prisoner's credits")
        fun `get prisoner credits`() {
            val id = db.query("SELECT $prisonerIdCol AS id FROM security_prisonerprofile LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            securityAuth()
                .get("${EndpointResolver.prisoners()}${id.toLong()}/credits/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("GET /prisoners/{prisoner_pk}/disbursements/ returns prisoner's disbursements")
        fun `get prisoner disbursements`() {
            val id = db.query("SELECT $prisonerIdCol AS id FROM security_prisonerprofile LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            securityAuth()
                .get("${EndpointResolver.prisoners()}${id.toLong()}/disbursements/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }
    }

    @Nested
    @DisplayName("Recipient Profile detail")
    inner class RecipientDetail {

        @Test
        @DisplayName("GET /recipients/{id}/ returns a single recipient profile")
        fun `get recipient by id`() {
            val id = db.query("SELECT $recipientIdCol AS id FROM security_recipientprofile LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            securityAuth()
                .get("${EndpointResolver.recipients()}${id.toLong()}/")
                .then()
                .statusCode(200)
        }
    }

    @Nested
    @DisplayName("Monitored Email CRUD")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class MonitoredEmailCrud {

        @Test
        @Order(1)
        @DisplayName("POST /security/monitored-email-addresses/ creates keyword")
        fun `create monitored email`() {
            fiuAuth()
                .body(mapOf("keyword" to "testcompat"))
                .post("/security/monitored-email-addresses/")
                .then()
                .statusCode(201)
        }

        @Test
        @Order(2)
        @DisplayName("DELETE /security/monitored-email-addresses/{keyword}/ deletes keyword")
        fun `delete monitored email`() {
            fiuAuth()
                .delete("/security/monitored-email-addresses/testcompat/")
                .then()
                .statusCode(204)
        }
    }

    @Nested
    @DisplayName("Saved Searches CRUD")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class SavedSearches {

        private var searchId: Long = 0

        @Test
        @Order(1)
        @DisplayName("GET /searches/ returns paginated list")
        fun `list searches`() {
            securityAuth()
                .get("/searches/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @Order(2)
        @DisplayName("POST /searches/ creates a saved search")
        fun `create search`() {
            val response = securityAuth()
                .body(mapOf("description" to "Test search", "endpoint" to "/senders/", "filters" to "{}"))
                .post("/searches/")
            response.then().statusCode(201)
            searchId = response.jsonPath().getLong("id")
        }

        @Test
        @Order(3)
        @DisplayName("PATCH /searches/{id}/ updates a saved search")
        fun `update search`() {
            if (searchId == 0L) return
            securityAuth()
                .body(mapOf("description" to "Updated test search"))
                .patch("/searches/$searchId/")
                .then()
                .statusCode(200)
        }

        @Test
        @Order(4)
        @DisplayName("DELETE /searches/{id}/ deletes a saved search")
        fun `delete search`() {
            if (searchId == 0L) return
            securityAuth()
                .delete("/searches/$searchId/")
                .then()
                .statusCode(204)
        }
    }

    @Nested
    @DisplayName("Security Check assign")
    inner class CheckAssign {

        @Test
        @DisplayName("PATCH /security/checks/{id}/ assigns to user")
        fun `assign check`() {
            val checkIdCol = if (TestConfig.apiTarget == ApiTarget.PYTHON) "id" else "check_id"
            val id = db.query("SELECT $checkIdCol AS id FROM security_check LIMIT 1")
                .firstOrNull()?.get("id") as? Number ?: return
            fiuAuth()
                .body(mapOf("assigned_to" to 1))
                .patch("/security/checks/${id.toLong()}/")
                .then()
                .statusCode(200)
        }
    }
}
