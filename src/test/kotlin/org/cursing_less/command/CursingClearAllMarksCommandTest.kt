package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import kotlinx.coroutines.runBlocking
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkStorageService
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingClearAllMarksCommandTest {

    lateinit var projectTestFixture: IdeaProjectTestFixture
    lateinit var codeInsightFixture: CodeInsightTestFixture

    @BeforeEach
    fun setUp() {
        codeInsightFixture = CursingTestUtils.setupTestFixture()
    }

    @AfterEach
    fun tearDown() {
        CursingTestUtils.tearDownTestFixture(codeInsightFixture)

        // Clear any stored marked text
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        markStorageService.clearAllMarkedText()
    }

    @Test
    fun testMatches() {
        // Test that the command matches the correct string
        assertTrue(CursingClearAllMarksCommand.matches("curse_clear_all_marks"))
        assertFalse(CursingClearAllMarksCommand.matches("curse_mark"))
        assertFalse(CursingClearAllMarksCommand.matches("curse_remove_mark"))
    }

    @Test
    fun testClearAllMarks() = runBlocking {
        // given a project
        val project = codeInsightFixture.project

        // and multiple marked texts stored in the mark storage service
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        markStorageService.storeMarkedText(1, "text1", 0)
        markStorageService.storeMarkedText(2, "text2", 10)
        markStorageService.storeMarkedText(3, "text3", 20)

        // Verify the marks exist before removal
        assertNotNull(markStorageService.getMarkedText(1))
        assertNotNull(markStorageService.getMarkedText(2))
        assertNotNull(markStorageService.getMarkedText(3))

        // When we run the clear all marks command
        val response = CursingClearAllMarksCommand.run(emptyList(), project, null)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and all marked texts should be removed
        assertNull(markStorageService.getMarkedText(1))
        assertNull(markStorageService.getMarkedText(2))
        assertNull(markStorageService.getMarkedText(3))
        
        // and getAllMarkedTextInfo should return an empty map
        assertTrue(markStorageService.getAllMarkedTextInfo().isEmpty())
    }

    @Test
    fun testClearAllMarksWithNoMarks() = runBlocking {
        // given a project
        val project = codeInsightFixture.project

        // and no marked texts stored
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        markStorageService.clearAllMarkedText() // Ensure no marks exist

        // Verify no marks exist before running the command
        assertTrue(markStorageService.getAllMarkedTextInfo().isEmpty())

        // When we run the clear all marks command
        val response = CursingClearAllMarksCommand.run(emptyList(), project, null)

        // then the response should still be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and getAllMarkedTextInfo should still return an empty map
        assertTrue(markStorageService.getAllMarkedTextInfo().isEmpty())
    }
}