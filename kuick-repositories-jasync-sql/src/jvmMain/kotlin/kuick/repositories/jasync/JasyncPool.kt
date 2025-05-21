package kuick.repositories.jasync

import com.github.jasync.sql.db.QueryResult
import com.github.jasync.sql.db.SuspendingConnection
import com.github.jasync.sql.db.asSuspending
import com.github.jasync.sql.db.pool.ConnectionPool
import com.github.jasync.sql.db.postgresql.PostgreSQLConnection
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import kuick.env.Environment
import kuick.logging.Logger
import kuick.logging.trace
import java.util.*

val logger = Logger("Jasync")

class JasyncPool(
    host: String,
    port: Int,
    database: String,
    username: String,
    password: String,
    applicationName: String = "kuick-jaync-pool",
    maxActiveConnections: Int = 8,
    val debug: Boolean = false
) {
    private val pool: ConnectionPool<PostgreSQLConnection>

    init {
        pool = PostgreSQLConnectionBuilder.createConnectionPool {
            this.host = host
            this.port = port
            this.database = database
            this.username = username
            this.password = password
            this.applicationName = applicationName
            this.maxActiveConnections = maxActiveConnections
        }
        log("JasyncPool ---------------------")
        log("Created conection pool:")
        log("  host                 : $host")
        log("  port                 : $port")
        log("  database             : $database")
        log("  username             : $username")
        log("  applicationName      : $applicationName")
        log("  maxActiveConnections : $maxActiveConnections")
        log("/JasyncPool ---------------------")
    }

    fun connection(): SuspendingConnection = pool.asSuspending

    suspend fun query(sql: String): QueryResult = execute(sql, null)

    suspend fun prepQuery(sql: String, values: List<Any?>): QueryResult = execute(sql, values)

    private suspend fun execute(sql: String, values: List<Any?>?): QueryResult {
        try {
            val begin = System.currentTimeMillis()
            val qr = values
                ?.let { connection().sendPreparedStatement(sql, values) }
                ?: connection().sendQuery(sql)
            val end = System.currentTimeMillis()
            val lapse = (end - begin)
            logger.trace { "[SQL] $sql ${values ?: ""} | ${qr.rowsAffected} rows, $lapse ms" }
            return qr
        } catch (t: Throwable) {
            logger.error { "SQL ERROR ---------------------" }
            logger.error { "SQL:    $sql" }
            logger.error { "Values: $values" }
            logger.error(t) { "Exception during SQL execution" } // Log exception before rethrowing
            throw t
        }
    }

    private fun log(msg: String) {
        logger.info { msg } // Date prefix will be handled by logger configuration
    }

    private fun debug(msg: String) {
        if (debug) log(msg)
    }

    suspend fun <R> inTransaction(block: suspend (connection: SuspendingConnection) -> R): R {
        val conn = this.connection()
        conn.beginTransaction() // Start transaction
        try {
            val result = block(conn) // Execute block with the connection
            conn.commitTransaction() // Commit
            return result
        } catch (e: Throwable) {
            try {
                conn.rollbackTransaction() // Rollback on error
            } catch (rollbackEx: Throwable) {
                logger.warn(rollbackEx) { "Error during transaction rollback" } // Log rollback error
            }
            throw e // Re-throw original exception
        }
        // Note: Connection release is typically handled by the JAsync pool/connection proxy.
        // If explicit release were needed, it would be in a finally block.
    }


    companion object {

        fun fromEnvironment(prefix: String = ""): JasyncPool {

            fun env(key: String, default: String? = null): String =
                Environment.env("$prefix$key", default)

            return JasyncPool(
                host = env("DB_HOST"),
                port = env("DB_PORT").toInt(),
                database = env("DB_DATABASE"),
                username = env("DB_USERNAME"),
                password = env("DB_PASSWORD"),
                maxActiveConnections = env("DB_MAX_ACTIVE_CONNECTIONS", "8").toInt(),
                debug = env("DB_DEBUG", "false").toBoolean()
            )
        }

    }
}
