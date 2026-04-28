package uk.gov.justice.digital.hmpps.compatibility.support

import uk.gov.justice.digital.hmpps.compatibility.config.TestConfig
import java.sql.Connection
import java.sql.DriverManager

class DatabaseHelper : AutoCloseable {

    private val connection: Connection by lazy {
        DriverManager.getConnection(TestConfig.dbUrl, TestConfig.dbUser, TestConfig.dbPassword)
    }

    fun query(sql: String): List<Map<String, Any?>> {
        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val meta = rs.metaData
                val results = mutableListOf<Map<String, Any?>>()
                while (rs.next()) {
                    val row = mutableMapOf<String, Any?>()
                    for (i in 1..meta.columnCount) {
                        row[meta.getColumnName(i)] = rs.getObject(i)
                    }
                    results.add(row)
                }
                return results
            }
        }
    }

    fun executeSql(sql: String) {
        connection.createStatement().use { stmt -> stmt.execute(sql) }
    }

    fun count(table: String, where: String = "1=1"): Int {
        val rows = query("SELECT COUNT(*) AS cnt FROM $table WHERE $where")
        return (rows[0]["cnt"] as Number).toInt()
    }

    override fun close() {
        if (!connection.isClosed) connection.close()
    }
}
