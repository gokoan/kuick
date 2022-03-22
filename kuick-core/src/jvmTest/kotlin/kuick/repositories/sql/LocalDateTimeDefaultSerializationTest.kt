package kuick.repositories.sql

import kuick.models.Id
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalDateTimeDefaultSerializationTest {

    data class UserId(override val id: String): Id
    data class User(val userId: UserId, val birth: LocalDateTime)

    val mq = ModelSqlBuilder(User::class, "user")
    val dss = mq.serializationStrategy

    @Test
    fun `Local date time serialization`() {
        assertEquals("1974-06-29T10:00:00", dss.toDatabaseValue(LocalDateTime.of(1974, 6, 29, 10, 0,0)))
    }

}
