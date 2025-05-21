package kuick.repositories.jasync

import com.github.jasync.sql.db.QueryResult
import kuick.repositories.sql.DefaultSerializationStrategy
import kuick.repositories.sql.SerializationStrategy
import kuick.repositories.sql.SqlModelRepository
import kuick.repositories.sql.SqlQueryResults
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1


open class ModelRepositoryJasync<I : Any, T : Any>(
    val pool: JasyncPool,
    modelClass: KClass<T>,
    tableName: String,
    idField: KProperty1<T, I>,
    serializationStrategy: SerializationStrategy = DefaultSerializationStrategy()
) : SqlModelRepository<I, T>(modelClass, tableName, idField, serializationStrategy) {

    // Step 2.1: Implement executeInTransaction
    override suspend fun <R> executeInTransaction(block: suspend (transactionalConnection: com.github.jasync.sql.db.SuspendingConnection) -> R): R {
        return pool.inTransaction { conn -> // conn is from JasyncPool.inTransaction
            block(conn) // Pass this specific connection to the block
        }
    }

    // Step 2.2: Update query and prepQuery implementations
    override suspend fun query(sql: String, connection: com.github.jasync.sql.db.SuspendingConnection?): SqlQueryResults {
        return if (connection != null) {
            connection.sendQuery(sql).toSqlQueryResults()
        } else {
            pool.query(sql).toSqlQueryResults()
        }
    }

    override suspend fun prepQuery(sql: String, values: List<Any?>, connection: com.github.jasync.sql.db.SuspendingConnection?): SqlQueryResults {
        return if (connection != null) {
            connection.sendPreparedStatement(sql, values).toSqlQueryResults()
        } else {
            pool.prepQuery(sql, values).toSqlQueryResults()
        }
    }

    fun QueryResult.toSqlQueryResults(): SqlQueryResults = SqlQueryResults(rowsAffected, rows)

}
