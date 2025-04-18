package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.*
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.listener.CursingApplicationListener
import org.cursing_less.service.CursingCommandService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CursingSelectCommandTest {

    lateinit var projectTestFixture: IdeaProjectTestFixture
    lateinit var codeInsightFixture: CodeInsightTestFixture

    @BeforeEach
    fun setUp() {
        CursingApplicationListener.skipServer = true

        projectTestFixture =
            IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(LightProjectDescriptor(), "foo")
                .fixture

        codeInsightFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectTestFixture)
        codeInsightFixture.setUp()
    }

    @AfterEach
    fun tearDown() {
        runInEdtAndWait(true) {
            codeInsightFixture.tearDown()
        }
    }

    @Test
    fun testMatches() {
        // Test that the command matches the correct string
        assertTrue(CursingSelectCommand.matches("curse_select"))
        assertFalse(CursingSelectCommand.matches("curse_copy"))
        assertFalse(CursingSelectCommand.matches("select"))
    }

    @Test
    fun testRunWithInvalidParameters() = runBlocking {
        // Setup test environment
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor

        // Test with too few parameters
        val response1 = CursingSelectCommand.run(listOf("red", "circle"), project, editor)
        assertEquals(CursingCommandService.BadResponse, response1)

        // Test with null editor
        val response2 = CursingSelectCommand.run(listOf("red", "circle", "b"), project, null)
        assertEquals(CursingCommandService.BadResponse, response2)
    }

    @Test
    fun testWithComplexDocument() = runBlocking {
        // Setup a more complex document
        val complexText = """
            <html>
                <head>
                    <title>Test Document</title>
                </head>
                <body>
                    <h1>Hello World</h1>
                    <p>This is a paragraph with <b>bold</b> and <i>italic</i> text.</p>
                    <ul>
                        <li>Item 1</li>
                        <li>Item 2</li>
                        <li>Item 3</li>
                    </ul>
                </body>
            </html>
        """.trimIndent()

        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, complexText)
        val editor = codeInsightFixture.editor

        // Save initial selection state
        var initialSelectionStart = 0
        var initialSelectionEnd = 0
        runInEdtAndWait {
            initialSelectionStart = editor.selectionModel.selectionStart
            initialSelectionEnd = editor.selectionModel.selectionEnd
        }

        // Test with valid parameters but without actual markup
        // This just tests that the command can handle a complex document
        val response = CursingSelectCommand.run(listOf("1", "1", "h"), project, editor)

        // The response should be BadResponse since no markup is found
        assertEquals(CursingCommandService.BadResponse, response)

        // Verify that the selection hasn't changed (since no actual markup was found)
        var finalSelectionStart = 0
        var finalSelectionEnd = 0
        runInEdtAndWait {
            finalSelectionStart = editor.selectionModel.selectionStart
            finalSelectionEnd = editor.selectionModel.selectionEnd
        }
        assertEquals(initialSelectionStart, finalSelectionStart)
        assertEquals(initialSelectionEnd, finalSelectionEnd)
    }
}
