package kuick.repositories.sql

import kuick.repositories.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSqlBuilder_projection_Test {

    data class User(val registerYear: String, val companyName: String, val name: String, val userAge: Int, val fingers: Int)
    data class SubUser(val registerYear: String, val companyName: String, val name: String)

    val mq = ModelSqlBuilder(User::class, "user")

    @Test
    fun `you can select a subset of columns from the given model`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql(
                sql = "SELECT register_year, company_name, name FROM user WHERE user_age >= ?",
                values = listOf(0)
            ),
            mq.selectPreparedSql(
                User::userAge gte 0,
                SubUser::class
            ))
    }

}
