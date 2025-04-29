package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.*
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingPreferenceService
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CursingRemoveCursorCommandTest {

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
        assertTrue(CursingRemoveCursorCommand.matches("curse_remove_cursor"))
        assertFalse(CursingRemoveCursorCommand.matches("curse_add_cursor"))
    }

    @Test
    fun testRemoveCursor() = runBlocking {
        // given a doc
        val text = "<!--test this works--><foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // Add two secondary carets
        val offset1 = 4  // at 't' in "test"
        val offset2 = 20 // at 'f' in "foo"
        
        runInEdtAndWait { 
            // Add secondary carets at specific positions
            editor.caretModel.addCaret(editor.offsetToVisualPosition(offset1))
            editor.caretModel.addCaret(editor.offsetToVisualPosition(offset2))
        }

        // Verify that we have 3 carets
        var initialCaretCount = 0
        runInEdtAndWait { 
            initialCaretCount = editor.caretModel.caretCount
        }
        assertEquals(3, initialCaretCount)

        // When we run the remove cursor command with the index 2 (second caret)
        val response = CursingRemoveCursorCommand.run(listOf("2"), project, editor)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and there should be one less caret
        var finalCaretCount = 0
        runInEdtAndWait { 
            finalCaretCount = editor.caretModel.caretCount
        }
        assertEquals(2, finalCaretCount)

        // and the remaining carets should be at the expected positions
        runInEdtAndWait {
            val remainingCarets = editor.caretModel.allCarets
            val caretOffsets = remainingCarets.map { it.offset }.sorted()
            
            // The primary caret is at position 0, and the caret at offset2 should remain
            assertTrue(caretOffsets.contains(0))
            assertTrue(caretOffsets.contains(offset2))
            assertFalse(caretOffsets.contains(offset1))
        }
    }
}