package kuick.repositories.sql

import kuick.models.Id
import kuick.repositories.annotations.DbName
import kuick.repositories.annotations.Index
import kuick.repositories.eq
import kuick.repositories.sql.annotations.AsArray
import java.util.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
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


    data class TestArrays(
        val id: String,
        @AsArray val values: List<UUID>)


    val dataWithArray = TestArrays("someId",listOf(
        UUID.fromString("a931017b-1eea-4356-9460-0a644f0be4ee"),
        UUID.fromString("a931017b-1eea-4356-9460-0a644f0be4ee")))

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
    fun `Array serialization`() {
        val data = dss.toDatabaseValue(dataWithArray.values, TestArrays::values.annotations)
        assertEquals("""{"a931017b-1eea-4356-9460-0a644f0be4ee","a931017b-1eea-4356-9460-0a644f0be4ee"}""",data)
    }


    @Test
    fun `enum serialization`() {
        assertEquals("ONE", dss.toDatabaseValue(TestEnum.ONE))
        assertEquals("TWO", dss.toDatabaseValue(TestEnum.TWO))
        assertNotEquals("TWO", dss.toDatabaseValue(TestEnum.THREE))

    }

    @Test
    fun `enum de-serialization`() {
        assertEquals(TestEnum.THREE, dss.fromDatabaseValue(TestEnum::class.starProjectedType.toReflectInfo() ,"THREE" ))
        assertEquals(TestEnum.THREE, dss.fromDatabaseValue(TestEnum::class.starProjectedType.toReflectInfo() ,"\"THREE\"" ))
        assertNull(dss.fromDatabaseValue(TestEnum::class.starProjectedType.toReflectInfo() ,"\"FOUR\""))
        assertNull(dss.fromDatabaseValue(TestEnum::class.starProjectedType.toReflectInfo() ,"FOUR"))
    }

}
