package kuick.samples.todo

import com.google.inject.*
import com.soywiz.korio.file.std.*
import com.soywiz.korte.*
import com.soywiz.korte.ktor.Korte
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kuick.client.db.*
import kuick.client.jdbc.*
import kuick.client.repositories.*
import kuick.di.*
import kuick.ktor.*
import kuick.models.*
import kuick.repositories.annotations.*
import kuick.repositories.patterns.*
import kuick.utils.*

suspend fun main(args: Array<String>) {
    embeddedServer(Netty, port = 8080) { module() }.start(wait = true)
}

fun Application.installKorte(templates: Templates) = run { install(Korte) { this.templates = templates } }

fun Application.module() {
    val injector = Guice {
        bindPerCoroutineJob()
    }

    val templates = Templates(resourcesVfs["templates"])

    installKorte(templates)
    installContextPerRequest(injector, DbClientPool { JdbcDriver.connectMemoryH2() }) { Todo.CachedRepository.init() }
    installHttpExceptionsSupport()

    kuickRouting {
        get("/sample-html") { "Hello world!" }
        get("/sample-txt") { "Hello world!".withContentType(ContentType.Text.Plain) }
        get("/") {
            templates.render("index.html", StandardModel(injector))
        }
        get("/remove/{id}") {
            val param = param("id")
            Todo.CachedRepository.delete(Todo.Id(param))
            redirect("/")
        }
        post("/") {
            val item = post("item")
            Todo.CachedRepository.insert(Todo(Todo.Id(), item))
            redirect("/")
        }
    }
}

@Suppress("unused")
open class StandardModel(val injector: Injector) {
    suspend fun allTodos() = Todo.CachedRepository.getAll()
}

data class Todo(
        val id: Id,
        @MaxLength(512) val text: String
) {
    @Suppress("unused")
    fun removeLink() = "/remove/$id"

    class Id(id: String = randomUUID()) : AbstractId(id)

    companion object {
        val Repository = DbModelRepository(Todo::id)
        val CachedRepository = Repository.cached(MemoryCache())
    }
}
