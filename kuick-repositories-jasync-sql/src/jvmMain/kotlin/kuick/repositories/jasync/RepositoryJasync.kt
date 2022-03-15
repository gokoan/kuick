package kuick.repositories.jasync

import com.github.jasync.sql.db.QueryResult
import kuick.repositories.ModelQuery
import kuick.repositories.Repository
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

open class RepositoryJasync<T : Any>(
    val modelClass: KClass<T>,
    val tableName: String,
    protected val pool: JasyncPool,
    serializationStrategy: SerializationStrategy = DefaultSerializationStrategy()
) : Repository<T> {

    private val mqb = ModelSqlBuilder(modelClass, tableName, serializationStrategy)
    private var initialized = false

    override suspend fun init() {
        if (initialized) return
        initialized = true
        // TODO ¿DML de creación de la tabla?
    }

    private suspend fun ModelSqlBuilder.PreparedSql.execute(): QueryResult {
        //println("TEST SQL: $sql <-- $values")
        return pool.prepQuery(sql, values)
    }

    override suspend fun insert(t: T): T {
        init()
        mqb.insertPreparedSql(t).execute()
        return t
    }

    override suspend fun insertMany(ts: Collection<T>): Int {
        init()
        return if (ts.isEmpty()) 0 else pool.query(mqb.insertManySql(ts)).rowsAffected.toInt()
    }

    override suspend fun update(set: Map<KProperty1<T, *>, Any?>, incr: Map<KProperty1<T, Number>, Number>, where: ModelQuery<T>): Int {
        init()
        return mqb.preparedAtomicUpdateSql(set, incr, where).execute().rowsAffected.toInt()
    }

    override suspend fun deleteBy(q: ModelQuery<T>) {
        init()
        mqb.deletePreparedSql(q).execute()
    }

    override suspend fun findBy(q: ModelQuery<T>): List<T> {
        init()
        return mqb
            .selectPreparedSql(q)
            .execute().toModelList()
    }


    private fun QueryResult.toModelList(): List<T> = rows.map { row ->
        mqb.serializationStrategy.modelFromValues(modelClass, (0 until row.size).map { i -> row[i] } )
    }

    override suspend fun getAll(): List<T> {
        init()
        return pool.query(mqb.selectAll()).toModelList()
    }

    override suspend fun count(q: ModelQuery<T>): Int {
        init()
        return mqb.countPreparedSql(q).execute().rows.firstOrNull()?.get(0)?.toString()?.toInt() ?: 0
    }

}
