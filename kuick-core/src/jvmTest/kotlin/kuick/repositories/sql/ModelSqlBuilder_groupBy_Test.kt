package kuick.repositories.sql

import kuick.repositories.avg
import kuick.repositories.count
import kuick.repositories.desc
import kuick.repositories.gte
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSqlBuilder_groupBy_Test {

    data class User(val registerYear: String, val companyName: String, val name: String, val userAge: Int, val fingers: Int)

    val mq = ModelSqlBuilder(User::class, "user")

    @Test
    fun `group by columns are added both to the select and the group by clause`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql(
                sql = "SELECT company_name, register_year, COUNT(user_age), AVG(fingers) FROM user WHERE user_age >= ? GROUP BY company_name, register_year",
                values = listOf(0)
            ),
            mq.groupByPreparedSql(
                select = listOf(User::userAge.count(), User::fingers.avg()),
                groupBy = listOf(User::companyName, User::registerYear),
                where = User::userAge gte 0
            ))
    }

    @Test
    fun `limit and order by work as expected`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql(
                sql = "SELECT company_name, register_year, COUNT(user_age), AVG(fingers) FROM user GROUP BY company_name, register_year ORDER BY register_year DESC LIMIT 10",
                values = listOf()
            ),
            mq.groupByPreparedSql(
                select = listOf(User::userAge.count(), User::fingers.avg()),
                groupBy = listOf(User::companyName, User::registerYear),
                limit = 10,
                orderBy = User::registerYear.desc()
            ))
    }

}
