package kuick.slackposter

import org.junit.Test
import kotlin.test.assertEquals


class SlackPosterTest {

    @Test
    fun `exception blocks works`() {
        val e = Exception("SlackPoster exception")
        val exceptionBlock = SlackPoster.exceptionBlock(e)

        assert(exceptionBlock.startsWith("\n```\njava.lang.Exception: SlackPoster exception"))
        assert(exceptionBlock.endsWith("\n```\n"))
    }

    @Test
    fun `code blocks works`() {
        assertEquals(
            """
                
                ```
                This is a code block
                ```
                
            """.trimIndent(),
            SlackPoster.codeBlock("This is a code block")
        )
    }

}
