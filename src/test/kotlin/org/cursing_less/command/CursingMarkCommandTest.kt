package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.*
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkStorageService
import org.cursing_less.service.MarkedTextInfo
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CursingMarkCommandTest {

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
        assertTrue(CursingMarkCommand.matches("curse_mark"))
        assertFalse(CursingMarkCommand.matches("curse_select"))
    }

    @Test
    fun testMarkWithPrimaryCursor() = runBlocking {
        // given a doc
        val text = "<!--test this works--><foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor

        // Set up a selection
        val startOffset = 4  // 't' in "test"
        val endOffset = 8    // ' ' after "test"
        val expectedText = "test"
        val markNumber = 1

        runInEdtAndWait { 
            // Create a selection
            editor.selectionModel.setSelection(startOffset, endOffset)
        }

        // When we run the mark command with a mark number
        val response = CursingMarkCommand.run(listOf(markNumber.toString()), project, editor)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and the text and its starting offset should be stored in the mark storage service with the mark number
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        val markedTextInfo = markStorageService.getMarkedTextInfo(markNumber)

        // Verify that the text and offset were stored correctly
        assertNotNull(markedTextInfo)
        assertEquals(expectedText, markedTextInfo?.text)
        assertEquals(startOffset, markedTextInfo?.startOffset)
    }

    @Test
    fun testMarkWithSecondaryCursor() = runBlocking {
        // given a doc
        val text = "<!--test this works--><foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor

        val startOffset = 9  // 't' in "this"
        val endOffset = 13   // ' ' after "this"
        val expectedText = "this"
        val markNumber = 2

        runInEdtAndWait {
            // add a selection for the primary caret
            editor.selectionModel.setSelection(15, 19)
            // and a secondary caret is added
            val secondaryCaret = editor.caretModel.addCaret(editor.offsetToVisualPosition(startOffset))
            assertNotNull(secondaryCaret)
            // and the secondary caret has the selection that we want
            secondaryCaret!!.setSelection(startOffset, endOffset)
        }

        // When we run the mark command with a mark number
        val response = CursingMarkCommand.run(listOf(markNumber.toString()), project, editor)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)
        // and the text and its starting offset should be stored in the mark storage service with the mark number
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        val markedTextInfo = markStorageService.getMarkedTextInfo(markNumber)
        assertNotNull(markedTextInfo)
        assertEquals(expectedText, markedTextInfo?.text)
        assertEquals(startOffset, markedTextInfo?.startOffset)
    }

    @Test
    fun testMarkWithDefaultNumber() = runBlocking {
        // given a doc
        val text = "<!--test this works--><foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor
        val startOffset = 4  // 't' in "test"
        val endOffset = 8    // ' ' after "test"
        val expectedText = "test"
        val defaultMarkNumber = 1

        runInEdtAndWait { 
            // and a selection
            editor.selectionModel.setSelection(startOffset, endOffset)
        }

        // When we run the mark command without a mark number
        val response = CursingMarkCommand.run(emptyList(), project, editor)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)
        // and the text and its starting offset should be stored with the default mark number
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        val markedTextInfo = markStorageService.getMarkedTextInfo(defaultMarkNumber)
        assertNotNull(markedTextInfo)
        assertEquals(expectedText, markedTextInfo?.text)
        assertEquals(startOffset, markedTextInfo?.startOffset)
    }

    @Test
    fun testMarkWithNoSelection() = runBlocking {
        // given a doc
        val text = "<!--test this works--><foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor

        // and no selection is set

        // When we run the mark command
        val response = CursingMarkCommand.run(emptyList(), project, editor)

        // then the response should be Bad
        assertEquals(CursingCommandService.BadResponse, response)
    }
}
