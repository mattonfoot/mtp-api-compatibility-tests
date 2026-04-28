package uk.gov.justice.digital.hmpps.compatibility.config

object TestConfig {
    val apiTarget: ApiTarget = ApiTarget.valueOf(
        System.getProperty("api.target", "kotlin").uppercase(),
    )
    val apiBaseUrl: String = System.getProperty("api.base-url", "http://localhost:8080")
    val dbUrl: String = System.getProperty("db.url", "jdbc:postgresql://localhost:5432/mtp_api")
    val dbUser: String = System.getProperty("db.user", "postgres")
    val dbPassword: String = System.getProperty("db.password", "postgres")
    val hmppsAuthUrl: String = System.getProperty("hmpps.auth.url", "http://localhost:8090/auth")
}
