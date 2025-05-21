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

class CursingMoveCommandTest {

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
        assertTrue(CursingMoveCommand.matches("curse_to_location"))
        assertFalse(CursingMoveCommand.matches("curse_copy"))
    }

    @Test
    fun testMove() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // Save initial caret position
        var initialCaretPosition = 0
        runInEdtAndWait {
            initialCaretPosition = editor.caretModel.offset
        }
        assertEquals(0, initialCaretPosition)

        //and we can get the shape and color at offset 5 (tied to bar)
        val colorAndShape = CursingTestUtils.getCursingColorShape(editor, 5)
        assertNotNull(colorAndShape)

        // Test with "pre" parameter (move to start of token)
        val preResponse = CursingMoveCommand.run(listOf("pre", "${colorAndShape?.color?.name}", "${colorAndShape?.shape?.name}", "b"), project, editor)
        assertEquals(CursingCommandService.OkayResponse, preResponse)

        // Verify that the caret position has changed to the start of "bar"
        var preCaretPosition = 0
        runInEdtAndWait {
            preCaretPosition = editor.caretModel.offset
        }
        assertEquals(5, preCaretPosition) // Position of 'b' in "<foo>bar</foo>"

        // Test with "post" parameter (move to end of token)
        val postResponse = CursingMoveCommand.run(listOf("post", "${colorAndShape?.color?.name}", "${colorAndShape?.shape?.name}", "b"), project, editor)
        assertEquals(CursingCommandService.OkayResponse, postResponse)

        // Verify that the caret position has changed to the end of "bar"
        var postCaretPosition = 0
        runInEdtAndWait {
            postCaretPosition = editor.caretModel.offset
        }
        assertEquals(8, postCaretPosition) // Position after 'r' in "<foo>bar</foo>"
    }
}