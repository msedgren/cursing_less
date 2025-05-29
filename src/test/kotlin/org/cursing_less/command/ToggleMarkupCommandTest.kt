package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingMarkupService.Companion.INLAY_KEY
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ToggleMarkupCommandTest {

    lateinit var codeInsightFixture: CodeInsightTestFixture

    @BeforeEach
    fun setUp() {
        codeInsightFixture = CursingTestUtils.setupTestFixture()
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
    fun testToggleMarkupRemovesAndAddsInlays() = runBlocking {
        // Setup test environment with a document
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor

        // Get the markup service
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        // Verify that markup is present initially
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        var inlays = getInlays(editor)
        assertTrue(inlays.isNotEmpty(), "Inlays should be present initially")
        val initialInlayCount = inlays.size

        // Run the toggle command to disable markup
        toggleMarkupAndWait(cursingMarkupService, project, editor)

        // Verify that markup is removed
        inlays = getInlays(editor)
        assertTrue(inlays.isEmpty(), "Inlays should be removed after toggling markup off")

        // Run the toggle command again to enable markup
        toggleMarkupAndWait(cursingMarkupService, project, editor)

        // Verify that markup is present again
        inlays = getInlays(editor)

        // There should be inlays present again
        assertTrue(inlays.isNotEmpty(), "Inlays should be present after toggling markup on again")
        assertEquals(initialInlayCount, inlays.size, "The number of inlays should be the same as initially")
    }

    private suspend fun toggleMarkupAndWait(
        cursingMarkupService: CursingMarkupService,
        project: Project,
        editor: Editor
    ) {
        cursingMarkupService.clearExistingWork()
        ToggleMarkupCommand.run(listOf(""), project, editor)
        cursingMarkupService.processExistingWork()
    }

    private suspend fun getInlays(editor: Editor): List<Inlay<*>> {
        var inlays: List<Inlay<*>> = listOf()
        withContext(Dispatchers.EDT) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            inlays = editor.inlayModel.getInlineElementsInRange(0, 1000)
                .filter { it.getUserData(INLAY_KEY) != null }
                .toList()
        }
        return inlays
    }
}
