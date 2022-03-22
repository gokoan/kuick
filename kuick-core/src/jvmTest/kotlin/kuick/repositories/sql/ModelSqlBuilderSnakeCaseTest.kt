package kuick.repositories.sql

import kuick.models.Id
import kuick.repositories.eq
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSqlBuilderSnakeCaseTest {

    data class UserId(override val id: String): Id
    data class User(val userId: UserId, val name: String)

    val mq = ModelSqlBuilder(User::class, "user")

    val mikeUserId = UserId("mike")

    @Test
    fun `snake case in WHERE clauses`() {
        assertEquals("user_id = 'mike'", mq.toSql(User::userId eq mikeUserId))
    }

    @Test
    fun `snake case in SELECT clause`() {
        assertEquals("SELECT user_id, name FROM user WHERE user_id = 'mike'", mq.selectSql(User::userId eq mikeUserId))
    }

    @Test
    fun `snake case in UPDATE clause`() {
        assertEquals("UPDATE user SET user_id = ?, name = ? WHERE user_id = 'mike'", mq.updateSql(User::userId eq mikeUserId))
    }

    @Test
    fun `snake case in DELETE clause`() {
        assertEquals("DELETE FROM user WHERE user_id = 'mike'", mq.deleteSql(User::userId eq mikeUserId))
    }

}
