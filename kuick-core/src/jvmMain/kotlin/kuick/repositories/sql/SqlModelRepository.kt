package kuick.repositories.sql

import kuick.models.Id
import kuick.repositories.*
import java.lang.reflect.Type
import kotlin.reflect.*
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.*
import kuick.logging.Logger // Added import
import com.github.jasync.sql.db.SuspendingConnection // Added import

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

    // Step 1.a: Rename init to initialize
    protected suspend fun initialize(connection: SuspendingConnection?) { // Replaced FQN
        if (initialized) return
        initialized = true
        checkTableSchema(connection)
    }

    // Step 1.b: Add override for init
    override suspend fun init() {
        initialize(null) // Default non-transactional initialization
    }

    private suspend fun checkTableSchema(connection: SuspendingConnection? = null) { // Replaced FQN
        query(mqb.checkTableSchema(), connection)
    }

    // Step 2.a: Rename insert to insertTransactional
    protected suspend fun insertTransactional(t: T, connection: SuspendingConnection?): T { // Replaced FQN
        initialize(connection) // Step 1.c: Update call to initialize
        val results = mqb.insertPreparedSql(t).execute(connection)
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
                            else -> modelClass.memberProperties.find { property -> property.name == param.name }?.get(t)
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

    // Step 2.b: Add override for insert
    override suspend fun insert(t: T): T {
        return insertTransactional(t, null) // Default non-transactional insert
    }

    override suspend fun insertMany(ts: Collection<T>): Int {
        return executeInTransaction { transactionalConn ->
            initialize(transactionalConn) // Step 1.c: Update call to initialize
            if (ts.isEmpty()) return@executeInTransaction 0
            var totalRowsAffected = 0
            ts.chunked(CHUNK_SIZE).forEach { chunk ->
                if (chunk.isNotEmpty()) {
                    val sqlForChunk = mqb.insertManySql(chunk)
                    totalRowsAffected += this.query(sqlForChunk, transactionalConn).rowsAffected.toInt()
                }
            }
            totalRowsAffected
        }
    }

    suspend fun insertManyAndRetrieve(ts: Collection<T>): List<T> {
        return executeInTransaction { transactionalConn ->
            initialize(transactionalConn) // Step 1.c: Update call to initialize
            if (ts.isEmpty()) return@executeInTransaction emptyList()
            val resultList = mutableListOf<T>()
            for (t in ts) {
                // Step 2.c: Update internal call to insertTransactional
                resultList.add(insertTransactional(t, transactionalConn))
            }
            resultList
        }
    }

    // Step 3.a: Rename upsert to upsertTransactional
    protected suspend fun upsertTransactional(t: T, connection: SuspendingConnection?): T { // Replaced FQN
        initialize(connection) // Step 1.c: Update call to initialize
        val preparedSql = mqb.upsertPreparedSqlPostgres(t, idField)
        val results = this.prepQuery(preparedSql.sql, preparedSql.values, connection)
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
                            else -> modelClass.memberProperties.find { property -> property.name == param.name }?.get(t)
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

    // Step 3.b: Add override for upsert
    override suspend fun upsert(t: T): T {
        return upsertTransactional(t, null) // Default non-transactional upsert
    }

    override suspend fun updateBy(t: T, q: ModelQuery<T>): T {
        initialize(null) // General review: call initialize(null)
        mqb.updatePreparedSql(t, q).execute(null) // General review: call execute(null)
        return t
    }

    override suspend fun update(
        set: Map<KProperty1<T, *>, Any?>,
        incr: Map<KProperty1<T, Number>, Number>,
        where: ModelQuery<T>
    ): Int {
        initialize(null) // General review: call initialize(null)
        return mqb.preparedAtomicUpdateSql(set, incr, where).execute(null).rowsAffected.toInt() // General review: call execute(null)
    }

    override suspend fun updateMany(collection: Collection<T>) {
        executeInTransaction { transactionalConn ->
            initialize(transactionalConn) // General review: Use transactionalConn
            if (collection.isEmpty()) return@executeInTransaction

            for (t in collection) {
                val q = idField eq idField.get(t)
                val preparedSql = mqb.updatePreparedSql(t, q)
                this.prepQuery(preparedSql.sql, preparedSql.values, transactionalConn) // General review: Use transactionalConn
            }
        }
    }

    override suspend fun updateManyBy(collection: Collection<T>, comparator: (T) -> (ModelQuery<T>)) {
        executeInTransaction { transactionalConn ->
            initialize(transactionalConn) // General review: Use transactionalConn
            if (collection.isEmpty()) return@executeInTransaction

            for (t in collection) {
                val q = comparator(t)
                val preparedSql = mqb.updatePreparedSql(t, q)
                this.prepQuery(preparedSql.sql, preparedSql.values, transactionalConn) // General review: Use transactionalConn
            }
        }
    }


    override suspend fun deleteBy(q: ModelQuery<T>) {
        initialize(null) // General review: call initialize(null)
        mqb.deletePreparedSql(q).execute(null) // General review: call execute(null)
    }

    override suspend fun findBy(q: ModelQuery<T>): List<T> {
        initialize(null) // General review: call initialize(null)
        return mqb
            .selectPreparedSql(q)
            .execute(null) // General review: call execute(null)
            .toModelList(modelClass)
    }

    override suspend fun <P : Any> findProyectionBy(
        select: KClass<P>,
        where: ModelQuery<T>,
        limit: Int?,
        orderBy: OrderByDescriptor<T>?
    ): List<P> {
        initialize(null) // General review: call initialize(null)
        return mqb
            .selectPreparedSql(where, select)
            .execute(null) // General review: call execute(null)
            .toModelList(select)
    }

    override suspend fun getAll(): List<T> {
        initialize(null) // General review: call initialize(null)
        return query(mqb.selectAll(), null).toModelList(modelClass)
    }

    override suspend fun count(q: ModelQuery<T>): Int {
        initialize(null) // General review: call initialize(null)
        return mqb.countPreparedSql(q).execute(null).rows.firstOrNull()?.get(0)?.toString()?.toInt() ?: 0 // General review: call execute(null)
    }

    override suspend fun groupBy(
        select: List<GroupBy<T>>,
        groupBy: List<KProperty1<T, *>>,
        where: ModelQuery<T>?,
        orderBy: OrderByDescriptor<T>?,
        limit: Int?
    ): List<List<Any?>> {
        initialize(null) // General review: call initialize(null)
        return mqb.groupByPreparedSql(select, groupBy, where, orderBy, limit).execute(null).rows // General review: call execute(null)
    }

    private suspend fun ModelSqlBuilder.PreparedSql.execute(connection: SuspendingConnection? = null): SqlQueryResults { // Replaced FQN
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

    abstract suspend fun query(sql: String, connection: SuspendingConnection? = null): SqlQueryResults // Replaced FQN

    abstract suspend fun prepQuery(sql: String, values: List<Any?>, connection: SuspendingConnection? = null): SqlQueryResults // Replaced FQN

    protected abstract suspend fun <R> executeInTransaction(block: suspend (transactionalConnection: SuspendingConnection) -> R): R // Replaced FQN

}
