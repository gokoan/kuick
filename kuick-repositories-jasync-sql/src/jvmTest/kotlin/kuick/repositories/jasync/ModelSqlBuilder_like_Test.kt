package kuick.repositories.jasync

import kuick.repositories.like
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSqlBuilder_like_Test {

    data class User(val name: String)
    val mq = ModelSqlBuilder(User::class, "user")


    @Test
    fun `like operator`() {
        assertEquals("name ILIKE '%mike'", mq.toSql(User::name like "%mike" ))
    }



}
