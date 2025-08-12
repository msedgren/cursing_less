package org.cursing_less.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.service.CursingMarkStorageService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.toolwindow.CursingMarksToolWindow

/**
 * Utility class containing common functions used across test classes.
 */
object CursingTestUtils {

    /**
     * Creates and sets up a CodeInsightTestFixture for testing.
     *
     * @return The configured CodeInsightTestFixture
     */
    fun setupTestFixture(): Pair<IdeaProjectTestFixture, CodeInsightTestFixture> {
        val projectTestFixture =
            IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(LightProjectDescriptor(), "foo")
                .fixture

        val codeInsightFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectTestFixture)
        codeInsightFixture.setUp()

        val markupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        markupService.resetProcessingCount()
        runBlocking {
            if (!markupService.isEnabled()) {
                markupService.toggleEnabled()
            }
        }

        return Pair(projectTestFixture, codeInsightFixture)
    }

    /**
     * Tears down the provided CodeInsightTestFixture.
     *
     * @param projectTestFixture
     * @param codeInsightFixture The fixture to tear down
     */
    fun tearDownTestFixture(projectTestFixture: IdeaProjectTestFixture, codeInsightFixture: CodeInsightTestFixture) {
        // Clear any stored marked text
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        markStorageService.clearAllMarkedText()

        try {
            runInEdtAndWait(true) {
                codeInsightFixture.tearDown()
            }
            runBlocking {
                completeProcessing()
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to tear down test fixture", e)
        }
    }

    /**
     * Gets the CursingColorShape at the specified offset in the editor.
     *
     * @param editor The editor to get the color shape from
     * @param offset The offset in the editor
     * @return The CursingColorShape at the specified offset, or null if not found
     */
    suspend fun getCursingColorShape(editor: Editor, offset: Int): CursingColorShape? {
        val markupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)

        completeProcessing()
        return markupService.pullExistingGraphics(editor)
            .values
            .asSequence()
            .filter { it.offset == offset }
            .map { it.cursingColorShape }
            .firstOrNull()
    }

    suspend fun completeProcessing() {
        val startTime = System.currentTimeMillis()
        val waitAmount = 1000L
        val markupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        withContext(Dispatchers.EDT) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
        while((CursingMarksToolWindow.workToProcess() || markupService.workToProcess()) &&
            (System.currentTimeMillis() - startTime) < waitAmount) {
            delay(10L)
        }

        if (markupService.workToProcess()) {
            markupService.resetProcessingCount()
        }

    }

}