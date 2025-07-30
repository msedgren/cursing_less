package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import kotlinx.coroutines.runBlocking
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CursingOffsetCommandTest {

    lateinit var projectTestFixture: IdeaProjectTestFixture
    lateinit var codeInsightFixture: CodeInsightTestFixture

    @BeforeEach
    fun setUp() {
        codeInsightFixture = CursingTestUtils.setupTestFixture()
    }

    @AfterEach
    fun tearDown() {
        CursingTestUtils.tearDownTestFixture(codeInsightFixture)
    }

    @Test
    fun testMatches() {
        // Test that the command matches the correct string
        assertTrue(CursingOffsetCommand.matches("curse_offset"))
        assertFalse(CursingOffsetCommand.matches("curse_move"))
    }

    @Test
    fun testOffset() = runBlocking {
        // given a test editor
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)

        //and we can get the shape and color at offset 5 (tied to bar)
        val colorAndShape = CursingTestUtils.getCursingColorShape(editor, 5)
        assertNotNull(colorAndShape)

        // when we run the offset command with the numbers and character
        val response = CursingOffsetCommand.run(listOf("${colorAndShape?.color?.name}", "${colorAndShape?.shape?.name}", "b"), project, editor)

        // it works
        assertTrue(response.responseCode == 200)
        // and we get the offset information
        assertEquals("(5,8)", response.response)
    }
}