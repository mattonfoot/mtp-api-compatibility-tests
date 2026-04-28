package uk.gov.justice.digital.hmpps.compatibility.notification

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

@Tag("notifications")
@DisplayName("Notification API Compatibility")
class NotificationCompatibilityTest : CompatibilityTestBase() {

    private fun nomsOpsAuth() = ApiClient.authenticatedAs("test-token-security")

    @Nested
    @DisplayName("GET /events/")
    inner class ListEvents {

        @Test
        @DisplayName("returns paginated list of events")
        fun `list events`() {
            nomsOpsAuth()
                .get("/events/")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0))
        }

        @Test
        @DisplayName("response has pagination envelope")
        fun `response has count and results`() {
            val json = nomsOpsAuth().get("/events/").jsonPath()
            assertThat(json.getMap<String, Any>("")).containsKeys("count", "results")
        }

        @Test
        @DisplayName("filter by rule code")
        fun `filter by rule`() {
            nomsOpsAuth()
                .queryParam("rule", "MONP")
                .get("/events/")
                .then()
                .statusCode(200)
        }
    }

    @Nested
    @DisplayName("GET /events/pages/")
    inner class EventPages {

        @Test
        @DisplayName("returns event page dates")
        fun `event pages`() {
            nomsOpsAuth()
                .get("/events/pages/")
                .then()
                .statusCode(200)
        }
    }

    @Nested
    @DisplayName("GET /rules/")
    inner class ListRules {

        @Test
        @DisplayName("returns list of enabled notification rules")
        fun `list rules`() {
            val json = nomsOpsAuth().get("/rules/").jsonPath()
            assertThat(json.getMap<String, Any>("")).containsKeys("count", "results")
            assertThat(json.getInt("count")).isGreaterThanOrEqualTo(1)
        }

        @Test
        @DisplayName("each rule has code and description")
        fun `rule has fields`() {
            val rules = nomsOpsAuth().get("/rules/").jsonPath().getList<Map<String, Any>>("results")
            if (rules.isNotEmpty()) {
                assertThat(rules[0]).containsKeys("code", "description")
            }
        }
    }

    @Nested
    @DisplayName("Email Preferences")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class EmailPreferences {

        @Test
        @Order(1)
        @DisplayName("GET returns email preferences for authenticated user")
        fun `get email preferences`() {
            nomsOpsAuth()
                .get("/emailpreferences/")
                .then()
                .statusCode(200)
        }

        @Test
        @Order(2)
        @DisplayName("POST sets email frequency - returns 204")
        fun `set email preferences`() {
            nomsOpsAuth()
                .body(mapOf("frequency" to "weekly"))
                .post("/emailpreferences/")
                .then()
                .statusCode(204)
        }

        @Test
        @Order(3)
        @DisplayName("GET returns updated frequency")
        fun `get updated preferences`() {
            val json = nomsOpsAuth().get("/emailpreferences/").jsonPath()
            assertThat(json.getString("frequency")).isEqualTo("weekly")
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        @DisplayName("unauthenticated events returns 401")
        fun `events without token`() {
            ApiClient.unauthenticated()
                .get("/events/")
                .then()
                .statusCode(401)
        }

        @Test
        @DisplayName("unauthenticated rules returns 401")
        fun `rules without token`() {
            ApiClient.unauthenticated()
                .get("/rules/")
                .then()
                .statusCode(401)
        }
    }
}
