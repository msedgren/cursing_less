package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.util.CursingTestUtils
import org.cursing_less.util.CursingTestUtils.completeProcessing
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ToggleMarkupCommandTest {

    lateinit var codeInsightFixture: CodeInsightTestFixture
    lateinit var projectTestFixture: IdeaProjectTestFixture

    @BeforeEach
    fun setUp() {
        val (projectTestFixture, codeInsightFixture) = CursingTestUtils.setupTestFixture()
        this.projectTestFixture = projectTestFixture
        this.codeInsightFixture = codeInsightFixture
    }

    @AfterEach
    fun tearDown() {
        CursingTestUtils.tearDownTestFixture(projectTestFixture, codeInsightFixture)
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

        var graphics = cursingMarkupService.pullExistingGraphics(editor)
        assertTrue(graphics.isNotEmpty(), "Graphics should be present initially")
        val initialInlayCount = graphics.size

        // Run the toggle command to disable markup
        toggleMarkupAndWait(cursingMarkupService, project, editor)

        // Verify that markup is removed
        graphics = cursingMarkupService.pullExistingGraphics(editor)
        assertTrue(graphics.isEmpty(), "Graphics should be removed after toggling markup off")

        // Run the toggle command again to enable markup
        toggleMarkupAndWait(cursingMarkupService, project, editor)

        // Verify that markup is present again
        graphics = cursingMarkupService.pullExistingGraphics(editor)

        // There should be graphics present again
        assertTrue(graphics.isNotEmpty(), "Graphics should be present after toggling markup on again")
        assertEquals(initialInlayCount, graphics.size, "The number of graphics should be the same as initially")
    }

    private suspend fun toggleMarkupAndWait(
        cursingMarkupService: CursingMarkupService,
        project: Project,
        editor: Editor
    ) {
        ToggleMarkupCommand.run(listOf(""), project, editor)
        completeProcessing()
    }
}
