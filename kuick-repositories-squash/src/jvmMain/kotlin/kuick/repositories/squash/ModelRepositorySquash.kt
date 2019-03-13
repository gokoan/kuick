package kuick.repositories.squash

import kuick.db.domainTransaction
import kuick.json.Json
import kuick.models.Id
import kuick.repositories.*
import kuick.repositories.squash.orm.*
import kuick.utils.nonStaticFields
import org.jetbrains.squash.definition.*
import org.jetbrains.squash.expressions.*
import org.jetbrains.squash.schema.create
import java.time.LocalDate
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.starProjectedType


open class ModelRepositorySquash<I : Any, T : Any>(
        val modelClass: KClass<T>,
        val idField: KProperty1<T, I>,
        val textLength: Int = LONG_TEXT_LEN
) : ModelRepository<I, T> {

    val table = ORMTableDefinition(modelClass)

    init {
        modelClass.java.nonStaticFields().forEach { field ->
            val prop = modelClass.declaredMemberProperties.firstOrNull { it.name == field.name }
            if (prop == null) throw IllegalStateException("Property not found for field: ${field.name}")
            val nullableProp = prop.returnType.isMarkedNullable
            val returnType = prop.returnType.classifier!!.starProjectedType
            val columnName = prop.name.toSnakeCase()
            //println("Registering field ${prop} with return type: ${prop.returnType}")
            with(table) {
                var columnDefinition: ColumnDefinition<Any?> = when {
                    returnType == String::class.starProjectedType -> varchar(columnName, textLength)
                    returnType == Int::class.starProjectedType -> integer(columnName)
                    returnType == Long::class.starProjectedType -> long(columnName)
                    returnType == Double::class.starProjectedType -> decimal(columnName, 5, 4)
                    returnType == Boolean::class.starProjectedType -> bool(columnName)
                    returnType == Date::class.starProjectedType -> long(columnName)
                    returnType == LocalDate::class.starProjectedType -> varchar(columnName, LOCAL_DATE_TIME_LEN)
                    returnType.isSubtypeOf(Id::class.starProjectedType) -> (varchar(columnName, ID_LEN))
                    else -> varchar(columnName, textLength)
                }
                if (nullableProp) columnDefinition = columnDefinition.nullable()
                prop to columnDefinition
            }
        }
    }

    override suspend fun init() {
        domainTransaction().squashTr().databaseSchema().create(table)
    }

    override suspend fun insert(t: T): T = table.insert(domainTransaction(), t)

    override suspend fun update(t: T): T = table.update(domainTransaction(), t) {
        (idField eq (idField.get(t))).toSquash()
    }

    override suspend fun updateBy(t: T, q: ModelQuery<T>): T {
        table.update(domainTransaction(), t) { q.toSquash() }
        return t
    }

    override suspend fun delete(i: I) = table.delete(domainTransaction()) {
        (idField eq i).toSquash()
    }

    override suspend fun deleteBy(q: ModelQuery<T>) = table.delete(domainTransaction()) {
        q.toSquash()
    }

    override suspend fun findById(i: I): T? = findOneBy(idField eq i)

    override suspend fun findOneBy(q: ModelQuery<T>): T? =
            table.selectOne(domainTransaction()) { q.toSquash() }

    override suspend fun findBy(q: ModelQuery<T>): List<T> =
            table.select(domainTransaction()) { q.toSquash() }

    override suspend fun getAll(): List<T> = table.selectAll(domainTransaction())

    private fun ModelQuery<T>.toSquash(): Expression<Boolean> = when (this) {
        is FieldEqs<T, *> -> when (value) {
            is Id -> table[field] eq (value as Id).id
            else -> table[field] eq value
        }
        is FieldGt<T, *> -> table[field] gt value
        is FieldGte<T, *> -> table[field] gteq value
        is FieldLt<T, *> -> table[field] lt value
        is FieldLte<T, *> -> table[field] lteq value
        is FieldWithin<T, *> -> table[field] within (value ?: emptySet())
        is FieldWithinComplex<T, *> -> table[field] within (value?.map { Json.toJson(it) } ?: emptySet())
        is FilterExpAnd<T> -> left.toSquash() and right.toSquash()
        is FilterExpOr<T> -> left.toSquash() or right.toSquash()
        is FilterExpNot<T> -> not(exp.toSquash())
        else -> throw NotImplementedError("Missing implementation of .toSquash() for ${this}")
    }


//    fun <T> Collection<T>.isCollectionOfBasicType() = setOf(Boolean::class, Number::class, Char::class, String::class)
//        .contains(this::class::typeParameters[0])
//
//    fun KClass<*>.isBasicType() = setOf<KClass<*>>(Boolean::class, Number::class, Char::class, String::class)
//        .contains(this)
//
//    fun KClass<Collection<*>>.isCollectionOfBasicType() = this.typeParameters.get(0).
//        .contains(this)
}

private fun String.toSnakeCase(): String = flatMap {
    if (it.isUpperCase()) listOf('_', it.toLowerCase()) else listOf(it)
}.joinToString("")