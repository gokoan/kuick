package kuick.slackposter

import kuick.json.Json
import java.io.PrintWriter
import java.io.StringWriter

class SlackPoster(
    val environment: String,
    val httpJsonPoster: IHttpJsonPoster,
) {

    suspend fun post(message: String, webHook: String) {
        val _message = "[`$environment`] $message"
        val messageBlock =
            SlackMessageBlock(type = "section", text = SlackMessageText(type = "mrkdwn", text = _message))
        val slackMessage = SlackMessage(text = _message, blocks = listOf(messageBlock))
        val jsonBody = Json.toJson(slackMessage)
        try {
            httpJsonPoster.post(webHook, jsonBody)
        } catch (ex: Exception) {
            System.err.println("----------------------")
            System.err.println("ERROR sending to Slack markdown message:\n$_message")
            System.err.println("------")
            System.err.println("Sent JSON with blocks:\n$jsonBody")
            ex.printStackTrace()
            System.err.println("----------------------")
        }
    }

    companion object {

        fun codeBlock(code: String) = "\n```\n$code\n```\n"

        fun limitedCodeBlock(code: String, limit: Int = 1_000) =
            codeBlock(code.take(limit / 2) + "\n\n\n[...REMOVED LONG CODE...]\n\n\n" + code.takeLast(limit / 2))

        fun exceptionBlock(exception: Throwable): String {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)

            return codeBlock(sw.toString())
        }

    }
}
