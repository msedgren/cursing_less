package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.*
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.cursing_less.listener.CursingApplicationListener
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingMarkupService.Companion.INLAY_KEY
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ToggleMarkupCommandTest {


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
    fun testMatches() {
        // Test that the command matches the correct string
        assertTrue(ToggleMarkupCommand.matches("toggle_markup"))
        assertFalse(ToggleMarkupCommand.matches("curse_copy"))
    }

    @Test
    fun testRun() {
        // Setup test environment
        val project = codeInsightFixture.project

        runBlocking {
            // Run the command
            val response = ToggleMarkupCommand.run(emptyList(), project, null)

            // Verify the response
            assertEquals(CursingCommandService.OkayResponse, response)
        }
    }

    @Test
    fun testToggleMarkupRemovesAndAddsInlays() {
        // Setup test environment with a document
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor

        // Get the markup service
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        // Verify that markup is present initially
        runBlocking {
            cursingMarkupService.updateCursingTokensNow(editor, 0)
        }
        var inlays = getInlays(editor)

        assertTrue(inlays.isNotEmpty(), "Inlays should be present initially")
        val initialInlayCount = inlays.size

        // Run the toggle command to disable markup
        runBlocking { toggleMarkupAndWait(cursingMarkupService) }

        // Verify that markup is removed
        inlays = getInlays(editor)
        assertTrue(inlays.isEmpty(), "Inlays should be removed after toggling markup off")

        // Run the toggle command again to enable markup
        runBlocking { toggleMarkupAndWait(cursingMarkupService) }

        // Verify that markup is present again
        inlays = getInlays(editor)

        // There should be inlays present again
        assertTrue(inlays.isNotEmpty(), "Inlays should be present after toggling markup on again")
        assertEquals(initialInlayCount, inlays.size, "The number of inlays should be the same as initially")
    }

    private suspend fun toggleMarkupAndWait(cursingMarkupService: CursingMarkupService) {
        // Call the service directly to ensure it works
        cursingMarkupService.toggleEnabled()

        // Wait for the toggle to take effect
        withContext(Dispatchers.EDT) {
            cursingMarkupService.flush()
        }
        cursingMarkupService.coroutineScope.coroutineContext.job.children.forEach { it.join() }
    }

    private fun getInlays(editor: Editor): List<Inlay<*>> {
        var inlays: List<Inlay<*>> = listOf()
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            inlays = editor.inlayModel.getInlineElementsInRange(0, 1000)
                .filter { it.getUserData(INLAY_KEY) != null }
                .toList()
        }
        return inlays
    }
}
