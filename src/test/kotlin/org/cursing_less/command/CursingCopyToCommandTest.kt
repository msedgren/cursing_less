package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
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
import java.awt.datatransfer.DataFlavor

class CursingCopyToCommandTest {

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
        assertTrue(CursingCopyToCommand.matches("curse_copy_to"))
        assertFalse(CursingCopyToCommand.matches("curse_move"))
    }

    @Test
    fun testCopyTo() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // Position caret at the start of "bar"
        runInEdtAndWait {
            editor.caretModel.moveToOffset(5)
        }

        //and we can get the shape and color at offset 5 (tied to bar)
        val colorAndShape = CursingTestUtils.getCursingColorShape(editor, 5)
        assertNotNull(colorAndShape)
        // map to numbers
        val cursingPreferenceService = ApplicationManager.getApplication().service<CursingPreferenceService>()
        val colorNumber = cursingPreferenceService.mapToCode(colorAndShape!!.color)
        val shapeNumber = cursingPreferenceService.mapToCode(colorAndShape.shape)

        // when we run the copy_to command with the numbers and character
        val response = CursingCopyToCommand.run(listOf("copy", "$colorNumber", "$shapeNumber", "b"), project, editor)

        // it works
        assertEquals(CursingCommandService.OkayResponse, response)
        // and we can get the copied text
        val copiedText: Object? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
        assertEquals("bar", copiedText)
    }

    @Test
    fun testCutTo() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // Position caret at the start of "bar"
        runInEdtAndWait {
            editor.caretModel.moveToOffset(5)
        }

        //and we can get the shape and color at offset 5 (tied to bar)
        val colorAndShape = CursingTestUtils.getCursingColorShape(editor, 5)
        assertNotNull(colorAndShape)
        // map to numbers
        val cursingPreferenceService = ApplicationManager.getApplication().service<CursingPreferenceService>()
        val colorNumber = cursingPreferenceService.mapToCode(colorAndShape!!.color)
        val shapeNumber = cursingPreferenceService.mapToCode(colorAndShape.shape)

        // when we run the cut_to command with the numbers and character
        val response = CursingCopyToCommand.run(listOf("cut", "$colorNumber", "$shapeNumber", "b"), project, editor)

        // it works
        assertEquals(CursingCommandService.OkayResponse, response)
        // and we can get the cut text
        val copiedText: Object? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
        assertEquals("bar", copiedText)
        // and the text is removed from document
        assertEquals("<foo></foo>", editor.document.text)
    }
}