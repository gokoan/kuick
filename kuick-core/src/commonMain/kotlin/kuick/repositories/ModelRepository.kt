package kuick.repositories

import kotlin.reflect.KProperty1

interface ModelRepository<I : Any, T : Any> : Repository<T> {

    // Interface
    val idField: KProperty1<T, I>

    // Default implementations
    suspend fun findById(i: I): T? = findOneBy(idField eq i)

    // Interface
    suspend fun updateBy(t: T, q: ModelQuery<T>): T

    // Default implementations
    suspend fun delete(i: I): Unit { deleteBy((idField eq i)) }
    suspend fun update(t: T): T = updateBy(t, idField eq idField.get(t))
    suspend fun upsert(t: T): T
    suspend fun updateMany(collection: Collection<T>) = collection.forEach { update(it) }
    suspend fun updateManyBy(collection: Collection<T>, comparator: (T) -> (ModelQuery<T>)): Unit {
        throw NotImplementedError()
    }
}

suspend fun <I : Any, T : Any> ModelRepository<I, T>.updateBy(q: ModelQuery<T>, updater: (T) -> T) {
    for (it in findBy(q)) update(updater(it))
}

suspend fun <I : Any, T : Any> ModelRepository<I, T>.updateOneBy(q: ModelQuery<T>, updater: (T) -> T): T =
        update(updater(findOneBy(q)!!))
