package uk.gov.justice.digital.hmpps.compatibility.config

interface AuthProvider {
    fun obtainToken(): String
}
