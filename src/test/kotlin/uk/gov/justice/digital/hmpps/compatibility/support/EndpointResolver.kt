package uk.gov.justice.digital.hmpps.compatibility.support

import uk.gov.justice.digital.hmpps.compatibility.config.ApiTarget
import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig

/**
 * Resolves endpoint URL differences between the Python (canonical) and Kotlin APIs.
 *
 * The Python API is the reference implementation. Where the Kotlin API has different
 * URL paths, this resolver maps to the correct path per target. The test descriptions
 * always use the Python (canonical) paths.
 */
object EndpointResolver {

    fun healthCheck(): String = when (TestConfig.apiTarget) {
        ApiTarget.PYTHON -> "/ping.json"
        ApiTarget.KOTLIN -> "/health/ping"
    }

    fun senders(): String = "/senders/"
    fun prisoners(): String = "/prisoners/"
    fun recipients(): String = "/recipients/"

    // Endpoints with identical paths on both targets
    fun balances(): String = "/balances/"
    fun credits(): String = "/credits/"
    fun prisons(): String = "/prisons/"
    fun prisonCategories(): String = "/prison_categories/"
    fun prisonPopulations(): String = "/prison_populations/"
    fun disbursements(): String = "/disbursements/"
    fun securityChecks(): String = "/security/checks/"
    fun transactions(): String = "/transactions/"
    fun payments(): String = "/payments/"

    // Database table name differences
    fun balancesTable(): String = when (TestConfig.apiTarget) {
        ApiTarget.PYTHON -> "account_balance"
        ApiTarget.KOTLIN -> "balances"
    }

    /** Credit FK column name: Django uses prison_id, Kotlin dump uses prison_id (after V4 migration) */
    fun creditPrisonColumn(): String = "prison_id"

    /**
     * Generates INSERT SQL for credit_credit that works on both schemas.
     * Django has is_counted_in_sender/prisoner_profile_total; Kotlin does not.
     */
    fun creditInsertSql(
        amount: Long,
        prisonerNumber: String,
        prisonerName: String,
        prisonId: String,
        resolution: String = "credited",
    ): String = when (TestConfig.apiTarget) {
        ApiTarget.PYTHON -> """
            INSERT INTO credit_credit (
                amount, prisoner_number, prisoner_name, prison_id,
                resolution, reconciled, reviewed, blocked,
                is_counted_in_sender_profile_total, is_counted_in_prisoner_profile_total,
                created, modified
            ) VALUES (
                $amount, '$prisonerNumber', '$prisonerName', '$prisonId',
                '$resolution', false, false, false,
                false, false,
                NOW(), NOW()
            )
        """.trimIndent()
        ApiTarget.KOTLIN -> """
            INSERT INTO credit_credit (
                amount, prisoner_number, prisoner_name, prison_id,
                resolution, reconciled, reviewed, blocked,
                source, incomplete_sender_info,
                created, modified
            ) VALUES (
                $amount, '$prisonerNumber', '$prisonerName', '$prisonId',
                '$resolution', false, false, false,
                'online', false,
                NOW(), NOW()
            )
        """.trimIndent()
    }
}
