package kuick.repositories.jasync

import kuick.models.Id
import kuick.repositories.eq
import kotlin.reflect.full.defaultType
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DefaultSerializationTest {

    data class UserId(override val id: String): Id
    data class UserData(val name: String, val age: Int)
    data class User(val userId: UserId, val data: UserData)

    val mq = ModelSqlBuilder(User::class, "user")
    val dss = mq.serializationStrategy

    val mikeUserId = UserId("mike")
    val mike = User(mikeUserId, UserData("Mike", 46))


    enum class TestEnum {
        ONE,
        TWO,
        THREE
    }

    @Test
    fun `IDs are properly serialized in WHERE clauses`() {
        assertEquals(
            "SELECT user_id, data FROM user WHERE user_id = 'mike'",
            mq.selectSql(User::userId eq mikeUserId)
        )

        assertEquals(
            ModelSqlBuilder.PreparedSql("SELECT user_id, data FROM user WHERE user_id = ?", listOf("mike")),
            mq.selectPreparedSql(User::userId eq mikeUserId)
        )
    }

    @Test
    fun `ID serialization`() {
        assertEquals(mikeUserId.id, dss.toDatabaseValue(mike.userId))
    }

    @Test
    fun `Object serialization`() {
        assertEquals("""{"name":"Mike","age":46}""", dss.toDatabaseValue(mike.data))
    }

    @Test
    fun `enum serialization`() {
        assertEquals("ONE", dss.toDatabaseValue(TestEnum.ONE))
        assertEquals("TWO", dss.toDatabaseValue(TestEnum.TWO))
        assertNotEquals("TWO", dss.toDatabaseValue(TestEnum.THREE))

    }

    @Test
    fun `enum de-serialization`() {
        assertEquals(TestEnum.THREE, dss.fromDatabaseValue(TestEnum::class.starProjectedType ,"THREE" ))
        assertEquals(TestEnum.THREE, dss.fromDatabaseValue(TestEnum::class.starProjectedType ,"\"THREE\"" ))
        assertNull(dss.fromDatabaseValue(TestEnum::class.starProjectedType ,"\"FOUR\""))
        assertNull(dss.fromDatabaseValue(TestEnum::class.starProjectedType ,"FOUR"))
    }

}
