package kuick.repositories.sql

import kuick.repositories.eq
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSqlBuilder_escape_Test {

    data class User(val name: String)

    val mq = ModelSqlBuilder(User::class, "user")

    @Test
    fun `strings are property escaped in expressions`() {
        assertEquals("name = 'David L''Oreal'", mq.toSql(User::name eq "David L'Oreal"))
    }

}
