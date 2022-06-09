package kuick.repositories.sql

import kotlinx.coroutines.runBlocking
import kuick.models.Id
import kuick.repositories.gt
import kuick.repositories.sql.annotations.AsNumericId
import kuick.repositories.sql.annotations.AutoIncrementIndex
import org.junit.Test
import kotlin.test.assertEquals

class ModelSqlBuilder_autoincrement_Test {

    data class UserId(override val id: String) : Id

    data class User( @AutoIncrementIndex val userId: UserId, val name: String, val age: Int)

    val mq = ModelSqlBuilder(User::class, "data_array")

    @Test
    fun `correct autoincrement index sql build`() = runBlocking {

        assertEquals(
            ModelSqlBuilder.PreparedSql("INSERT INTO data_array (name, age) VALUES (?, ?)",
                listOf("Mike", 46)),
            mq.insertPreparedSql(User( UserId("-1"),"Mike", 46)))



    }

}
