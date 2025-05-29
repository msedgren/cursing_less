package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingPreferenceService
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingSwapTokensCommandTest {

    lateinit var projectTestFixture: IdeaProjectTestFixture
    lateinit var codeInsightFixture: CodeInsightTestFixture

    @BeforeEach
    fun setUp() {
        codeInsightFixture = CursingTestUtils.setupTestFixture()
    }

    @AfterEach
    fun tearDown() {
        CursingTestUtils.tearDownTestFixture(codeInsightFixture)
    }

    @Test
    fun testMatches() {
        // Test that the command matches the correct string
        assertTrue(CursingSwapTokensCommand.matches("curse_swap_tokens"))
        assertFalse(CursingSwapTokensCommand.matches("curse_move"))
    }

    @Test
    fun testSwapTokens() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo><baz>qux</baz>")
        val editor = codeInsightFixture.editor

        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // Find the first token (bar)
        val firstToken = CursingTestUtils.getCursingColorShape(editor, 5)
        assertNotNull(firstToken)

        // Find the second token (qux)
        val secondToken = CursingTestUtils.getCursingColorShape(editor, 15)
        assertNotNull(secondToken)

        // When we run the swap_tokens command with the token parameters
        val response = CursingSwapTokensCommand.run(
            listOf(
                firstToken!!.color.name, firstToken.shape.name, "b",
                secondToken!!.color.name, secondToken.shape.name, "b"
            ), 
            project, 
            editor
        )

        // Then it works
        assertEquals(CursingCommandService.OkayResponse, response)

        // And the tokens are swapped
        runInEdtAndWait {
            // Check that "bar" and "baz" have been swapped
            assertEquals("<foo>baz</foo><bar>qux</baz>", editor.document.text)
        }
    }

    @Test
    fun testSwapTokensInvalidParameters() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo><baz>qux</baz>")
        val editor = codeInsightFixture.editor

        // When we run the swap_tokens command with invalid parameters (wrong number of parameters)
        val response = CursingSwapTokensCommand.run(
            listOf("blue", "square", "f", "blue", "circle"), // Missing one parameter
            project, 
            editor
        )

        // Then it fails
        assertEquals(CursingCommandService.BadResponse, response)
    }

    @Test
    fun testSwapTokensNonExistentTokens() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo><baz>qux</baz>")
        val editor = codeInsightFixture.editor

        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // When we run the swap_tokens command with non-existent tokens
        val response = CursingSwapTokensCommand.run(
            listOf("blue", "square", "f", "red", "circle", "y"),
            project, 
            editor
        )

        // Then it fails
        assertEquals(CursingCommandService.BadResponse, response)

        // And the document is unchanged
        runInEdtAndWait {
            assertEquals("<foo>bar</foo><baz>qux</baz>", editor.document.text)
        }
    }
}
