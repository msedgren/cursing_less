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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingSelectToCommandTest {

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
        assertTrue(CursingSelectToCommand.matches("curse_select_to"))
        assertFalse(CursingSelectToCommand.matches("curse_move"))
    }

    @Test
    fun testSelectTo() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor
        // and the caret is positioned to the start of "oo>bar"
        runInEdtAndWait {
            editor.caretModel.moveToOffset(2)
        }
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)


        //and we can get the shape and color at offset 5 (tied to bar)
        val colorAndShape = CursingTestUtils.getCursingColorShape(editor, 5)
        assertNotNull(colorAndShape)
        // map to numbers
        val cursingPreferenceService = ApplicationManager.getApplication().service<CursingPreferenceService>()
        val colorNumber = cursingPreferenceService.mapToCode(colorAndShape!!.color)
        val shapeNumber = cursingPreferenceService.mapToCode(colorAndShape.shape)

        // when we run the select_to command with the numbers and character
        val response = CursingSelectToCommand.run(listOf("$colorNumber", "$shapeNumber", "b"), project, editor)

        // it works
        assertEquals(CursingCommandService.OkayResponse, response)
        // and the text is selected
        var selectedText = ""
        runInEdtAndWait { selectedText = editor.selectionModel.selectedText ?: "" }
        assertEquals("oo>bar", selectedText)
    }

}
