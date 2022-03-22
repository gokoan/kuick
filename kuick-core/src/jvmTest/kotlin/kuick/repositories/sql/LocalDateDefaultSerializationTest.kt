package kuick.repositories.sql

import kuick.models.Id
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalDateDefaultSerializationTest {

    data class UserId(override val id: String): Id
    data class User(val userId: UserId, val birth: LocalDate)

    val mq = ModelSqlBuilder(User::class, "user")
    val dss = mq.serializationStrategy

    val mikeUserId = UserId("mike")
    val mike = User(mikeUserId, LocalDate.of(1974, 6, 29))




    @Test
    fun `Object serialization`() {
        assertEquals("1974-06-29", dss.toDatabaseValue(mike.birth))
    }

}
