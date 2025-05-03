package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingRemoveAllSecondaryCursorsCommandTest {

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
        assertTrue(CursingRemoveAllSecondaryCursorsCommand.matches("curse_remove_all_secondary_cursors"))
        assertFalse(CursingRemoveAllSecondaryCursorsCommand.matches("curse_remove_cursor"))
    }

    @Test
    fun testRemoveAllSecondaryCursors() = runBlocking {
        // given a doc
        val text = "<!--test this works--><foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // Add multiple secondary carets
        val offset1 = 4  // at 't' in "test"
        val offset2 = 9  // at 'h' in "this"
        val offset3 = 20 // at 'f' in "foo"

        runInEdtAndWait {
            // Position the primary caret at the beginning
            editor.caretModel.primaryCaret.moveToOffset(0)

            // Add secondary carets at specific positions
            editor.caretModel.addCaret(editor.offsetToVisualPosition(offset1))
            editor.caretModel.addCaret(editor.offsetToVisualPosition(offset2))
            editor.caretModel.addCaret(editor.offsetToVisualPosition(offset3))
        }

        // Verify that we have 4 carets
        var initialCaretCount = 0
        runInEdtAndWait {
            initialCaretCount = editor.caretModel.caretCount
        }
        assertEquals(4, initialCaretCount)

        // When we run the remove all secondary cursors command
        val response = CursingRemoveAllSecondaryCursorsCommand.run(emptyList(), project, editor)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and there should be only one caret left (the primary caret)
        var finalCaretCount = 0
        var primaryCaretOffset = -1
        runInEdtAndWait {
            finalCaretCount = editor.caretModel.caretCount
            primaryCaretOffset = editor.caretModel.primaryCaret.offset
        }
        assertEquals(1, finalCaretCount)

        // The remaining caret should be at one of the offsets we created
        // In this case, it's at offset3 (20) which is the last caret we added
        assertEquals(offset3, primaryCaretOffset)
    }
}
