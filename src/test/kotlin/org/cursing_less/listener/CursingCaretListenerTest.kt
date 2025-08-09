package org.cursing_less.listener

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.JBColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.Graphics
import java.awt.Rectangle

class CursingCaretListenerTest {

    lateinit var projectTestFixture: IdeaProjectTestFixture
    lateinit var codeInsightFixture: CodeInsightTestFixture
    lateinit var caretListener: CursingCaretListener

    @BeforeEach
    fun setUp() {
        val (projectTestFixture, codeInsightFixture) = CursingTestUtils.setupTestFixture()
        this.projectTestFixture = projectTestFixture
        this.codeInsightFixture = codeInsightFixture
        caretListener = CursingCaretListener(CoroutineScope(Dispatchers.Default))
    }

    @AfterEach
    fun tearDown() {
        CursingTestUtils.tearDownTestFixture(projectTestFixture, codeInsightFixture)
    }

    @Test
    fun testCaretMovementWithInlaysOnceToTheRight() = runBlocking {
        // Set up test editor with content
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor

        // Initial caret position
        runInEdtAndWait {
            editor.caretModel.moveToOffset(0) // Position at 'b' in "<foo>bar</foo>"
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        // Create inlays using the CursingMarkupService
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // Simulate a right arrow key press by moving the caret right (jiggle the handle by going left twice at zero which goes nowhere...)
        codeInsightFixture.performEditorAction("EditorLeft")
        codeInsightFixture.performEditorAction("EditorLeft")
        codeInsightFixture.performEditorAction("EditorRight")


        // Get the new offset
        val newOffset = runInEdtAndGet {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            editor.caretModel.offset
        }

        // Check if the offset has changed
        assertEquals(1, newOffset, "Caret offset should have increased after right arrow key press")
    }


    @Test
    fun testCaretMovementWithInlays() = runBlocking {
        // Set up test editor with content
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor

        // Initial caret position
        runInEdtAndWait {
            editor.caretModel.moveToOffset(0) // Position at 'b' in "<foo>bar</foo>"
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }

        // Create inlays using the CursingMarkupService
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // Simulate a right arrow key press by moving the caret right (jiggle the handle by going left twice at zero which goes nowhere...)
        codeInsightFixture.performEditorAction("EditorLeft")
        codeInsightFixture.performEditorAction("EditorLeft")
        codeInsightFixture.performEditorAction("EditorRight")
        codeInsightFixture.performEditorAction("EditorRight")
        codeInsightFixture.performEditorAction("EditorRight")
        codeInsightFixture.performEditorAction("EditorRight")
        codeInsightFixture.performEditorAction("EditorRight")

        // Get the new offset
        val newOffset = runInEdtAndGet {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            editor.caretModel.offset
        }

        // Check if the offset has changed
        assertEquals(5, newOffset, "Caret offset should have increased after right arrow key press")
    }

    @Test
    fun testCaretMovementWithComplexInlays() = runBlocking {
        // Set up test editor with content
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor

        // Create inlays using the CursingMarkupService
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        withContext(Dispatchers.EDT) {
            // Initial caret position
            editor.caretModel.moveToOffset(0)
            editor.inlayModel.addInlineElement(1, TestRenderer())
        }
        CursingTestUtils.completeProcessing()

        // Simulate a right arrow key press by moving the caret right
        codeInsightFixture.performEditorAction("EditorRight")
        codeInsightFixture.performEditorAction("EditorRight")
        codeInsightFixture.performEditorAction("EditorRight")
        codeInsightFixture.performEditorAction("EditorRight")
        codeInsightFixture.performEditorAction("EditorRight")
        codeInsightFixture.performEditorAction("EditorRight")

        CursingTestUtils.completeProcessing()

        // Get the new offset
        withContext(Dispatchers.EDT) {
            // Check if the offset has changed
            assertEquals(5, editor.caretModel.offset, "Caret offset should have increased after right arrow key press")
        }
    }

    class TestRenderer() : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int {
            return 5
        }

        override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
            g.color = JBColor.RED
            g.drawChars("test".toCharArray(), 0, 4, targetRegion.x, targetRegion.y + targetRegion.height - 10)
        }

    }
}
