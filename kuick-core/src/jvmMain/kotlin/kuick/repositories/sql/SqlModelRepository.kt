package kuick.repositories.sql

import kuick.repositories.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1



abstract class SqlModelRepository<I : Any, T : Any>(
    val modelClass: KClass<T>,
    val tableName: String,
    override val idField: KProperty1<T, I>,
    serializationStrategy: SerializationStrategy = DefaultSerializationStrategy()
) : ModelRepository<I, T> {

    private val mqb = ModelSqlBuilder(modelClass, tableName, serializationStrategy)
    private var initialized = false

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
            .toModelList()
    }

    override suspend fun getAll(): List<T> {
        init()
        return query(mqb.selectAll()).toModelList()
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

    private fun SqlQueryResults.toModelList(): List<T> =
        rows.map { row ->
            mqb.serializationStrategy.modelFromValues(modelClass, (0 until row.size).map { i -> row[i] })
        }

    //--------------------

    abstract suspend fun query(sql: String): SqlQueryResults

    abstract suspend fun prepQuery(sql: String, values: List<Any?>): SqlQueryResults

}
