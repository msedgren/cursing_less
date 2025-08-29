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

class CursingRemoveMarkCommandTest {

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
    fun testMatches() {
        // Test that the command matches the correct string
        assertTrue(CursingRemoveMarkCommand.matches("curse_remove_mark"))
        assertFalse(CursingRemoveMarkCommand.matches("curse_mark"))
    }

    @Test
    fun testRemoveMarkWithSpecificNumber() = runBlocking {
        // given a project
        val project = codeInsightFixture.project

        // and marked text stored in the mark storage service
        val markNumber = 2
        val markedText = "test"
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
        markStorageService.storeMarkedText(markNumber, markedText, 0)

        // Verify the mark exists before removal
        assertNotNull(markStorageService.getMarkedText(markNumber))

        // When we run the remove mark command with the mark number
        val response = CursingRemoveMarkCommand.run(listOf(markNumber.toString()), project, null)

        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)

        // and the marked text should be removed
        assertNull(markStorageService.getMarkedText(markNumber))
    }

    @Test
    fun testRemoveMarkWithNonExistentMark() = runBlocking {
        // given a project
        val project = codeInsightFixture.project

        // and no marked text is stored for mark number 3
        val markNumber = 3
        val markStorageService = ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)

        // Verify the mark doesn't exist before removal
        assertNull(markStorageService.getMarkedText(markNumber))

        // When we run the remove mark command with a non-existent mark number
        val response = CursingRemoveMarkCommand.run(listOf(markNumber.toString()), project, null)

        // then the response should be Bad
        assertEquals(CursingCommandService.BadResponse, response)
    }

    @Test
    fun testRemoveMarkWithInvalidMarkNumber() = runBlocking {
        // given a project
        val project = codeInsightFixture.project

        // When we run the remove mark command with an invalid mark number
        val response = CursingRemoveMarkCommand.run(listOf("invalid"), project, null)

        // then the response should be Bad
        assertEquals(CursingCommandService.BadResponse, response)
    }

    @Test
    fun testRemoveMarkWithNegativeMarkNumber() = runBlocking {
        // given a project
        val project = codeInsightFixture.project

        // When we run the remove mark command with a negative mark number
        val response = CursingRemoveMarkCommand.run(listOf("-1"), project, null)

        // then the response should be Bad
        assertEquals(CursingCommandService.BadResponse, response)
    }
}
