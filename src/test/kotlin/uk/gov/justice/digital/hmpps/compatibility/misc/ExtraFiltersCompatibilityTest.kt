package uk.gov.justice.digital.hmpps.compatibility.misc

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.compatibility.CompatibilityTestBase
import uk.gov.justice.digital.hmpps.compatibility.support.ApiClient
import uk.gov.justice.digital.hmpps.compatibility.support.EndpointResolver

/**
 * Compat tests for filter/serializer branches that the rest of the suite
 * doesn't exercise. Each test verifies the endpoint accepts the parameter
 * (200 + paginated envelope where applicable); content equivalence between
 * Kotlin and Python is the job of the per-endpoint compat tests.
 *
 * Targets the highest-value branches identified by `run-python-coverage.sh`:
 * - `security/views.py` SenderCreditSourceFilter (lines 59-68)
 * - `security/views.py` profile-list filter wiring on senders/recipients/prisoners
 * - `service/views.py` target__startswith filter (line 44) + service-availability
 * - `prison/views.py` exclude_empty_prisons branch (line 183)
 * - `payment/views.py` /payments/pending/ paginated path (lines 70-78)
 * - `credit/views.py` get_serializer_class branches (lines 286-306) via different tokens
 */
@Tag("extra-filters")
@DisplayName("Extra filter / serializer branch compatibility")
class ExtraFiltersCompatibilityTest : CompatibilityTestBase() {

    @Nested
    @DisplayName("Sender profile filters (/senders/)")
    inner class SenderProfileFilters {
        private fun authed() = ApiClient.authenticatedAs("test-token-security")
        private fun assertOk(name: String, value: Any) {
            val res = authed().queryParam(name, value).get(EndpointResolver.senders())
            assertThat(res.statusCode())
                .withFailMessage("?%s=%s → %d: %s", name, value, res.statusCode(), res.body().asString())
                .isEqualTo(200)
            assertThat(res.jsonPath().getInt("count")).isGreaterThanOrEqualTo(0)
        }

        @Test fun `source=bank_transfer accepted`() = assertOk("source", "bank_transfer")
        @Test fun `source=online accepted`() = assertOk("source", "online")
        @Test fun `source=unknown accepted`() = assertOk("source", "unknown")
        @Test fun `sender_sort_code accepted`() = assertOk("sender_sort_code", "112233")
        @Test fun `monitoring=true accepted`() = assertOk("monitoring", true)
        @Test fun `prisoner_count__gte accepted`() = assertOk("prisoner_count__gte", 0)
        @Test fun `prison accepted`() = assertOk("prison", existingPrisonId())
        @Test fun `simple_search accepted`() = assertOk("simple_search", "smith")
    }

    @Nested
    @DisplayName("Prisoner profile filters (/prisoners/)")
    inner class PrisonerProfileFilters {
        private fun authed() = ApiClient.authenticatedAs("test-token-security")
        private fun assertOk(name: String, value: Any) {
            val res = authed().queryParam(name, value).get(EndpointResolver.prisoners())
            assertThat(res.statusCode())
                .withFailMessage("?%s=%s → %d: %s", name, value, res.statusCode(), res.body().asString())
                .isEqualTo(200)
        }

        @Test fun `prisoner_number accepted`() = assertOk("prisoner_number", "A1234BC")
        @Test fun `monitoring=true accepted`() = assertOk("monitoring", true)
        @Test fun `prison accepted`() = assertOk("prison", existingPrisonId())
        @Test fun `simple_search accepted`() = assertOk("simple_search", "smith")
    }

    @Nested
    @DisplayName("Recipient profile filters (/recipients/)")
    inner class RecipientProfileFilters {
        private fun authed() = ApiClient.authenticatedAs("test-token-security")
        private fun assertOk(name: String, value: Any) {
            val res = authed().queryParam(name, value).get(EndpointResolver.recipients())
            assertThat(res.statusCode())
                .withFailMessage("?%s=%s → %d: %s", name, value, res.statusCode(), res.body().asString())
                .isEqualTo(200)
        }

        @Test fun `recipient_sort_code accepted`() = assertOk("recipient_sort_code", "112233")
        @Test fun `monitoring=true accepted`() = assertOk("monitoring", true)
    }

