package uk.gov.justice.digital.hmpps.compatibility.balance

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
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

@Tag("balances")
@DisplayName("Balance API Compatibility")
class BalanceCompatibilityTest : CompatibilityTestBase() {

    private val table get() = EndpointResolver.balancesTable()

    @Nested
    @DisplayName("GET /balances/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class ListBalances {

        @BeforeAll
        fun cleanUp() {
            db.executeSql("DELETE FROM $table")
        }

        @Test
        @Order(1)
        @DisplayName("returns empty paginated response when no data")
        fun `empty list returns paginated envelope`() {
            ApiClient.authenticated()
                .get(EndpointResolver.balances())
                .then()
                .statusCode(200)
                .body("count", equalTo(0))
                .body("results", hasSize<Any>(0))
        }

        @Test
        @Order(2)
        @DisplayName("response envelope has count, next, previous, results fields")
        fun `response has Django REST Framework pagination shape`() {
            val json = ApiClient.authenticated()
                .get(EndpointResolver.balances())
                .jsonPath()

            assertThat(json.getMap<String, Any>("")).containsKeys("count", "results")
        }
    }

    @Nested
    @DisplayName("POST /balances/")
    @TestMethodOrder(MethodOrderer.OrderAnnotation::class)
    inner class CreateBalance {

        @BeforeAll
        fun cleanUp() {
            db.executeSql("DELETE FROM $table")
        }

        @Test
        @Order(1)
        @DisplayName("creates a balance and returns 201 with correct fields")
        fun `create balance returns 201`() {
            ApiClient.authenticatedAs("test-token-bank-admin")
                .body(mapOf("closing_balance" to 15000, "date" to "2024-01-15"))
                .post(EndpointResolver.balances())
                .then()
                .statusCode(201)
                .body("closing_balance", equalTo(15000))
                .body("date", equalTo("2024-01-15"))
                .body("id", notNullValue())
        }

        @Test
        @Order(2)
        @DisplayName("created balance exists in database")
        fun `balance persisted to database`() {
            val rows = db.query("SELECT * FROM $table WHERE date = '2024-01-15'")
            assertThat(rows).hasSize(1)
            assertThat((rows[0]["closing_balance"] as Number).toLong()).isEqualTo(15000)
        }

        @Test
        @Order(3)
        @DisplayName("response includes created and modified timestamps")
        fun `response has timestamp fields`() {
            val balance = ApiClient.authenticated()
                .get(EndpointResolver.balances())
                .jsonPath()
                .getMap<String, Any>("results[0]")

            assertThat(balance).containsKeys("id", "closing_balance", "date", "created", "modified")
        }

        @Test
        @Order(4)
        @DisplayName("duplicate date rejected with 400")
        fun `duplicate date returns 400`() {
            ApiClient.authenticatedAs("test-token-bank-admin")
                .body(mapOf("closing_balance" to 99999, "date" to "2024-01-15"))
                .post(EndpointResolver.balances())
                .then()
                .statusCode(400)
        }
    }

    @Nested
    @DisplayName("GET /balances/ filtering")
    inner class Filtering {

        @BeforeAll
        fun seedData() {
            db.executeSql("DELETE FROM $table")
            db.executeSql(
                """
                INSERT INTO $table (closing_balance, date, created, modified) VALUES
                    (1000, '2024-01-01', NOW(), NOW()),
                    (2000, '2024-01-15', NOW(), NOW()),
                    (3000, '2024-01-31', NOW(), NOW())
                """.trimIndent(),
            )
        }

        @Test
        fun `date__lt filters balances before given date`() {
            val json = ApiClient.authenticated()
                .queryParam("date__lt", "2024-01-20")
                .get(EndpointResolver.balances())
                .jsonPath()
            assertThat(json.getInt("count")).isEqualTo(2)
        }

        @Test
        fun `date__gte filters balances on or after given date`() {
            val json = ApiClient.authenticated()
                .queryParam("date__gte", "2024-01-15")
                .get(EndpointResolver.balances())
                .jsonPath()
            assertThat(json.getInt("count")).isEqualTo(2)
        }

        @Test
        fun `combined date filters`() {
            val json = ApiClient.authenticated()
                .queryParam("date__gte", "2024-01-10")
                .queryParam("date__lt", "2024-01-20")
                .get(EndpointResolver.balances())
                .jsonPath()
            assertThat(json.getInt("count")).isEqualTo(1)
        }

        @Test
        fun `default ordering is newest first`() {
            val dates = ApiClient.authenticated()
                .get(EndpointResolver.balances())
                .jsonPath()
                .getList<Map<String, Any>>("results")
                .map { it["date"] as String }
            assertThat(dates).containsExactly("2024-01-31", "2024-01-15", "2024-01-01")
        }
    }

    @Nested
    @DisplayName("Authentication")
    inner class Auth {

        @Test
        fun `unauthenticated request returns 401`() {
            ApiClient.unauthenticated()
                .get(EndpointResolver.balances())
                .then()
                .statusCode(401)
        }
    }
}
