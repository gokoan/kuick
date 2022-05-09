package kuick.repositories.sql

import kuick.repositories.avg
import kuick.repositories.count
import kuick.repositories.gte
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSqlBuilder_groupBy_Test {

    data class User(val companyName: String, val name: String, val userAge: Int, val fingers: Int)

    val mq = ModelSqlBuilder(User::class, "user")

    @Test
    fun `strings are property escaped in expressions`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql(
                sql = "SELECT company_name, COUNT(user_age), AVG(fingers) FROM user WHERE user_age >= ?",
                values = listOf(0)
            ),
            mq.groupByPreparedSql(
                select = listOf(User::userAge.count(), User::fingers.avg()),
                groupBy = listOf(User::companyName),
                where = User::userAge gte 0
            ))
    }

}
