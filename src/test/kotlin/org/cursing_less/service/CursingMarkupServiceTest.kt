package org.cursing_less.service

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Inlay
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.*
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.listener.CursingApplicationListener
import org.cursing_less.service.CursingMarkupService.Companion.INLAY_KEY
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CursingMarkupServiceTest {

    lateinit var projectTestFixture: IdeaProjectTestFixture
    lateinit var codeInsightFixture: CodeInsightTestFixture

    @BeforeEach
    fun setUp() {
        CursingApplicationListener.skipServer = true

        projectTestFixture =
            IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(LightProjectDescriptor(), "foo")
                .fixture

        codeInsightFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectTestFixture)
        codeInsightFixture.setUp()
    }

    @AfterEach
    fun tearDown() {
        runInEdtAndWait(true) {
            codeInsightFixture.tearDown()
        }
    }

    @Test
    fun testBasicMarkupOfTokens() {
        System.err.println("testFoo Started")
        val project = codeInsightFixture.project
        var inlays: List<Inlay<*>> = listOf()
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")

        val cursingMarkupService = project.service<CursingMarkupService>()
        assertTrue(CursingApplicationListener.handler.initialized.get())

        runBlocking {
            cursingMarkupService.updateCursingTokensNow(codeInsightFixture.editor, 0)
        }

        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            inlays = codeInsightFixture.editor.inlayModel.getInlineElementsInRange(0, 1000)
                .filter { it.getUserData(INLAY_KEY) != null }
                .toList()
        }

        assertEquals(8, inlays.size)
    }

    @Test
    fun testOffsetDistanceComparator() {
        // Test with integers directly
        val items = listOf(5, 10, 3, 8, 15)
        val offset = 7
        
        // Identity function for extracting the offset from integers
        val comparator = CursingMarkupService.OffsetDistanceComparator<Int>(offset) { it }
        
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
            CursingMarkupService.CursingToken(100, 102, "a"),
            CursingMarkupService.CursingToken(50, 52, "b"),
            CursingMarkupService.CursingToken(80, 82, "c"),
            CursingMarkupService.CursingToken(30, 32, "d"),
            CursingMarkupService.CursingToken(120, 122, "e")
        )
        
        // Test with cursor at offset 75
        val comparator75 = CursingMarkupService.OffsetDistanceComparator<CursingMarkupService.CursingToken>(75) { it.startOffset }
        val sorted75 = tokens.sortedWith(comparator75)
        
        // Expected order based on distance from 75:
        // 80 (distance 5), 100 (distance 25), 50 (distance 25), 30 (distance 45), 120 (distance 45) -- stable sort assumed
        assertEquals(80, sorted75[0].startOffset)
        assertEquals(100, sorted75[1].startOffset)
        assertEquals(50, sorted75[2].startOffset)
        assertEquals(30, sorted75[3].startOffset)
        assertEquals(120, sorted75[4].startOffset)
    }
}
