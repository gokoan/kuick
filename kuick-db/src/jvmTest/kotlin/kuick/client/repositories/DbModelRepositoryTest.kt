package kuick.client.repositories

import kotlinx.coroutines.*
import kuick.client.db.*
import kuick.client.jdbc.*
import kuick.repositories.*
import kotlin.test.*

class DbModelRepositoryTest {
    @Test
    fun test() {
        data class Demo(val id: String, val v: Int)

        runBlocking {
            DbClientPool(1) { JdbcDriver.connectMemoryH2() }.use { pool ->
                withContext(pool) {
                    val repo = DbModelRepository(Demo::class, Demo::id)
                    repo.init()
                    repo.insert(Demo("hello", 10))
                    repo.insert(Demo("hello", 20))
                    repo.insert(Demo("world", 30))
                    assertEquals(3, repo.getAll().size)
                    assertEquals(2, repo.findBy(Demo::id eq "hello").size)
                    assertEquals(1, repo.findBy(Demo::id eq "hello", limit = 1).size)
                    repo.update(Demo("world", 30).copy(v = 40))
                    assertEquals(Demo("world", 40), repo.findOneBy(Demo::id eq "world"))
                }
            }
        }
    }
}