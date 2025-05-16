package org.cursing_less.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.listener.CursingApplicationListener
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingMarkupService.Companion.INLAY_KEY

/**
 * Utility class containing common functions used across test classes.
 */
object CursingTestUtils {

    /**
     * Creates and sets up a CodeInsightTestFixture for testing.
     *
     * @return The configured CodeInsightTestFixture
     */
    fun setupTestFixture(): CodeInsightTestFixture {
        CursingApplicationListener.skipServer = true

        val projectTestFixture =
            IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(LightProjectDescriptor(), "foo")
                .fixture

        val codeInsightFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectTestFixture)
        codeInsightFixture.setUp()
        return codeInsightFixture
    }

    /**
     * Tears down the provided CodeInsightTestFixture.
     *
     * @param codeInsightFixture The fixture to tear down
     */
    fun tearDownTestFixture(codeInsightFixture: CodeInsightTestFixture) {
        try {
            runBlocking {
                ApplicationManager.getApplication().getService(CursingMarkupService::class.java).processExistingWork()
            }
            runInEdtAndWait(true) {
                codeInsightFixture.tearDown()
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
    fun getCursingColorShape(editor: Editor, offset: Int): CursingColorShape? {
        var data: CursingColorShape? = null
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            data = editor.inlayModel.getInlineElementsInRange(offset, offset)
                .firstNotNullOf { it.getUserData(INLAY_KEY) }
        }
        return data
    }
}