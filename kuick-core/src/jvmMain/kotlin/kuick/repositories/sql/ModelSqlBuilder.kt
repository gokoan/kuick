package kuick.repositories.sql

import kuick.models.Id
import kuick.repositories.*
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

    val modelColumns = modelFields.map { it.name.toSnakeCase() }

    protected val selectColumns = modelColumns.joinToString(", ")

    protected val insertColumns = selectColumns
    protected val insertValueSlots = modelColumns.map { "?" }.joinToString(", ")

    protected val updateColumns = modelColumns.map { "$it = ?" }.joinToString(", ")

    protected val selectBase = "SELECT $selectColumns FROM $tableName"

    fun selectSql(q: ModelQuery<T>) = "$selectBase WHERE ${toSql(q, this::toSqlValue)}"

    fun selectAll() = selectBase

    fun checkTableSchema() = "$selectBase LIMIT 1" // Select básico con 1 row máximo

    fun <A: Any> selectPreparedSql(q: ModelQuery<A>): PreparedSql = selectPreparedSql(selectBase, q)

    private fun <A: Any> selectPreparedSql(selectBase: String, q: ModelQuery<A>): PreparedSql {
        val base = PreparedSql("$selectBase WHERE ${toSql(q, this::toSlotValue)}", queryValues(q))
        val extraSql = mutableListOf<String>()

        q.tryGetAttributed()?.let { q ->
            if (q.orderBy != null) extraSql.add("ORDER BY ${q.orderBy!!.list.map { "${it.prop.name.toSnakeCase()} ${if (it.ascending) "ASC" else "DESC"}" }.joinToString(", ")}")
            if (q.skip > 0) extraSql.add("OFFSET ${q.skip}")
            if (q.limit != null) extraSql.add("LIMIT ${q.limit}")
        }

        return base.copy("${base.sql} ${extraSql.joinToString(" ")}".trim(), base.values)
    }

    val insertSql = "INSERT INTO $tableName ($insertColumns) VALUES ($insertValueSlots)"
    fun insertPreparedSql(t: T): PreparedSql =
        PreparedSql(insertSql, valuesOf(t))

    fun insertManySql(ts: Collection<T>): String =
        "INSERT INTO $tableName ($insertColumns) VALUES ${ts.map { "(${valuesOf(it).map { toSqlValue(it) }.joinToString(", ")})" }.joinToString(", ")}"

    fun updateSql(q: ModelQuery<T>) = "UPDATE $tableName SET $updateColumns WHERE ${toSql(q, this::toSqlValue)}"

    fun updatePreparedSql(t: T, q: ModelQuery<T>): PreparedSql =
        PreparedSql("UPDATE $tableName SET $updateColumns WHERE ${toSql(q, this::toSlotValue)}", valuesOf(t) + queryValues(q))


    fun updateManyPreparedSql(ts: Collection<Pair<T, ModelQuery<T>>>): String =
        ts.map { (t, q) ->
            "UPDATE $tableName " +
                "SET ${modelColumns.mapIndexed { index, s -> "$s = ${toSqlValue(valuesOf(t)[index])}" }.joinToString(", ")} " +
                "WHERE ${toSql(q, this::toSqlValue)};"
        }.joinToString (separator = " ")

    fun preparedAtomicUpdateSql(set: Map<KProperty1<T, *>, Any?>, incr: Map<KProperty1<T, Number>, Number>, where: ModelQuery<T>): PreparedSql {
        // Necesitamos listas para fijar un orden conocido
        val setPairs = set.entries.toList()
        val incPairs = incr.entries.toList()

        val setClause =
            setPairs.map { (p, v) -> "${p.name.toSnakeCase()} = ?" } +
            incPairs.map { (p, v) -> "${p.name.toSnakeCase()} = ${p.name.toSnakeCase()} + ?" }

        val setValues = setPairs.map { prepareToSetCommand(it.value) } + incPairs.map { prepareToSetCommand(it.value) }

        return PreparedSql("UPDATE $tableName SET ${setClause.joinToString(", ")} WHERE ${toSql(where, this::toSlotValue)}", setValues + queryValues(where))
    }

    private fun prepareToSetCommand(value: Any?) = if (value is Id) value.id else value

    fun deleteSql(q: ModelQuery<T>) = "DELETE FROM $tableName WHERE ${toSql(q, this::toSqlValue)}"

    fun deletePreparedSql(q: ModelQuery<T>): PreparedSql =
        PreparedSql("DELETE FROM $tableName WHERE ${toSql(q, this::toSlotValue)}", queryValues(q))

    fun countPreparedSql(q: ModelQuery<T>): PreparedSql =
        PreparedSql("SELECT COUNT(*) FROM $tableName WHERE ${toSql(q, this::toSlotValue)}", queryValues(q))


    fun groupByPreparedSql(select: List<GroupBy<T>>,
                           groupBy: List<KProperty1<T, *>>,
                           where: ModelQuery<T>): PreparedSql {
        val select = (groupBy.map { it.name.toSnakeCase() } +
            select.map { "${it.operator.name}(${it.prop.name.toSnakeCase()})" }
            ).joinToString(", ")

        return PreparedSql("SELECT $select FROM $tableName WHERE ${toSql(where, this::toSlotValue)}", queryValues(where))
    }

    fun <T: Any> toSql(q: ModelQuery<T>, toSqlValue: (Any?) -> String = this::toSqlValue): String = when {
        q is FieldIsNull<T, *> -> "${q.field.name.toSnakeCase()} IS NULL"
        q is FieldWithin<T, *> -> "${q.field.name.toSnakeCase()} in (${(q.value ?: emptySet()).map { toSqlValue(it) }.joinToString(", ")})"
        q is FieldWithinComplex<T, *> -> "${q.field.name.toSnakeCase()} in (${(q.value ?: emptySet()).map { toSqlValue(it) }.joinToString(", ")})"
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


    fun valuesOf(t: T): List<Any?> = modelProperties.map { prop -> toDb(prop.get(t)) }


    fun <T: Any> queryValues(q: ModelQuery<T>): List<Any?> = when (q) {
        is FieldIsNull<T, *> -> emptyList()
        is FieldWithin<T, *> -> q.value?.map { toDb(it) } ?: emptyList()
        is FieldWithinComplex<T, *> -> q.value?.map { toDb(it) } ?: emptyList()
        is FilterExpUnopLogic<T> -> queryValues(q.exp)

        is SimpleFieldBinop<T, *> -> listOf(toDb(q.value))
        is FilterExpAnd<T> -> queryValues(q.left) + queryValues(q.right)
        is FilterExpOr<T> -> queryValues(q.left) + queryValues(q.right)

        is FieldBinopOnSubselect<T, *> -> queryValues(q.value)

        is DecoratedModelQuery<T> -> queryValues(q.base) // Ignore
        else -> throw NotImplementedError("Missing implementation of .toSql() for ${q}")
    }

    private inline fun toDb(value: Any?): Any? = serializationStrategy.toDatabaseValue(value)


    private fun String.toSnakeCase(): String = flatMap {
        if (it.isUpperCase()) listOf('_', it.toLowerCase()) else listOf(it)
    }.joinToString("")

}
