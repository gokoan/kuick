package kuick.repositories.memory


import kuick.repositories.*
import java.util.Comparator
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.javaField


open class RepositoryMemory<T : Any>(
        val modelClass: KClass<T>
) : Repository<T> {

    val table = mutableListOf<T>()

    private var initialized = false

    override suspend fun init() {
        if (initialized) return
        initialized = true
    }

    override suspend fun insert(t: T): T {
        init()
        table.add(t)
        return t
    }

    override suspend fun deleteBy(q: ModelQuery<T>) {
        init()
        table.removeIf { it.match(q) }
    }

    private class ModelComparator<T>(val orderByList: List<OrderBy<T>>): Comparator<T> {

        override fun compare(a: T, b: T): Int {
            orderByList.forEach {
                val comparation = (it.prop.get(a) as Comparable<Any>).compareTo(it.prop.get(b)!!)
                if (comparation != 0) return if (it.ascending) comparation else -comparation
            }
            return 0
        }
    }

    override suspend fun findBy(q: ModelQuery<T>): List<T> {
        init()
        return find(q).map { tryToClone(it) }
    }

    private fun find(q: ModelQuery<T>): List<T> {
        var rows = table.filter { it.match(q) }
        if (q is AttributedModelQuery) {
            q.orderBy?.let { orderBy ->
                rows = rows.sortedWith(ModelComparator(orderBy.list))
            }
            q.skip?.let {
                rows = rows.drop(q.skip.toInt())
            }
            q.limit?.let {
                rows = rows.take(q.limit)
            }
        }
        return rows
    }

    private fun <T : Any> tryToClone (obj: T): T {
        if (!obj::class.isData) {
            println("cannot clone object, possible unpredictable errors if modifying it")
            return obj
        }

        val copy = obj::class.memberFunctions.first { it.name == "copy" }
        val instanceParam = copy.instanceParameter!!
        return copy.callBy(mapOf(
            instanceParam to obj
        )) as T
    }


    private val modelClassFieldByName = modelClass.declaredMemberProperties.map { Pair(it.name, it) }.toMap()

    override suspend fun <P : Any> findProyectionBy(
        select: KClass<P>,
        where: ModelQuery<T>,
        limit: Int?,
        orderBy: OrderByDescriptor<T>?
    ): List<P> {
        val all = find(AttributedModelQuery(where, limit = limit, orderBy = orderBy))
        val constructor = select.constructors.first()
        return all.map { data ->
            val projectedValues = constructor.parameters.map { modelClassFieldByName[it.name]?.get(data) }
            constructor.call(*projectedValues.toTypedArray())
        }
    }

    override suspend fun getAll(): List<T> {
        init()
        return table.map { tryToClone(it) }
    }

    override suspend fun count(q: ModelQuery<T>): Int {
        init()
        return table.filter { it.match(q) }.size
    }

    override suspend fun groupBy(
        select: List<GroupBy<T>>,
        groupBy: List<KProperty1<T, *>>,
        where: ModelQuery<T>?,
        orderBy: OrderByDescriptor<T>?,
        limit: Int?
    ): List<List<Any?>> {
        TODO("Not yet implemented")
    }

    protected fun T.match(q: ModelQuery<T>): Boolean = when (q) {
        is FieldUnop<T, *> -> {
            when (q) {
                is FieldIsNull<T, *> -> q.field.get(this) == null
                else -> throw NotImplementedError("Missing implementation of .toSquash() for ${this}")
            }
        }
        is FieldBinop<T, *, *> -> {
            when (q) {
                is FieldEqs<T, *> ->
                    q.field.get(this) == q.value
                is FieldNeq<T, *> ->
                    q.field.get(this) != q.value
                is FieldLike<T> ->
                    q.value?.let { q.field.get(this)?.contains(q.value) ?: false } ?: false
                is FieldGt<T, *> ->
                    compare(q).let { if (it == null) false else it > 0 }
                is FieldGte<T, *> ->
                    compare(q).let { if (it == null) false else it >= 0 }
                is FieldLt<T, *> ->
                    compare(q).let { if (it == null) false else it < 0 }
                is FieldLte<T, *> ->
                    compare(q).let { if (it == null) false else it <= 0 }
                is FieldWithin<T, *> ->
                    q.value.contains(q.field(this))
                is FieldWithinComplex<T, *> ->
                    q.value.contains(q.field(this))
                is FieldBinopOnSubselect<T, *> ->
                    find(q.value as ModelQuery<T>).any { q.field(it) == q.field(this) }
                else -> throw NotImplementedError("Missing implementation of .toSquash() for ${this}")
            }
        }
        is FilterExpNot<T> -> !match(q.exp)
        is FilterExpAnd<T> -> this.match(q.left) and this.match(q.right)
        is FilterExpOr<T> -> this.match(q.left) or this.match(q.right)
        is DecoratedModelQuery<T> -> this.match(q.base)

        else -> throw NotImplementedError("Missing implementation of .match() for ${q}")
    }

    private fun T.compare(q: SimpleFieldBinop<T, *>) =
            (q.field.get(this) as Comparable<Any>?)?.compareTo(q.value as Comparable<Any>)

    override suspend fun update(set: Map<KProperty1<T, *>, Any?>, incr: Map<KProperty1<T, Number>, Number>, where: ModelQuery<T>): Int {
        init()
        val toModify = find(where)

        fun KProperty1<T, Any?>.hardSet(e: T, v: Any?) {
            this.javaField?.isAccessible = true
            this.javaField?.set(e, v)
        }

        toModify.forEach { entity ->
            set.forEach { (k, v) -> k.hardSet(entity, v)}
            incr.forEach { (k, v) ->
                val currentValue = k.get(entity)
                val updatedValue = when {
                    currentValue is Int -> currentValue + (v as Int)
                    currentValue is Long  -> currentValue + v.toLong()
                    currentValue is Double -> currentValue + (v as Double)
                    else -> throw IllegalArgumentException("Falta gestionar el tipo de dato ${currentValue::class} en el update()")
                }
                k.hardSet(entity, updatedValue)
            }
        }

        return toModify.count()
    }

    override suspend fun insertMany(collection: Collection<T>): Int {
        init()
        return collection.forEach { insert(it) }.let { collection.count() }
    }

}