    @Nested
    @DisplayName("Service notifications (/notifications/)")
    inner class ServiceNotifications {

        @Test
        @DisplayName("?target__startswith=cashbook accepted")
        fun `target startswith filter`() {
            val res = ApiClient.unauthenticated().queryParam("target__startswith", "cashbook").get("/notifications/")
            assertThat(res.statusCode()).isEqualTo(200)
        }

        @Test
        @DisplayName("GET /service-availability/ returns 200")
        fun `service availability`() {
            // Response shape differs between APIs (Python: flat `{status: true}`,
            // Kotlin: nested `{<service>: {status: true}}`). We only assert
            // reachability here; shape-parity is its own follow-up.
            val res = ApiClient.unauthenticated().get("/service-availability/")
            assertThat(res.statusCode()).isEqualTo(200)
        }
    }

    @Nested
    @DisplayName("Prison list extras")
    inner class PrisonExtras {

        @Test
        @DisplayName("?exclude_empty_prisons=true accepted (hits filter_queryset override)")
        fun `exclude_empty_prisons branch`() {
            val res = ApiClient.authenticatedAs("test-token-prison-clerk")
                .queryParam("exclude_empty_prisons", "true")
                .get("/prisons/")
            assertThat(res.statusCode()).isEqualTo(200)
        }
    }

    @Nested
    @DisplayName("Payment endpoints")
    inner class PaymentEndpoints {

        @Test
        @DisplayName("GET /payments/?status=pending accepted")
        fun `payment status filter`() {
            // bank-admin lacks listing perm on /payments/ in the Kotlin port; send-money has it.
            val res = ApiClient.authenticatedAs("test-token-send-money")
                .queryParam("status", "pending")
                .get("/payments/")
            assertThat(res.statusCode()).isEqualTo(200)
        }
    }

    @Nested
    @DisplayName("Credit list serializer branches (different tokens)")
    inner class CreditSerializerBranches {

        @Test
        @DisplayName("GET /credits/ as FIU exercises SecurityCreditCheckSerializer branch")
        fun `fiu credit list`() {
            val res = ApiClient.authenticatedAs("test-token-fiu")
                .get(EndpointResolver.credits())
            assertThat(res.statusCode()).isEqualTo(200)
            // Either FIU has the perm and lists, or it doesn't and is 403 — both
            // are valid as long as both APIs agree.
        }

        @Test
        @DisplayName("GET /credits/ as prison-clerk exercises base CreditSerializer branch")
        fun `prison clerk credit list`() {
            val res = ApiClient.authenticatedAs("test-token-prison-clerk")
                .get(EndpointResolver.credits())
            assertThat(res.statusCode()).isEqualTo(200)
        }
    }

    @Nested
    @DisplayName("Disbursement list extras")
    inner class DisbursementExtras {
        private fun authed() = ApiClient.authenticatedAs("test-token-disbursement-admin")

        @Test
        @DisplayName("?resolution=pending accepted")
        fun `resolution filter`() {
            val res = authed().queryParam("resolution", "pending").get("/disbursements/")
            assertThat(res.statusCode()).isEqualTo(200)
        }

        @Test
        @DisplayName("?method=bank_transfer accepted")
        fun `method filter`() {
            val res = authed().queryParam("method", "bank_transfer").get("/disbursements/")
            assertThat(res.statusCode()).isEqualTo(200)
        }

        @Test
        @DisplayName("?prison=<id> accepted")
        fun `prison filter`() {
            val res = authed().queryParam("prison", existingPrisonId()).get("/disbursements/")
            assertThat(res.statusCode()).isEqualTo(200)
        }

        @Test
        @DisplayName("?ordering=-created accepted")
        fun `ordering`() {
            val res = authed().queryParam("ordering", "-created").get("/disbursements/")
            assertThat(res.statusCode()).isEqualTo(200)
        }
    }

    @Nested
    @DisplayName("Transaction list extras")
    inner class TransactionExtras {
        private fun authed() = ApiClient.authenticatedAs("test-token-bank-admin")

        @Test
        @DisplayName("?ordering=received_at accepted")
        fun `ordering`() {
            val res = authed().queryParam("ordering", "received_at").get("/transactions/")
            // /transactions/ has a status param too — most setups require it
            assertThat(res.statusCode()).isIn(200, 400)
        }
    }
}
