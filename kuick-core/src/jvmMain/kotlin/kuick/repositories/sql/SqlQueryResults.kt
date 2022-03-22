package kuick.repositories.sql

data class SqlQueryResults(val rowsAffected: Long, val rows: List<List<Any?>>)
