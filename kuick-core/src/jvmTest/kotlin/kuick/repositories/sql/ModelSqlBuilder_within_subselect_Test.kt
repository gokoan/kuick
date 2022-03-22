package kuick.repositories.sql

import kuick.models.Id
import kuick.repositories.AttributedModelQuery
import kuick.repositories.eq
import kuick.repositories.within
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSqlBuilder_within_subselect_Test {

    data class MessageId(override val id: String): Id
    data class TestQueue(val id: MessageId, val state: String, val data: String, val batchId: String)
    val mq = ModelSqlBuilder(TestQueue::class, "koan_queue")


    @Test
    fun `prepared select within subselect`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql("SELECT id, state, data, batch_id FROM koan_queue WHERE id IN (SELECT id FROM koan_queue WHERE state = ? LIMIT 2)", listOf("PROCESSING")),
            mq.selectPreparedSql(TestQueue::id within AttributedModelQuery(TestQueue::state eq  "PROCESSING", limit = 2) )
        )
    }

    @Test
    fun `prepared update within subselect`() {
        assertEquals(
            ModelSqlBuilder.PreparedSql("UPDATE koan_queue SET state = ? WHERE id IN (SELECT id FROM koan_queue WHERE state = ? LIMIT 2)", listOf("PROCESSING", "PENDING")),
            mq.preparedAtomicUpdateSql(
                set = mapOf(
                    TestQueue::state to "PROCESSING"
                ),
                incr = mapOf(),
                where = TestQueue::id within AttributedModelQuery(TestQueue::state eq "PENDING", limit = 2)
            )
        )
    }


}
