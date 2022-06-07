package kuick.repositories.sql

import kuick.models.Id
import kuick.repositories.*
import kuick.repositories.sql.annotations.AutoIncrementIndex
import kuick.utils.nonStaticFields
import java.lang.reflect.Field
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

class ModelSqlBuilder<T: Any>(
    val kClass: KClass<T>,
    _tableName: String,
    val serializationStrategy: SerializationStrategy = DefaultSerializationStrategy()
) {
    private val tableName = if (_tableName != _tableName.toLowerCase()) """public."$_tableName"""" else _tableName

    data class PreparedSql(val sql: String, val values: List<Any?>)

    private val modelFields: List<Field> = kClass.java.nonStaticFields()

    private val modelProperties = modelFields
        .map { field -> kClass.memberProperties.first { it.name == field.name } }

    private val modelPropertiesForInsert = modelFields
        .filterNot { it.annotations.any { it is AutoIncrementIndex } }
        .map { field -> kClass.memberProperties.first { it.name == field.name } }

    val modelColumns = modelFields.map { it.name.toSnakeCase() }

    protected val selectColumns = modelColumns.csv()

    protected val insertColumns = modelFields
        .filterNot { it.annotations.any { it is AutoIncrementIndex } }
        .map { it.name.toSnakeCase() }

    protected val insertValueSlots = insertColumns.map { "?" }.csv()

    protected val updateColumns = modelColumns.map { "$it = ?" }.csv()

    protected val selectBase = "SELECT $selectColumns FROM $tableName"

    fun selectSql(q: ModelQuery<T>) = "$selectBase WHERE ${toSql(q, this::toSqlValue)}"

    fun selectAll() = selectBase

    fun checkTableSchema() = "$selectBase LIMIT 1" // Select básico con 1 row máximo

    fun <A: Any> selectPreparedSql(q: ModelQuery<A>, projection: KClass<*> = kClass): PreparedSql {
        val selectClause = if (projection == kClass) selectBase else {
            val selectColumns = projection.java.nonStaticFields().map { it.name.toSnakeCase() }.csv()
            "SELECT $selectColumns FROM $tableName"
        }
        return selectPreparedSql(selectClause, q)
    }

    private fun <A: Any> selectPreparedSql(selectBase: String, q: ModelQuery<A>): PreparedSql {
        val base = PreparedSql("$selectBase WHERE ${toSql(q, this::toSlotValue)}", queryValues(q))
        val extraSql = mutableListOf<String>()

        q.tryGetAttributed()?.let { q ->
            if (q.orderBy != null) extraSql.add("ORDER BY ${q.orderBy!!.list.map { "${it.prop.name.toSnakeCase()} ${if (it.ascending) "ASC" else "DESC"}" }.csv()}")
            if (q.skip > 0) extraSql.add("OFFSET ${q.skip}")
            if (q.limit != null) extraSql.add("LIMIT ${q.limit}")
        }

        return base.copy("${base.sql} ${extraSql.joinToString(" ")}".trim(), base.values)
    }

    val insertSql = "INSERT INTO $tableName ($insertColumns) VALUES ($insertValueSlots)"
    fun insertPreparedSql(t: T): PreparedSql =
        PreparedSql(insertSql, valuesOfForInsert(t))

    fun insertManySql(ts: Collection<T>): String =
        "INSERT INTO $tableName ($insertColumns) VALUES ${ts.map { "(${valuesOfForInsert(it).map { toSqlValue(it) }.csv()})" }.csv()}"

    fun updateSql(q: ModelQuery<T>) = "UPDATE $tableName SET $updateColumns WHERE ${toSql(q, this::toSqlValue)}"

    fun updatePreparedSql(t: T, q: ModelQuery<T>): PreparedSql =
        PreparedSql("UPDATE $tableName SET $updateColumns WHERE ${toSql(q, this::toSlotValue)}", valuesOf(t) + queryValues(q))


    fun updateManyPreparedSql(ts: Collection<Pair<T, ModelQuery<T>>>): String =
        ts.map { (t, q) ->
            "UPDATE $tableName " +
                "SET ${modelColumns.mapIndexed { index, s -> "$s = ${toSqlValue(valuesOf(t)[index])}" }.csv()} " +
                "WHERE ${toSql(q, this::toSqlValue)};"
        }.joinToString (separator = " ")

    fun preparedAtomicUpdateSql(set: Map<KProperty1<T, *>, Any?>, incr: Map<KProperty1<T, Number>, Number>, where: ModelQuery<T>): PreparedSql {
        // Necesitamos listas para fijar un orden conocido
        val setPairs = set.entries.toList()
        val incPairs = incr.entries.toList()

        val setClause =
            setPairs.map { (p, v) -> "${p.name.toSnakeCase()} = ?" } +
            incPairs.map { (p, v) -> "${p.name.toSnakeCase()} = ${p.name.toSnakeCase()} + ?" }

        val setValues = setPairs.map { prepareToSetCommand(it.key.annotations, it.value) } + incPairs.map { prepareToSetCommand(it.key.annotations, it.value) }

        return PreparedSql("UPDATE $tableName SET ${setClause.csv()} WHERE ${toSql(where, this::toSlotValue)}", setValues + queryValues(where))
    }

    private fun prepareToSetCommand(annotations: List<Annotation>,value: Any?) = toDb(annotations, value)

    fun deleteSql(q: ModelQuery<T>) = "DELETE FROM $tableName WHERE ${toSql(q, this::toSqlValue)}"

    fun deletePreparedSql(q: ModelQuery<T>): PreparedSql =
        PreparedSql("DELETE FROM $tableName WHERE ${toSql(q, this::toSlotValue)}", queryValues(q))

    fun countPreparedSql(q: ModelQuery<T>): PreparedSql =
        PreparedSql("SELECT COUNT(*) FROM $tableName WHERE ${toSql(q, this::toSlotValue)}", queryValues(q))


    fun groupByPreparedSql(select: List<GroupBy<T>>,
                           groupBy: List<KProperty1<T, *>>,
                           where: ModelQuery<T>? = null,
                           orderBy: OrderByDescriptor<T>? = null,
                           limit: Int? = null
    ): PreparedSql {
        val sqlParts = mutableListOf<String>()

        sqlParts.add("SELECT " + (groupBy.map { it.name.toSnakeCase() } +
            select.map { "${it.operator.name}(${it.prop.name.toSnakeCase()})" }
            ).csv())

        sqlParts.add("FROM $tableName")

        where?.let { sqlParts.add("WHERE ${toSql(where, this::toSlotValue)}") }

        sqlParts.add("GROUP BY ${groupBy.map { it.name.toSnakeCase() }.csv()}")

        orderBy?.let {
            sqlParts.add("ORDER BY ${orderBy.list.map { "${it.prop.name.toSnakeCase()} ${if (it.ascending) "ASC" else "DESC"}" }.csv()}")
        }
        limit?.let { sqlParts.add("LIMIT $limit") }

        return PreparedSql(sqlParts.joinToString(" "), where?.let { queryValues(where) } ?: emptyList())
    }

    fun <T: Any> toSql(q: ModelQuery<T>, toSqlValue: (Any?) -> String = this::toSqlValue): String = when {
        q is FieldIsNull<T, *> -> "${q.field.name.toSnakeCase()} IS NULL"
        q is FieldWithin<T, *> -> "${q.field.name.toSnakeCase()} in (${(q.value ?: emptySet()).map { toSqlValue(it) }.csv()})"
        q is FieldWithinComplex<T, *> -> "${q.field.name.toSnakeCase()} in (${(q.value ?: emptySet()).map { toSqlValue(it) }.csv()})"
        q is FieldLike<T> -> "${q.field.name.toSnakeCase()} ILIKE ${toSqlValue(q.value)}"
        q is FilterExpUnopLogic<T> -> "${q.op}(${toSql(q.exp, toSqlValue)})"

        q is FieldEqs<T, *> && q.value == null -> "${q.field.name.toSnakeCase()} IS NULL"
        q is SimpleFieldBinop<T, *> -> "${q.field.name.toSnakeCase()} ${q.op} ${toSqlValue(q.value)}"
        q is FilterExpAnd<T> -> "(${toSql(q.left, toSqlValue)}) ${q.op} (${toSql(q.right, toSqlValue)})"
        q is FilterExpOr<T> -> "(${toSql(q.left, toSqlValue)}) ${q.op} (${toSql(q.right, toSqlValue)})"

        q is FieldBinopOnSubselect<T, *> -> {
            val baseSubselect = "SELECT ${q.field.name.toSnakeCase()} FROM $tableName"
            "${q.field.name.toSnakeCase()} IN (${selectPreparedSql(baseSubselect, q.value).sql})"
        }

        q is DecoratedModelQuery<T> -> toSql(q.base, toSqlValue) // Ignore
        else -> throw NotImplementedError("Missing implementation of .toSql() for ${q}")
    }

    fun toSlotValue(value: Any?): String = "?"

    fun toSqlValue(value: Any?): String = when (value) {
        null -> "NULL"
        is Boolean, is Int, is Long, is Float, is Double -> value.toString()
        is Id -> "'${value.id}'"
        else -> "'${value.toString().replace("'", "''")}'" // Escape single quotes
    }


    fun valuesOf(t: T): List<Any?> = modelProperties
        .map { prop -> toDb(prop.annotations, prop.get(t)) }

    fun valuesOfForInsert(t: T): List<Any?> = modelPropertiesForInsert
        .map { prop -> toDb(prop.annotations, prop.get(t)) }

    fun <T: Any> queryValues(q: ModelQuery<T>): List<Any?> = when (q) {
        is FieldIsNull<T, *> -> emptyList()
        is FieldWithin<T, *> -> q.value?.map { toDb(q.field.annotations,it) } ?: emptyList()
        is FieldWithinComplex<T, *> -> q.value?.map { toDb(q.field.annotations,it) } ?: emptyList()
        is FilterExpUnopLogic<T> -> queryValues(q.exp)

        is SimpleFieldBinop<T, *> -> listOf(toDb(q.field.annotations,q.value))
        is FilterExpAnd<T> -> queryValues(q.left) + queryValues(q.right)
        is FilterExpOr<T> -> queryValues(q.left) + queryValues(q.right)

        is FieldBinopOnSubselect<T, *> -> queryValues(q.value)

        is DecoratedModelQuery<T> -> queryValues(q.base) // Ignore
        else -> throw NotImplementedError("Missing implementation of .toSql() for ${q}")
    }

    private inline fun toDb(annotations: List<Annotation>, value: Any?): Any? = serializationStrategy.toDatabaseValue(value, annotations)


    private fun String.toSnakeCase(): String = flatMap {
        if (it.isUpperCase()) listOf('_', it.toLowerCase()) else listOf(it)
    }.joinToString("")

    private fun List<String>.csv() = joinToString(", ")

}
