package kuick.repositories.sql

import kuick.models.Id
import kuick.repositories.*
import java.lang.reflect.Type
import kotlin.reflect.*
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.*

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
    serializationStrategy: SerializationStrategy = DefaultSerializationStrategy()
) : ModelRepository<I, T> {

    private val mqb = ModelSqlBuilder(modelClass, tableName, serializationStrategy)
    private var initialized = false


    private val reflectinfo : Map<String,ParameterReflectInfo> = modelClass.constructors.first().parameters
        .associate { it.name!! to it.toReflectInfo() }

    override suspend fun init() {
        if (initialized) return
        initialized = true
        checkTableSchema()
    }

    override suspend fun insert(t: T): T {
        init()
        mqb.insertPreparedSql(t).execute()
        return t
    }

    override suspend fun insertMany(ts: Collection<T>): Int {
        init()
        return if (ts.isEmpty()) 0 else query(mqb.insertManySql(ts)).rowsAffected.toInt()
    }

    override suspend fun upsert(t: T): T {
        init()
        val updated = mqb.updatePreparedSql(t, idField eq idField.get(t)).execute()
        return when (updated.rowsAffected) {
            0L -> insert(t)
            1L -> t
            else -> throw IllegalStateException("UPSERT operation returned MORE than 1 result ==> CHECK PRIMARY KEY at $tableName that should be in field ${idField.name}")
        }
    }

    override suspend fun updateBy(t: T, q: ModelQuery<T>): T {
        init()
        mqb.updatePreparedSql(t, q).execute()
        return t
    }

    override suspend fun update(
        set: Map<KProperty1<T, *>, Any?>,
        incr: Map<KProperty1<T, Number>, Number>,
        where: ModelQuery<T>
    ): Int {
        init()
        return mqb.preparedAtomicUpdateSql(set, incr, where).execute().rowsAffected.toInt()
    }

    override suspend fun updateMany(collection: Collection<T>) {
        init()
        if (collection.any()) query(mqb.updateManyPreparedSql(collection.map { it to (idField eq idField.get(it)) }))
    }

    override suspend fun updateManyBy(collection: Collection<T>, comparator: (T) -> (ModelQuery<T>)) {
        init()
        if (collection.any()) query(mqb.updateManyPreparedSql(collection.map { it to comparator(it) }))
    }


    override suspend fun deleteBy(q: ModelQuery<T>) {
        init()
        mqb.deletePreparedSql(q).execute()
    }

    override suspend fun findBy(q: ModelQuery<T>): List<T> {
        init()
        return mqb
            .selectPreparedSql(q)
            .execute()
            .toModelList(modelClass)
    }

    override suspend fun <P : Any> findProyectionBy(
        select: KClass<P>,
        where: ModelQuery<T>,
        limit: Int?,
        orderBy: OrderByDescriptor<T>?
    ): List<P> {
        init()
        return mqb
            .selectPreparedSql(where, select)
            .execute()
            .toModelList(select)
    }

    override suspend fun getAll(): List<T> {
        init()
        return query(mqb.selectAll()).toModelList(modelClass)
    }

    override suspend fun count(q: ModelQuery<T>): Int {
        init()
        return mqb.countPreparedSql(q).execute().rows.firstOrNull()?.get(0)?.toString()?.toInt() ?: 0
    }

    override suspend fun groupBy(
        select: List<GroupBy<T>>,
        groupBy: List<KProperty1<T, *>>,
        where: ModelQuery<T>?,
        orderBy: OrderByDescriptor<T>?,
        limit: Int?
    ): List<List<Any?>> {
        init()
        return mqb.groupByPreparedSql(select, groupBy, where, orderBy, limit).execute().rows
    }

    private suspend fun checkTableSchema() {
        query(mqb.checkTableSchema())
    }

    private suspend fun ModelSqlBuilder.PreparedSql.execute(): SqlQueryResults {
        //println("TEST SQL: $sql <-- $values")
        return prepQuery(sql, values)
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

    abstract suspend fun query(sql: String): SqlQueryResults

    abstract suspend fun prepQuery(sql: String, values: List<Any?>): SqlQueryResults

}
