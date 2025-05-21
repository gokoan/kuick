package kuick.repositories.sql

import kuick.models.Id
import kuick.repositories.*
import java.lang.reflect.Type
import kotlin.reflect.*
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.*
import kuick.logging.Logger // Added import

data class ParameterReflectInfo(val name: String, val type: Type, val clazz: KClass<*>, val isSubclassOfId: Boolean)
data class  ModelReflectionInfo<T>(val constructor: KFunction<T>, val constructorArgs: List<ParameterReflectInfo>)

fun KParameter.toReflectInfo() = ParameterReflectInfo(name!!, type.javaType, type.classifier as KClass<*>, (type.classifier as KClass<*>).isSubclassOf(Id::class))
fun KType.toReflectInfo() = ParameterReflectInfo(this.toString(), javaType, classifier as KClass<*>, (classifier as KClass<*>).isSubclassOf(Id::class))


fun <T : Any> KClass<T>.toModelReflectInfo(): ModelReflectionInfo<T> {
    val constructor = this.constructors.first()
    val parameters = constructor.parameters.map {it.toReflectInfo()}
    return ModelReflectionInfo(constructor, parameters)
}

abstract class SqlModelRepository<I : Any, T : Any>(
    override val modelClass: KClass<T>,
    val tableName: String,
    override val idField: KProperty1<T, I>,
    val serializationStrategy: SerializationStrategy = DefaultSerializationStrategy() // Made public for access in insert
) : ModelRepository<I, T> {

    protected val logger = Logger(this::class.simpleName ?: "SqlModelRepository") // Added logger

    // Helper function to convert camelCase to snake_case
    private fun String.toSnakeCase(): String = flatMap {
        if (it.isUpperCase()) listOf('_', it.toLowerCase()) else listOf(it)
    }.joinToString("")

    private val mqb = ModelSqlBuilder(modelClass, tableName, idField.name.toSnakeCase(), serializationStrategy)
    private var initialized = false


    private val reflectinfo : Map<String,ParameterReflectInfo> = modelClass.constructors.first().parameters
        .associate { it.name!! to it.toReflectInfo() }

    // Modified init signature
    override suspend fun init(connection: com.github.jasync.sql.db.SuspendingConnection?) {
        if (initialized) return
        initialized = true
        checkTableSchema(connection) // Pass connection to checkTableSchema
    }

    // Modified checkTableSchema to accept and use connection
    private suspend fun checkTableSchema(connection: com.github.jasync.sql.db.SuspendingConnection? = null) {
        query(mqb.checkTableSchema(), connection)
    }

    // Step 1: Modified insert signature and logic (already done in previous turn)
    override suspend fun insert(t: T, connection: com.github.jasync.sql.db.SuspendingConnection?): T {
        init(connection) // Pass connection
        val results = mqb.insertPreparedSql(t).execute(connection) // Pass connection
        val returnedRawId = results.rows.firstOrNull()?.get(0)

        if (returnedRawId != null) {
            val idParamInfo = ParameterReflectInfo(idField.name, idField.returnType.javaType, idField.returnType.classifier as KClass<*>, (idField.returnType.classifier as KClass<*>).isSubclassOf(Id::class))
            @Suppress("UNCHECKED_CAST")
            val newIdValue = serializationStrategy.fromDatabaseValue(idParamInfo, returnedRawId) as I

            // Use reflection to call the copy method
            return try {
                val copyMethod = modelClass.members.find {
                    it.name == "copy" && it is KFunction && it.parameters.any { p -> p.name == idField.name }
                } as? KFunction<T>

                if (copyMethod != null) {
                    val params = copyMethod.parameters.associateWith { param ->
                        when {
                            param.name == idField.name -> newIdValue
                            param.kind == KParameter.Kind.INSTANCE -> t
                            else -> modelClass.memberProperties.find { it.name == param.name }?.get(t)
                        }
                    }
                    // Filter out optional parameters that are not provided if their value is null
                    // This is important because callBy will fail if a null value is provided for a non-nullable optional parameter
                    val finalParams = params.filter { (param, value) -> value != null || param.isOptional || param.type.isMarkedNullable }
                    copyMethod.callBy(finalParams)
                } else {
                    logger.warn { "Copy method not found for ${modelClass.simpleName} during insert with new ID. Returning original object." }
                    t 
                }
            } catch (e: Exception) {
                logger.error(e) { "Error updating entity with new ID after insert: ${e.message}" }
                t // Return original object as fallback
            }
        }
        // If no ID was returned (should not happen with RETURNING clause)
        if (returnedRawId == null) {
             logger.warn { "Insert operation for table $tableName did not return an ID for entity: $t. Returning original object." }
        }
        return t 
    }

    companion object {
        private const val CHUNK_SIZE = 100 // Defined CHUNK_SIZE
    }

    override suspend fun insertMany(ts: Collection<T>): Int {
        return executeInTransaction { transactionalConn -> // Wrapped in executeInTransaction
            init(transactionalConn) // Pass the connection
            if (ts.isEmpty()) return@executeInTransaction 0
            var totalRowsAffected = 0
            ts.chunked(CHUNK_SIZE).forEach { chunk ->
                if (chunk.isNotEmpty()) {
                    val sqlForChunk = mqb.insertManySql(chunk)
                    totalRowsAffected += this.query(sqlForChunk, transactionalConn).rowsAffected.toInt() // Pass connection
                }
            }
            totalRowsAffected
        }
    }

    // Step 2: Refactor insertManyAndRetrieve
    suspend fun insertManyAndRetrieve(ts: Collection<T>): List<T> {
        return executeInTransaction { transactionalConn ->
            init(transactionalConn) // Call init with transactionalConn
            if (ts.isEmpty()) return@executeInTransaction emptyList()
            val resultList = mutableListOf<T>()
            for (t in ts) {
                resultList.add(insert(t, transactionalConn)) // Call insert with transactionalConn
            }
            resultList
        }
    }

    // Step 1: Modified upsert signature and logic (already done in previous turn)
    override suspend fun upsert(t: T, connection: com.github.jasync.sql.db.SuspendingConnection?): T { // Already updated
        init(connection) // Pass connection
        val preparedSql = mqb.upsertPreparedSqlPostgres(t, idField)
        val results = this.prepQuery(preparedSql.sql, preparedSql.values, connection) // Pass connection
        val returnedRawId = results.rows.firstOrNull()?.get(0)

        if (returnedRawId != null) {
            val idParamInfo = ParameterReflectInfo(idField.name, idField.returnType.javaType, idField.returnType.classifier as KClass<*>, (idField.returnType.classifier as KClass<*>).isSubclassOf(Id::class))
            @Suppress("UNCHECKED_CAST")
            val newIdValue = serializationStrategy.fromDatabaseValue(idParamInfo, returnedRawId) as I

            // Use reflection to call the copy method (similar to insert)
            return try {
                val copyMethod = modelClass.members.find {
                    it.name == "copy" && it is KFunction && it.parameters.any { p -> p.name == idField.name }
                } as? KFunction<T>

                if (copyMethod != null) {
                    val params = copyMethod.parameters.associateWith { param ->
                        when {
                            param.name == idField.name -> newIdValue
                            param.kind == KParameter.Kind.INSTANCE -> t
                            else -> modelClass.memberProperties.find { it.name == param.name }?.get(t)
                        }
                    }
                    val finalParams = params.filter { (param, value) -> value != null || param.isOptional || param.type.isMarkedNullable }
                    copyMethod.callBy(finalParams)
                } else {
                     logger.warn { "Copy method not found for ${modelClass.simpleName} during upsert. Returning original object." }
                    t
                }
            } catch (e: Exception) {
                logger.error(e) { "Error updating entity with new ID during upsert: ${e.message}" }
                t 
            }
        }
        // Fallback if no ID was returned.
        if (returnedRawId == null) {
             logger.warn { "Upsert operation did not return an ID for table $tableName. Returning original object for entity: $t" }
        }
        return t
    }

    override suspend fun updateBy(t: T, q: ModelQuery<T>): T {
        init(null) // Pass null for now
        mqb.updatePreparedSql(t, q).execute(null) // Pass null for now
        return t
    }

    override suspend fun update(
        set: Map<KProperty1<T, *>, Any?>,
        incr: Map<KProperty1<T, Number>, Number>,
        where: ModelQuery<T>
    ): Int {
        init(null) // Pass null for now
        return mqb.preparedAtomicUpdateSql(set, incr, where).execute(null).rowsAffected.toInt() // Pass null for now
    }

    // Step 3: Refactor updateMany
    override suspend fun updateMany(collection: Collection<T>) {
        executeInTransaction { transactionalConn ->
            init(transactionalConn) // Call init with transactionalConn
            if (collection.isEmpty()) return@executeInTransaction

            for (t in collection) {
                val q = idField eq idField.get(t)
                val preparedSql = mqb.updatePreparedSql(t, q)
                this.prepQuery(preparedSql.sql, preparedSql.values, transactionalConn) // Pass transactionalConn
            }
        }
    }

    // Step 4: Refactor updateManyBy
    override suspend fun updateManyBy(collection: Collection<T>, comparator: (T) -> (ModelQuery<T>)) {
        executeInTransaction { transactionalConn ->
            init(transactionalConn) // Call init with transactionalConn
            if (collection.isEmpty()) return@executeInTransaction

            for (t in collection) {
                val q = comparator(t)
                val preparedSql = mqb.updatePreparedSql(t, q)
                this.prepQuery(preparedSql.sql, preparedSql.values, transactionalConn) // Pass transactionalConn
            }
        }
    }


    override suspend fun deleteBy(q: ModelQuery<T>) {
        init(null) // Pass null for now
        mqb.deletePreparedSql(q).execute(null) // Pass null for now
    }

    override suspend fun findBy(q: ModelQuery<T>): List<T> {
        init(null) // Pass null for now
        return mqb
            .selectPreparedSql(q)
            .execute(null) // Pass null for now
            .toModelList(modelClass)
    }

    override suspend fun <P : Any> findProyectionBy(
        select: KClass<P>,
        where: ModelQuery<T>,
        limit: Int?,
        orderBy: OrderByDescriptor<T>?
    ): List<P> {
        init(null) // Pass null for now
        return mqb
            .selectPreparedSql(where, select)
            .execute(null) // Pass null for now
            .toModelList(select)
    }

    override suspend fun getAll(): List<T> {
        init(null) // Pass null for now
        return query(mqb.selectAll(), null).toModelList(modelClass) // Pass null for now
    }

    override suspend fun count(q: ModelQuery<T>): Int {
        init(null) // Pass null for now
        return mqb.countPreparedSql(q).execute(null).rows.firstOrNull()?.get(0)?.toString()?.toInt() ?: 0 // Pass null for now
    }

    override suspend fun groupBy(
        select: List<GroupBy<T>>,
        groupBy: List<KProperty1<T, *>>,
        where: ModelQuery<T>?,
        orderBy: OrderByDescriptor<T>?,
        limit: Int?
    ): List<List<Any?>> {
        init(null) // Pass null for now
        return mqb.groupByPreparedSql(select, groupBy, where, orderBy, limit).execute(null).rows // Pass null for now
    }

    // checkTableSchema already modified above

    // Updated PreparedSql.execute()
    private suspend fun ModelSqlBuilder.PreparedSql.execute(connection: com.github.jasync.sql.db.SuspendingConnection? = null): SqlQueryResults {
        //println("TEST SQL: $sql <-- $values")
        return prepQuery(sql, values, connection)
    }


    private fun <P : Any> SqlQueryResults.toModelList(toClass: KClass<P>): List<P> {
        val constructor = toClass.constructors.first()
        val parameters = constructor.parameters.map { reflectinfo[it.name]!! }
        val reflectionInfo = ModelReflectionInfo(constructor, parameters)
        return rows.map { row ->
            mqb.serializationStrategy.modelFromValues(reflectionInfo, (0 until row.size).map { i -> row[i] })
        }
    }

    //--------------------

    abstract suspend fun query(sql: String, connection: com.github.jasync.sql.db.SuspendingConnection? = null): SqlQueryResults

    abstract suspend fun prepQuery(sql: String, values: List<Any?>, connection: com.github.jasync.sql.db.SuspendingConnection? = null): SqlQueryResults

    protected abstract suspend fun <R> executeInTransaction(block: suspend (transactionalConnection: com.github.jasync.sql.db.SuspendingConnection) -> R): R

}
