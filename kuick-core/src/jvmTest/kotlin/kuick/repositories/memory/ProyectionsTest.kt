package kuick.repositories.memory

import kotlinx.coroutines.runBlocking
import kuick.repositories.gt
import kotlin.test.Test
import kotlin.test.assertEquals

class ProyectionsTest {

    data class User(val userId: String, val name: String, val age: Int)
    data class SubUser(val userId: String, val name: String)

    val repo = ModelRepositoryMemory(User::class, User::userId)

    @Test
    fun `you can select a subset of columns from the given model`() = runBlocking {

        repo.insertMany(listOf(
            User("user1", "User 1", 10),
            User("user2", "User 2", 30),
            User("user3", "User 3", 40),
        ))

        assertEquals(
            listOf(
                SubUser("user2", "User 2"),
                SubUser("user3", "User 3"),
            ),
            repo.findProyectionBy(SubUser::class, where = User::age gt 20)
        )

    }

}
