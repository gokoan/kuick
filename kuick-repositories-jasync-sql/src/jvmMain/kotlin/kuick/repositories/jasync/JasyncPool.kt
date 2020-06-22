package kuick.repositories.jasync

import com.github.jasync.sql.db.QueryResult
import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import java.util.Date

class JasyncPool(
    host: String,
    port: Int,
    database: String,
    username: String,
    password: String
) {
    private val pool =
        PostgreSQLConnectionBuilder.createConnectionPool(
            "jdbc:postgresql://$host:$port/$database?user=$username&password=$password"
        )

    fun connection(): SuspendingConnection = pool.asSuspending

    suspend fun query(sql: String): QueryResult = execute(sql, null)

    suspend fun prepQuery(sql: String, values: List<Any?>): QueryResult = execute(sql, values)

    private suspend fun execute(sql: String, values: List<Any?>?): QueryResult {
        // println("${Date()} [SQL] $sql ${values ?: ""}")
        val begin = System.currentTimeMillis()
        val qr = values
            ?.let { connection().sendPreparedStatement(sql, values) }
            ?: connection().sendQuery(sql)
        val end = System.currentTimeMillis()
        val lapse = (end - begin)
        println("${Date()} [SQL] $sql ${values ?: ""} | ${qr.rowsAffected} rows, $lapse ms")
        return qr
    }
}
