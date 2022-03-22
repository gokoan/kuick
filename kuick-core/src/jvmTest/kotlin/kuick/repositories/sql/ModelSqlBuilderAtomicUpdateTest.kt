package kuick.repositories.sql

import kuick.repositories.eq
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSqlBuilderAtomicUpdateTest {

    data class User(val userId: String, val userName: String, val age: Int)

    val mq = ModelSqlBuilder(User::class, "user")

    val mikeUserId = "mike"

    @Test
    fun `UPDATE atómico con seteo parcial`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql(
                "UPDATE user SET user_name = ? WHERE user_id = ?",
                listOf("Mike", "mike")
            ),
            mq.preparedAtomicUpdateSql(
                set = mapOf(User::userName to "Mike"),
                incr = mapOf(),
                where = User::userId eq mikeUserId)
        )
    }

    @Test
    fun `UPDATE atómico con incremento`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql(
                "UPDATE user SET age = age + ? WHERE user_id = ?",
                listOf(3, "mike")
            ),
            mq.preparedAtomicUpdateSql(
                set = mapOf(),
                incr = mapOf(User::age to 3),
                where = User::userId eq mikeUserId)
        )
    }

    @Test
    fun `UPDATE atómico combinado`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql(
                "UPDATE user SET user_name = ?, age = age + ? WHERE user_id = ?",
                listOf("Mike", 3, "mike")
            ),
            mq.preparedAtomicUpdateSql(
                set = mapOf(User::userName to "Mike"),
                incr = mapOf(User::age to 3),
                where = User::userId eq mikeUserId)
        )
    }

    @Test
    fun `UPDATEMANY`() {

        val users = listOf(
            User("id0", "name0", 0),
            User("id1", "name1", 1),
        )

        assertEquals(
            "UPDATE user SET user_id = 'id0', user_name = 'name0', age = 0 WHERE user_id = 'id0'; " +
            "UPDATE user SET user_id = 'id1', user_name = 'name1', age = 1 WHERE user_id = 'id1';",
            mq.updateManyPreparedSql(users.map { it to (User::userId eq it.userId) })
        )
    }




}
