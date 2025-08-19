package org.cursing_less.service

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.listener.CursingApplicationListener
import org.cursing_less.util.CursingTestUtils
import org.cursing_less.util.CursingTestUtils.completeProcessing
import org.cursing_less.util.CursingTestUtils.tearDownTestFixture
import org.cursing_less.util.CursingTestUtils.pullConsumedIndexes
import org.cursing_less.util.OffsetDistanceComparator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingMarkupServiceTest {

    lateinit var projectTestFixture: IdeaProjectTestFixture
    lateinit var codeInsightFixture: CodeInsightTestFixture

    val largeDoc = """
        <foo>
        <!-- A a a a a a a a a a a a a a a a a a a a a a a a a a a A a a a a a a a a a a a a a a a a a a a a a a a a a A  -->
        <!-- b b b b b b b b b b b b b b -->
        <!-- c c c c c c c c c c c c c c -->
        <!-- d d d d d d d d d d d d d d -->
        <!-- e e e e e e e e e e e e e e -->
        <!-- A a a a a a a a a a a a a a a a a a a a a a a a a a A a a a a a a a a a a a a a a a a a a a a a a a a a a A  -->
        <!-- b b b b b b b b b b b b b b  -->
        <!-- c c c c c c c c c c c c c  -->
        <!-- d d d d d d d d d d d d d d  -->
        </foo>
    """.trimIndent()

    @BeforeEach
    fun setUp() {
        val (projectTestFixture, codeInsightFixture) = CursingTestUtils.setupTestFixture()
        this.projectTestFixture = projectTestFixture
        this.codeInsightFixture = codeInsightFixture
    }

    @AfterEach
    fun tearDown() {
        tearDownTestFixture(projectTestFixture, codeInsightFixture)
    }

    @Test
    fun testBasicMarkupOfTokens() = runBlocking {
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")

        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        assertTrue(CursingApplicationListener.handler.initialized.get())

        cursingMarkupService.updateCursingTokensNow(codeInsightFixture.editor, 0)

        val graphics = withContext(Dispatchers.EDT) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            cursingMarkupService.pullExistingGraphics(codeInsightFixture.editor)
        }

        assertEquals(8, graphics.size)
    }

    @Test
    fun testOffsetDistanceComparator() {
        // Test with integers directly
        val items = listOf(5, 10, 3, 8, 15)
        val offset = 7

        // Identity function for extracting the offset from integers
        val comparator = OffsetDistanceComparator<Int>(offset) { it }

        // Sort using the comparator
        val sorted = items.sortedWith(comparator)

        // Expected order based on distance from offset 7: 8, 5, 10, 3, 15
        // Distance:                                        1, 2,  3, 4,  8
        assertEquals(listOf(8, 5, 10, 3, 15), sorted)
    }

    @Test
    fun testOffsetDistanceComparatorWithCustomObjects() {
        // Create mock tokens at different offsets
        val tokens = listOf(
            CursingTokenService.CursingToken(100, 102, "a"),
            CursingTokenService.CursingToken(50, 52, "b"),
            CursingTokenService.CursingToken(80, 82, "c"),
            CursingTokenService.CursingToken(30, 32, "d"),
            CursingTokenService.CursingToken(120, 122, "e")
        )

        // Test with cursor at offset 75
        val comparator75 = OffsetDistanceComparator<CursingTokenService.CursingToken>(75) { it.startOffset }
        val sorted75 = tokens.sortedWith(comparator75)

        // Expected order based on distance from 75:
        // 80 (distance 5), 100 (distance 25), 50 (distance 25), 30 (distance 45), 120 (distance 45) -- stable sort assumed
        assertEquals(80, sorted75[0].startOffset)
        assertEquals(100, sorted75[1].startOffset)
        assertEquals(50, sorted75[2].startOffset)
        assertEquals(30, sorted75[3].startOffset)
        assertEquals(120, sorted75[4].startOffset)
    }

    @Test
    fun testToggleEnabled() = runBlocking {
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)

        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")

        // Verify tokens are added
        completeProcessing()
        var graphics = cursingMarkupService.pullExistingGraphics(codeInsightFixture.editor)
        // There should be tokens
        assertTrue(graphics.isNotEmpty(), "Tokens should be added when cursing is enabled")
        // Toggle cursing off
        cursingMarkupService.toggleEnabled()
        // Verify all tokens are removed
        completeProcessing()
        graphics = cursingMarkupService.pullExistingGraphics(codeInsightFixture.editor)
        // There should be no tokens
        assertTrue(graphics.isEmpty(), "All tokens should be removed when cursing is disabled")
        // Toggle cursing back on
        cursingMarkupService.toggleEnabled()
        // Verify tokens are added again
        completeProcessing()
        graphics = cursingMarkupService.pullExistingGraphics(codeInsightFixture.editor)
        // There should be tokens again
        assertTrue(graphics.isNotEmpty(), "Tokens should be added when cursing is re-enabled")
    }

    

    @Test
    fun testGraphicsByChar() = runBlocking {
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)

        withContext(Dispatchers.EDT) {
            codeInsightFixture.configureByText(XmlFileType.INSTANCE, largeDoc)
            EditorTestUtil.setEditorVisibleSize(codeInsightFixture.editor, 500, 500)
        }
        completeProcessing()

        // Test at beginning of document
        cursingMarkupService.updateCursingTokensNow(codeInsightFixture.editor, 11)
        val existingGraphics = cursingMarkupService.pullExistingGraphics(codeInsightFixture.editor)

        var graphics = cursingMarkupService.graphicsByChar(existingGraphics,  11)
        assertEquals(generateSequence(11) { it + 2 }.take(30).toList(), graphics['a']?.map { it.offset })
    }

    @Test
    fun testItRendersTokensAsWeMove() = runBlocking {
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        withContext(Dispatchers.EDT) {
            // given a large document that is visible
            codeInsightFixture.configureByText(XmlFileType.INSTANCE, largeDoc)
            EditorTestUtil.setEditorVisibleSize(codeInsightFixture.editor, 500, 500)
            // and we are at offset 11 (just to match below...)
            codeInsightFixture.editor.caretModel.moveToOffset(11)
        }
        completeProcessing()
        // when we update for this position
        cursingMarkupService.updateCursingTokensNow(codeInsightFixture.editor, 11)
        // expect the first 30 'a' tokens were added.
        assertEquals(generateSequence(11) { it + 2 }.take(30).toSortedSet(),
            pullConsumedIndexes(codeInsightFixture.editor, 'a'))
        // and when we move  halfway down the list  update for this position
        cursingMarkupService.updateCursingTokensNow(codeInsightFixture.editor, 64)
        // expect the first 30 'a' tokens were added.
        assertEquals(generateSequence(35) { it + 2 }.take(30).toSortedSet(),
            pullConsumedIndexes(codeInsightFixture.editor, 'a'))

        // and when we move to offset 424
        cursingMarkupService.updateCursingTokensNow(codeInsightFixture.editor, 424)
        // expect the last 30 'a' tokens were added.
        assertEquals(generateSequence(325) { it + 2 }.take(30).toSortedSet(),
            pullConsumedIndexes(codeInsightFixture.editor, 'a'))
        // and when we are in the middle
        cursingMarkupService.updateCursingTokensNow(codeInsightFixture.editor, 197)
        // expect the last of the first 15 and first of the last 15 'a' tokens were added.
        assertEquals(
            generateSequence(89) { it + 2 }.take(15).plus(generateSequence(277) { it + 2 }.take(15)).toSortedSet(),
            pullConsumedIndexes(codeInsightFixture.editor, 'a')
        )
    }
}
