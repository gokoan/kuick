package kuick.repositories.memory


import kuick.repositories.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1


open class ModelRepositoryMemory<I : Any, T : Any>(
        modelClass: KClass<T>,
        override val idField: KProperty1<T, I>
) : ModelRepository<I, T>, RepositoryMemory<T>(modelClass) {


    override suspend fun update(t: T): T {
        init()
        val idx = table.indexOfFirst { id(it) == id(t) }
        if (idx >= 0) {
            table.removeAt(idx)
            table.add(idx, t)
        }
        return t
    }

    override suspend fun upsert(t: T): T {
        init()
        val idx = table.indexOfFirst { id(it) == id(t) }
        if (idx >= 0) {
            table.removeAt(idx)
            table.add(idx, t)
        } else {
            table.add(t)
        }
        return t
    }

    override suspend fun updateBy(t: T, q: ModelQuery<T>): T {
        init()
        findBy(q).forEach { update(t) }
        return t
    }

    override suspend fun delete(i: I) {
        init()
        table.removeIf { id(it) == i }
    }

    override suspend fun deleteBy(q: ModelQuery<T>) {
        init()
        table.removeIf { it.match(q) }
    }

    private fun id(t: T): I = idField.get(t)

}

