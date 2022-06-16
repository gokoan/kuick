package kuick.repositories.sql

import kuick.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class TypedListSerializationTest {

    data class Question(val questionId: String, val answers: List<Answer>)
    data class Answer(var right: Boolean, var answer: String)

    val mq = ModelSqlBuilder(Question::class, "user")
    val dss = mq.serializationStrategy

    val q = Question("q1", listOf(Answer(true, "V"), Answer(false, "F")))


    @Test
    fun `Typed List serialization`() {
        assertEquals(Json.toJson(q.answers), dss.toDatabaseValue(q.answers))
    }


    // ESTE TEST FALLA Y ES JUSTO LO QUE HAY QUE ARREGLAR!!!
    // ESTE TEST FALLA Y ES JUSTO LO QUE HAY QUE ARREGLAR!!!
    // ESTE TEST FALLA Y ES JUSTO LO QUE HAY QUE ARREGLAR!!!
    @Test
    fun `Typed List deserialization`() {
        assertEquals(q, dss.modelFromValues(Question::class.toModelReflectInfo(), listOf(q.questionId, Json.toJson(q.answers))))
    }

}
