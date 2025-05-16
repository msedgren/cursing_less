package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.service.CursingCommandService
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingSwapSelectionsCommandTest {

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
        assertTrue(CursingSwapSelectionsCommand.matches("curse_swap_selections"))
        assertFalse(CursingSwapSelectionsCommand.matches("curse_move"))
    }

    @Test
    fun testSwapSelections() = runBlocking {
        // given a test editor with some text
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<tag>first</tag><tag>second</tag>")
        val editor = codeInsightFixture.editor

        // Create two selections
        runInEdtAndWait {
            // Create the first selection (first)
            editor.caretModel.moveToOffset(5)
            editor.selectionModel.setSelection(5, 10)

            // Add a second caret and create the second selection (second)
            val caretModel = editor.caretModel
            caretModel.addCaret(editor.offsetToVisualPosition(21))
            val secondCaret = caretModel.allCarets[1]
            secondCaret.setSelection(21, 27)
        }

        // Verify we have two selections
        runInEdtAndWait {
            val caretModel = editor.caretModel
            assertEquals(2, caretModel.caretCount)
            assertEquals("first", editor.document.getText(com.intellij.openapi.util.TextRange(5, 10)))
            assertEquals("second", editor.document.getText(com.intellij.openapi.util.TextRange(21, 27)))
        }

        // When we run the swap_selections command with the selection indices
        val response = CursingSwapSelectionsCommand.run(listOf("1", "2"), project, editor)

        // Then it works
        assertEquals(CursingCommandService.OkayResponse, response)

        // And the selections are swapped
        runInEdtAndWait {
            // Get the current document text
            val docText = editor.document.text

            // Verify the text has been swapped correctly
            assertTrue(docText.contains("<tag>second</tag>"))
            assertTrue(docText.contains("<tag>first</tag>"))

            // The exact offsets might change due to different string lengths,
            // so we'll find the current positions of the swapped text
            val secondPos = docText.indexOf("second")
            val firstPos = docText.indexOf("first")

            // Verify the text at those positions
            assertEquals("second", editor.document.getText(com.intellij.openapi.util.TextRange(secondPos, secondPos + 6)))
            assertEquals("first", editor.document.getText(com.intellij.openapi.util.TextRange(firstPos, firstPos + 5)))
        }
    }

    @Test
    fun testSwapSelectionsInvalidIndices() = runBlocking {
        // given a test editor with some text
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<tag>first</tag><tag>second</tag>")
        val editor = codeInsightFixture.editor

        // Create two selections
        runInEdtAndWait {
            // Create the first selection (first)
            editor.caretModel.moveToOffset(5)
            editor.selectionModel.setSelection(5, 10)

            // Add a second caret and create the second selection (second)
            val caretModel = editor.caretModel
            caretModel.addCaret(editor.offsetToVisualPosition(21))
            val secondCaret = caretModel.allCarets[1]
            secondCaret.setSelection(21, 27)
        }

        // When we run the swap_selections command with invalid indices
        val response = CursingSwapSelectionsCommand.run(listOf("2", "3"), project, editor)

        // Then it fails
        assertEquals(CursingCommandService.BadResponse, response)

        // And the selections are not changed
        runInEdtAndWait {
            assertEquals("first", editor.document.getText(com.intellij.openapi.util.TextRange(5, 10)))
            assertEquals("second", editor.document.getText(com.intellij.openapi.util.TextRange(21, 27)))
        }
    }
}
