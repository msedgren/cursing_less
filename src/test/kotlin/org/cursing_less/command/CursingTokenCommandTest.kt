package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.PlatformTestUtil
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
import java.awt.datatransfer.DataFlavor

class CursingTokenCommandTest {

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
        assertTrue(CursingTokenCommand.matches("curse_token"))
        assertFalse(CursingTokenCommand.matches("curse_select"))
    }

    @Test
    fun testSelectToken() = runBlocking {
        // given a doc
        val text = "<foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
        
        // Position cursor within the "bar" token
        runInEdtAndWait {
            editor.caretModel.moveToOffset(6) // Middle of "bar"
        }
        
        // When we run the token command with select mode
        val response = CursingTokenCommand.run(listOf("select"), project, editor)
        
        // Then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)
        
        // And the correct text should be selected
        var selectedText = ""
        runInEdtAndWait { selectedText = editor.selectionModel.selectedText ?: "" }
        assertEquals("bar", selectedText)
    }

    @Test
    fun testCopyToken() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
        
        // Position cursor within the "bar" token
        runInEdtAndWait {
            editor.caretModel.moveToOffset(6) // Middle of "bar"
        }
        
        // When we run the token command with copy mode
        val response = CursingTokenCommand.run(listOf("copy"), project, editor)
        
        // Then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)
        
        // And the correct text should be in the clipboard
        val copiedText: Object? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
        assertEquals("bar", copiedText)
        
        // And the document should remain unchanged
        assertEquals("<foo>bar</foo>", editor.document.text)
    }

    @Test
    fun testCutToken() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
        
        // Position cursor within the "bar" token
        runInEdtAndWait {
            editor.caretModel.moveToOffset(6) // Middle of "bar"
        }
        
        // When we run the token command with cut mode
        val response = CursingTokenCommand.run(listOf("cut"), project, editor)
        
        // Then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)
        
        // And the correct text should be in the clipboard
        val copiedText: Object? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
        assertEquals("bar", copiedText)
        
        // And the text should be removed from the document
        assertEquals("<foo></foo>", editor.document.text)
    }

    @Test
    fun testClearToken() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
        
        // Position cursor within the "bar" token
        runInEdtAndWait {
            editor.caretModel.moveToOffset(6) // Middle of "bar"
        }
        
        // When we run the token command with clear mode
        val response = CursingTokenCommand.run(listOf("clear"), project, editor)
        
        // Then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)
        
        // And the text should be removed from the document
        assertEquals("<foo></foo>", editor.document.text)
    }

    @Test
    fun testNoTokenAtCursor() = runBlocking {
        // given a test editor with text that won't be tokenized
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>   </foo>")
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
        
        // Position cursor in whitespace where there's no token
        runInEdtAndWait {
            editor.caretModel.moveToOffset(6) // Middle of whitespace
        }
        
        // When we run the token command with any mode
        val response = CursingTokenCommand.run(listOf("select"), project, editor)
        
        // Then the response should be Bad
        assertEquals(CursingCommandService.BadResponse, response)
    }
}