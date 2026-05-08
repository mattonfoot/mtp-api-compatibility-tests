package uk.gov.justice.digital.hmpps.compatibility.support

/**
 * Endpoint URLs and SQL fragments shared by Python and Kotlin tests.
 *
 * Now that both APIs are backed by the Django-shaped schema (Kotlin's V1
 * Flyway migration is the Django pg_dump), there are no schema-level
 * differences left to bridge. The only thing that diverges is the health
 * check path — Spring Boot Actuator vs Django's `/ping.json`.
 */
object EndpointResolver {

    fun healthCheck(): String = "/ping.json"

    fun senders(): String = "/senders/"
    fun prisoners(): String = "/prisoners/"
    fun recipients(): String = "/recipients/"

    fun balances(): String = "/balances/"
    fun credits(): String = "/credits/"
    fun prisons(): String = "/prisons/"
    fun prisonCategories(): String = "/prison_categories/"
    fun prisonPopulations(): String = "/prison_populations/"
    fun disbursements(): String = "/disbursements/"
    fun securityChecks(): String = "/security/checks/"
    fun transactions(): String = "/transactions/"
    fun payments(): String = "/payments/"

    fun balancesTable(): String = "account_balance"

    fun creditPrisonColumn(): String = "prison_id"

    fun creditInsertSql(
        amount: Long,
        prisonerNumber: String,
        prisonerName: String,
        prisonId: String,
        resolution: String = "credited",
    ): String = """
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
}
