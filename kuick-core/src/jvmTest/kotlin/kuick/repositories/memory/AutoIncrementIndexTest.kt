package kuick.repositories.memory

import kotlinx.coroutines.runBlocking
import kuick.models.Id
import kuick.repositories.gt
import kuick.repositories.sql.annotations.AutoIncrementIndex
import org.junit.Test
import kotlin.test.assertEquals

class AutoIncrementIndexTest {

    data class UserId(override val id: String) : Id

    data class User( @AutoIncrementIndex val userId: UserId, val name: String, val age: Int)

    val repo = ModelRepositoryMemory(User::class, User::userId)

    @Test
    fun `you can select a subset of columns from the given model`() = runBlocking {

        repo.insertMany(listOf(
            User(UserId("-1"), "User 1", 10),
            User(UserId("-1"), "User 2", 30),
            User(UserId("-1"), "User 3", 40),
        ))

        assertEquals(
            listOf(
                User(UserId("1"),"User 2", 30),
                User(UserId("2"),"User 3", 40),
            ),
            repo.findBy(User::age gt 20)
        )

    }

}
