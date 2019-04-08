package kuick.client.sql

abstract class SqlBuilder {
    object Iso : SqlBuilder()

    protected fun String.quoteGeneric(quoteChar: Char): String = buildString {
        val base = this@quoteGeneric
        append(quoteChar)
        for (n in 0 until base.length) {
            val c = base[n]
            when (c) {
                '\'' -> append("\\\'")
                '"' -> append("\\\"")
                else -> append(c)
            }
        }
        append(quoteChar)
    }

    open fun String.quoteTableName(): String = quoteGeneric('"')
    open fun String.quoteIdentifier(): String = quoteGeneric('"')
    open fun String.quoteStringLiteral(): String = quoteGeneric('\'')

    @JvmName("quoteTableNameExt") fun quoteTableName(str: String): String = str.quoteTableName()
    @JvmName("quoteStringLiteralExt") fun quoteStringLiteral(str: String): String = str.quoteStringLiteral()

    // Tables
    open fun sqlCreateTable(table: String): String = "CREATE TABLE ${table.quoteTableName()}();"
    open fun sqlDropTable(table: String): String = "DROP TABLE ${table.quoteTableName()};"

    // Columns
    open fun sqlAddColumn(table: String, column: String, type: String) = "ALTER TABLE ${table.quoteTableName()} ADD COLUMN ${column.quoteTableName()} $type;"
    open fun sqlDropColumn(table: String, column: String) = "ALTER TABLE ${table.quoteTableName()} DROP COLUMN ${column.quoteTableName()};"

    open fun sqlInsert(table: String, columns: List<String>): String = "INSERT INTO ${table.quoteTableName()} (${columns.joinToString(", ") { it.quoteTableName() }}) VALUES (${columns.joinToString(", ") { "?" }})"
    open fun sqlCreateIndex(table: String, columns: List<String>, unique: Boolean, index: String): String = buildString {
        append("CREATE ")
        if (unique) append("UNIQUE ")
        append("INDEX ").append(index.quoteTableName()).append(" ON ").append(table.quoteTableName())
        append(" (").append(columns.joinToString(", ") { it.quoteIdentifier() }).append(")")
    }
    open fun sqlDropIndex(table: String, index: String): String = "DROP INDEX ${index.quoteIdentifier()} ON ${table.quoteTableName()};"
}
