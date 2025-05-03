package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkStorageService
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingPasteMarkCommandTest {

    lateinit var projectTestFixture: IdeaProjectTestFixture
    lateinit var codeInsightFixture: CodeInsightTestFixture

    @BeforeEach
    fun setUp() {
        codeInsightFixture = CursingTestUtils.setupTestFixture()
    }

    @AfterEach
    fun tearDown() {
        CursingTestUtils.tearDownTestFixture(codeInsightFixture)

        // Clear any stored marked text
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        markStorageService.clearAllMarkedText()
    }

    @Test
    fun testMatches() {
        // Test that the command matches the correct string
        assertTrue(CursingPasteMarkCommand.matches("curse_paste_mark"))
        assertFalse(CursingPasteMarkCommand.matches("curse_mark"))
    }

    @Test
    fun testPasteMark() = runBlocking {
        // given a doc
        val text = "<foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor

        // and a marked text stored in the mark storage service
        val markNumber = 1
        val markedText = "test"
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        markStorageService.storeMarkedText(markNumber, markedText, 0)

        // Position the cursor at a specific location
        val cursorOffset = 5 // before "bar"
        runInEdtAndWait {
            editor.caretModel.moveToOffset(cursorOffset)
        }

        // When we run the paste mark command with a mark number
        val response = CursingPasteMarkCommand.run(listOf(markNumber.toString()), project, editor)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and the marked text should be inserted at the cursor position
        val expectedText = "<foo>testbar</foo>"
        assertEquals(expectedText, editor.document.text)

        // and the cursor should be positioned after the inserted text
        val actualCursorOffset = runInEdtAndGet { editor.caretModel.offset }
        assertEquals(cursorOffset + markedText.length, actualCursorOffset)
    }

    @Test
    fun testPasteMarkWithMultipleCursors() = runBlocking {
        // given a doc
        val text = "<foo>bar</foo><baz>qux</baz>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor

        // and a marked text stored in the mark storage service
        val markNumber = 1
        val markedText = "test"
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        markStorageService.storeMarkedText(markNumber, markedText, 0)

        // Position multiple cursors
        val cursor1Offset = 5 // before "bar"
        val cursor2Offset = 19 // before "qux"
        runInEdtAndWait {
            editor.caretModel.moveToOffset(cursor1Offset)
            editor.caretModel.addCaret(editor.offsetToVisualPosition(cursor2Offset))
        }

        // When we run the paste mark command with a mark number
        val response = CursingPasteMarkCommand.run(listOf(markNumber.toString()), project, editor)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and the marked text should be inserted at both cursor positions
        val expectedText = "<foo>testbar</foo><baz>testqux</baz>"
        assertEquals(expectedText, editor.document.text)
    }

    @Test
    fun testPasteMarkWithNoMarkedText() = runBlocking {
        // given a doc
        val text = "<foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor

        // and no marked text is stored

        // Position the cursor at a specific location
        val cursorOffset = 5 // After "bar"
        runInEdtAndWait {
            editor.caretModel.moveToOffset(cursorOffset)
        }

        // When we run the paste mark command
        val response = CursingPasteMarkCommand.run(listOf("1"), project, editor)

        // then the response should be Bad
        assertEquals(CursingCommandService.BadResponse, response)

        // and the document should remain unchanged
        assertEquals(text, editor.document.text)
    }
}