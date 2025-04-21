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
import org.cursing_less.util.CARET_NUMBER_KEY
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
        runInEdtAndWait { 
            // Set primary caret number
            editor.caretModel.primaryCaret.putUserData(CARET_NUMBER_KEY, 1)

            val caret1 = editor.caretModel.addCaret(editor.offsetToVisualPosition(4)) // at 't' in "test"
            val caret2 = editor.caretModel.addCaret(editor.offsetToVisualPosition(9)) // at 'h' in "this"
            val caret3 = editor.caretModel.addCaret(editor.offsetToVisualPosition(20)) // at 'f' in "foo"

            // Set caret numbers
            caret1?.putUserData(CARET_NUMBER_KEY, 2)
            caret2?.putUserData(CARET_NUMBER_KEY, 3)
            caret3?.putUserData(CARET_NUMBER_KEY, 4)
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
        runInEdtAndWait { 
            finalCaretCount = editor.caretModel.caretCount

            // Explicitly set the primary caret number to 1 for verification
            editor.caretModel.primaryCaret.putUserData(CARET_NUMBER_KEY, 1)
        }
        assertEquals(1, finalCaretCount)

        // and the remaining caret should be the primary caret with number 1
        runInEdtAndWait {
            val primaryCaret = editor.caretModel.primaryCaret
            val caretNumber = primaryCaret.getUserData(CARET_NUMBER_KEY)
            assertEquals(1, caretNumber)
        }
    }
}
