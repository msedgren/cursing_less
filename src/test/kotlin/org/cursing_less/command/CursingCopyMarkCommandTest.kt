package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkStorageService
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class CursingCopyMarkCommandTest {

    lateinit var projectTestFixture: IdeaProjectTestFixture
    lateinit var codeInsightFixture: CodeInsightTestFixture

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
    fun testCopyMarkWithExplicitNumber() = runBlocking {
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")

        // Store two different marks
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        markStorageService.storeMarkedText(1, "one", 0)
        markStorageService.storeMarkedText(2, "two", 0)

        // Prime clipboard with known value
        CopyPasteManager.getInstance().setContents(StringSelection("initial"))

        val response = CursingCopyMarkCommand.run(listOf("2"), project, codeInsightFixture.editor)
        assertEquals(CursingCommandService.OkayResponse, response)

        val copiedText: Any? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
        assertEquals("two", copiedText)
    }

    @Test
    fun testCopyMarkDefaultsToOne() = runBlocking {
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")

        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        markStorageService.storeMarkedText(1, "default", 0)

        // Prime clipboard with known value
        CopyPasteManager.getInstance().setContents(StringSelection("initial"))

        val response = CursingCopyMarkCommand.run(emptyList(), project, codeInsightFixture.editor)
        assertEquals(CursingCommandService.OkayResponse, response)

        val copiedText: Any? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
        assertEquals("default", copiedText)
    }

    @Test
    fun testCopyMarkMissingReturnsBad() = runBlocking {
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")

        // Ensure clipboard has some initial value
        CopyPasteManager.getInstance().setContents(StringSelection("initial"))

        val response = CursingCopyMarkCommand.run(listOf("3"), project, codeInsightFixture.editor)
        assertEquals(CursingCommandService.BadResponse, response)

        // Clipboard should remain unchanged
        val copiedText: Any? = CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor)
        assertEquals("initial", copiedText)
    }
}
