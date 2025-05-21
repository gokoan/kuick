package kuick.repositories.memory


import kuick.repositories.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1


open class ModelRepositoryMemory<I : Any, T : Any>(
        modelClass: KClass<T>,
        override val idField: KProperty1<T, I>,
        efficientCloneFunction : ((T)->T)? = null,
) : ModelRepository<I, T>, RepositoryMemory<T>(modelClass, efficientCloneFunction) {


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

    override suspend fun updateManyBy(collection: Collection<T>, comparator: (T) -> (ModelQuery<T>)) {
        init()
        if (collection.isEmpty()) return

        for (itemInCollection in collection) {
            val query = comparator(itemInCollection)
            // Find the index of the item in the internal table that matches the query
            // We assume RepositoryMemory has `table: MutableList<T>` and `it.match(query)`
            val indexInTable = table.indexOfFirst { it.match(query) }

            if (indexInTable != -1) {
                // If found, replace the item in the table with the item from the input collection
                table.removeAt(indexInTable)
                table.add(indexInTable, efficientClone(itemInCollection)) // Use efficientClone for consistency
            }
            // If not found, this specific itemInCollection does not update anything.
            // This matches typical update semantics (update if exists).
        }
    }
}

