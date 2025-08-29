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
import org.cursing_less.util.CursingTestUtils.completeProcessing
import org.cursing_less.util.CursingTestUtils.getCursingColorShape
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingAddCursorCommandTest {

    lateinit var projectTestFixture: IdeaProjectTestFixture
    lateinit var codeInsightFixture: CodeInsightTestFixture

    @BeforeEach
    fun setUp() {
        val (projectTestFixture, codeInsightFixture) = CursingTestUtils.setupTestFixture()
        this.projectTestFixture = projectTestFixture
        this.codeInsightFixture = codeInsightFixture
    }

    @AfterEach
    fun tearDown() {
        CursingTestUtils.tearDownTestFixture(projectTestFixture, codeInsightFixture)
    }

    @Test
    fun testMatches() {
        // Test that the command matches the correct string
        assertTrue(CursingAddCursorCommand.matches("curse_add_cursor"))
        assertFalse(CursingAddCursorCommand.matches("curse_select"))
    }

    @Test
    fun testAddCursor() = runBlocking {
        // given a doc
        val text = "<!--test this works--><foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokens(editor, 0)
        completeProcessing()

        // and we can get the shape and color at offset 4 (test)
        val offset = 4
        val character = 't'
        val colorAndShape = getCursingColorShape(editor, offset)
        assertNotNull(colorAndShape)

        // When we run the add cursor command with the numbers and character
        val response =
            CursingAddCursorCommand.run(listOf("${colorAndShape?.color?.name}", "${colorAndShape?.shape?.name}", "$character"), project, editor)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and there should be a secondary caret at the expected position
        var caretCount = 0
        var caretOffset = 0
        runInEdtAndWait {
            caretCount = editor.caretModel.caretCount
            // Get the offset of the last added caret
            if (caretCount > 1) {
                caretOffset = editor.caretModel.allCarets.last().offset
            }
        }

        // Verify that a secondary caret was added
        assertEquals(2, caretCount)
        // Verify that the caret is at the expected position
        assertEquals(offset, caretOffset)
    }
}
