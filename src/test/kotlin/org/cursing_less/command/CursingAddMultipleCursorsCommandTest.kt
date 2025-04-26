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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CursingAddMultipleCursorsCommandTest {

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
        assertTrue(CursingAddMultipleCursorsCommand.matches("curse_add_multiple_cursors"))
        assertFalse(CursingAddMultipleCursorsCommand.matches("curse_add_cursor"))
    }

    @Test
    fun testAddMultipleCursorsBelow() = runBlocking {
        // given a multi-line doc
        val text = """
            line1
            line2
            line3
            line4
            line5
        """.trimIndent()

        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor

        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // Position the primary caret at the beginning of line1
        runInEdtAndWait {
            editor.caretModel.primaryCaret.moveToOffset(0)
        }

        // When we run the add multiple cursors command to add 2 cursors below
        val response = CursingAddMultipleCursorsCommand.run(listOf("2", "below"), project, editor)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and there should be 3 carets in total (primary + 2 new ones)
        var caretCount = 0
        var caretOffsets = listOf<Int>()
        runInEdtAndWait { 
            caretCount = editor.caretModel.caretCount
            caretOffsets = editor.caretModel.allCarets.map { it.offset }.sorted()
        }

        assertEquals(3, caretCount)

        // and the carets should be at the beginning of lines 1, 2, and 3
        val expectedOffsets = listOf(0, 6, 12) // Beginning of lines 1, 2, and 3
        assertEquals(expectedOffsets, caretOffsets)
    }

    @Test
    fun testAddMultipleCursorsAbove() = runBlocking {
        // given a multi-line doc
        val text = """
            line1
            line2
            line3
            line4
            line5
        """.trimIndent()

        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor

        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        // Position the primary caret at the beginning of line3
        runInEdtAndWait {
            editor.caretModel.primaryCaret.moveToOffset(12) // Beginning of line3
        }

        // When we run the add multiple cursors command to add 2 cursors above
        val response = CursingAddMultipleCursorsCommand.run(listOf("2", "above"), project, editor)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and there should be 3 carets in total (primary + 2 new ones)
        var caretCount = 0
        var caretOffsets = listOf<Int>()
        runInEdtAndWait { 
            caretCount = editor.caretModel.caretCount
            caretOffsets = editor.caretModel.allCarets.map { it.offset }.sorted()
        }

        assertEquals(3, caretCount)

        // and the carets should be at the beginning of lines 1, 2, and 3
        val expectedOffsets = listOf(0, 6, 12) // Beginning of lines 1, 2, and 3
        assertEquals(expectedOffsets, caretOffsets)
    }
}