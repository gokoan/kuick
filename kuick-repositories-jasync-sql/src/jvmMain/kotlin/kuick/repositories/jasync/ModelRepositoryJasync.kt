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

    override suspend fun query(sql: String): SqlQueryResults = pool.query(sql).toSqlQueryResults()

    override suspend fun prepQuery(sql: String, values: List<Any?>): SqlQueryResults =
        pool.prepQuery(sql, values).toSqlQueryResults()

    fun QueryResult.toSqlQueryResults(): SqlQueryResults = SqlQueryResults(rowsAffected, rows)

}
