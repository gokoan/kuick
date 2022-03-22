package kuick.repositories.sql

import kuick.models.Id
import kuick.repositories.eq
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSqlBuilder_Id_Test {

    private val mq = ModelSqlBuilder(User2::class, "user")

    data class UserId(override val id: String): Id
    data class User2(val id: UserId, val name: String)

    val mikeUserId = UserId("mike")

    @Test
    fun `ID extraction`() {
        assertEquals(
            "SELECT id, name FROM user WHERE id = 'mike'",
            mq.selectSql(User2::id eq mikeUserId)
        )

        assertEquals(
            ModelSqlBuilder.PreparedSql("SELECT id, name FROM user WHERE id = ?", listOf("mike")),
            mq.selectPreparedSql(User2::id eq mikeUserId)
        )
    }


    @Test
    fun `IDs in expressions should map to strings`() {
        assertEquals(
            "id = 'mike'",
            mq.toSql(User2::id eq UserId("mike"))
        )
    }

    @Test
    fun `prepared insert`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql("INSERT INTO user (id, name) VALUES (?, ?)",
                listOf("mikeId", "Mike")),
            mq.insertPreparedSql(User2(UserId("mikeId"), "Mike")))
    }

    @Test
    fun `prepared update`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql("UPDATE user SET id = ?, name = ? WHERE id = ?",
                listOf("mikeId", "Mike2", "mikeId")),
            mq.updatePreparedSql(User2(UserId("mikeId"), "Mike2"), User2::id eq UserId("mikeId")))
    }

    @Test
    fun `prepared delete`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql("DELETE FROM user WHERE id = ?",
                listOf("mikeId")),
            mq.deletePreparedSql(User2::id eq UserId("mikeId")))
    }

}
