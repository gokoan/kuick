package kuick.repositories.memory

import kuick.concurrent.Lock
import kuick.repositories.*
import kuick.repositories.patterns.ModelRepositoryDecorator
import kotlin.reflect.*

@Deprecated("Use kuick.caching.Cache")
interface Cache {
    suspend fun <T : Any> get(key: String): T?
    suspend fun <T : Any> put(key: String, cached: T)
    suspend fun remove(key: String)
    suspend fun removeAll(): Unit = TODO("Not implemented Cache.removeAll")
}

class MemoryCache: Cache {
    private val lock = Lock()
    private val map: MutableMap<String, Any> = mutableMapOf()

    override suspend fun <T : Any> get(key: String): T? = lock { map[key]?.let { it as T? } }
    override suspend fun <T : Any> put(key: String, cached: T) = lock { map[key] = cached }
    override suspend fun remove(key: String): Unit = lock { map.remove(key) }
    override suspend fun removeAll() = lock { map.clear() }
}

/**
 * [ModelRepository]
 */
open class CachedModelRepository<I : Any, T : Any>(
    override val modelClass: KClass<T>,
    override val idField: KProperty1<T, I>,
    val repo: ModelRepository<I, T>,
    private val cache: Cache,
    private val cacheField: KProperty1<T, *>
) : ModelRepositoryDecorator<I, T>(repo) {

    private var initialized = false

    override suspend fun init() {
        if (initialized) return
        initialized = true
        repo.init()
    }

    private suspend fun invalidate(t: T): Unit {
        init()
        cache.remove(cacheField(t).toString())
    }

    override suspend fun insert(t: T): T {
        init()
        invalidate(t)
        return super.insert(t)
    }

    override suspend fun update(t: T): T {
        init()
        invalidate(t)
        return super.update(t)
    }

    override suspend fun delete(i: I) {
        init()
        val t = findById(i) ?: throw IllegalArgumentException()
        invalidate(t)
        super.delete(i)
    }

    override suspend fun findBy(q: ModelQuery<T>): List<T> {
        init()
        val keyEq = findCacheQuery(q)
        return if (keyEq != null) {
            val key = keyEq.value.toString()
            var subset = cache.get<List<T>>(key)
            if (subset == null) {
                subset = super.findBy(q)
                cache.put(key, subset)
            }
            val subRepo = ModelRepositoryMemory<I, T>(modelClass, idField)
            subset.forEach { subRepo.insert(it) }
            subRepo.findBy(q)
        } else {
            super.findBy(q)
        }
    }

    private fun findCacheQuery(q: ModelQuery<T>): FieldEqs<T, *>? = when {
        q is FieldEqs<T, *> && q.field == cacheField -> q
        q is FilterExpAnd<T> -> findCacheQuery(q.left) ?: findCacheQuery(q.right)
        else -> throw NotImplementedError("Missing hadling of query type: ${q}")
    }

}

inline fun <I : Any, reified T : Any> ModelRepository<I, T>.cached(cache: Cache, cacheField: KProperty1<T, *> = idField) = CachedModelRepository(T::class, idField, this, cache, cacheField)

