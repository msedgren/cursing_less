package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingPreferenceService
import org.cursing_less.util.CursingTestUtils
import org.cursing_less.command.TokenPosition
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.datatransfer.DataFlavor

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

        // when we run the select_to command with the numbers and character
        val response =
            CursingSelectToCommand.run(listOf("select", "${colorAndShape?.color?.name}", "${colorAndShape?.shape?.name}", "b"), project, editor)

        // it works
        assertEquals(CursingCommandService.OkayResponse, response)
        // and the text is selected
        var selectedText = ""
        runInEdtAndWait { selectedText = editor.selectionModel.selectedText ?: "" }
        assertEquals("oo>bar", selectedText)
    }

    @Test
    fun testCopyTo() = runBlocking {
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

        // when we run the copy_to command with the numbers and character
        val response = CursingSelectToCommand.run(listOf("copy", "${colorAndShape?.color?.name}", "${colorAndShape?.shape?.name}", "b"), project, editor)

        // it works
        assertEquals(CursingCommandService.OkayResponse, response)
        // and we can get the copied text
        val copiedText: Object? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
        assertEquals("oo>bar", copiedText)
    }

    @Test
    fun testCutTo() = runBlocking {
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

        // when we run the cut_to command with the numbers and character
        val response = CursingSelectToCommand.run(listOf("cut", "${colorAndShape?.color?.name}", "${colorAndShape?.shape?.name}", "b"), project, editor)

        // it works
        assertEquals(CursingCommandService.OkayResponse, response)
        // and we can get the cut text
        val copiedText: Object? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
        assertEquals("oo>bar", copiedText)
        // and the text is removed from document
        assertEquals("<f</foo>", editor.document.text)
    }

    @Test
    fun testClearTo() = runBlocking {
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

        // when we run the clear_to command with the numbers and character
        val response = CursingSelectToCommand.run(listOf("clear", "${colorAndShape?.color?.name}", "${colorAndShape?.shape?.name}", "b"), project, editor)

        // it works
        assertEquals(CursingCommandService.OkayResponse, response)
        // We don't check the clipboard content for clear mode
        // Just verify that the text is removed from the document
        assertEquals("<f</foo>", editor.document.text)
    }

    @Test
    fun testSelectToWithBefore() = runBlocking {
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

        // when we run the select_to command with the numbers, character, and START.alternativeValue parameter
        val response = CursingSelectToCommand.run(
            listOf("select", TokenPosition.START.code, "${colorAndShape?.color?.name}", "${colorAndShape?.shape?.name}", "b"),
            project, 
            editor
        )

        // it works
        assertEquals(CursingCommandService.OkayResponse, response)
        // and the text is selected up to but not including the start of the token
        var selectedText = ""
        runInEdtAndWait { selectedText = editor.selectionModel.selectedText ?: "" }
        assertEquals("oo>", selectedText)
    }

    @Test
    fun testSelectToWithAfter() = runBlocking {
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

        // when we run the select_to command with the numbers, character, and END.code parameter
        val response = CursingSelectToCommand.run(
            listOf("select", TokenPosition.END.code, "${colorAndShape?.color?.name}", "${colorAndShape?.shape?.name}", "b"),
            project, 
            editor
        )

        // it works
        assertEquals(CursingCommandService.OkayResponse, response)
        // and the text is selected up to and including the first character of the token
        var selectedText = ""
        runInEdtAndWait { selectedText = editor.selectionModel.selectedText ?: "" }
        assertEquals("oo>bar", selectedText)
    }
}
