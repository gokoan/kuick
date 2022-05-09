package kuick.repositories

import kotlin.reflect.KProperty1

interface Repository<T : Any> {

    suspend fun init()

    suspend fun getAll(): List<T>

    suspend fun count(q: ModelQuery<T>): Int

    suspend fun groupBy(
        select: List<GroupBy<T>>,
        groupBy: List<KProperty1<T, *>>,
        where: ModelQuery<T>,
        orderBy: OrderByDescriptor<T>? = null,
        limit: Int? = null,
    ): List<List<Any?>>

    suspend fun findBy(q: ModelQuery<T>): List<T>

    suspend fun findBy(q: ModelQuery<T>, skip: Long = 0L, limit: Int? = null, orderBy: OrderByDescriptor<T>? = null): List<T> =
        findBy(AttributedModelQuery(base = q, skip = skip, limit = limit, orderBy = orderBy))

    suspend fun findOneBy(q: ModelQuery<T>): T? =
        findBy(q, skip = 0L, limit = 1).firstOrNull()

    suspend fun insert(t: T): T

    suspend fun insertMany(collection: Collection<T>): Int

    suspend fun update(
        set: Map<KProperty1<T, *>, Any?> = mapOf(),
        incr: Map<KProperty1<T, Number>, Number> = mapOf(),
        where: ModelQuery<T>): Int

    suspend fun deleteBy(q: ModelQuery<T>)
}
