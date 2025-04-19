package org.cursing_less.command

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.*
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.listener.CursingApplicationListener
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingMarkupService.Companion.INLAY_KEY
import org.cursing_less.service.CursingPreferenceService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CursingSelectCommandTest {

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
        assertTrue(CursingSelectCommand.matches("curse_select"))
        assertFalse(CursingSelectCommand.matches("curse_copy"))
    }


    @ParameterizedTest
    @MethodSource("selectParams")
    fun testSelect(offset: Int, character: Char, expected: String) = runBlocking {
        // given a doc
        val text = "<!--test this works--><foo>bar</foo>"
        // and a project and editor that contains the doc
        val project = codeInsightFixture.project
        codeInsightFixture.configureByText(XmlFileType.INSTANCE, text)
        val editor = codeInsightFixture.editor
        // and markup is present
        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        cursingMarkupService.updateCursingTokensNow(editor, 0)
        //and we can get the shape and color at offset 4 test
        val colorAndShape = getCursingColorShape(editor, offset)
        assertNotNull(colorAndShape)
        // map to numbers
        val cursingPreferenceService = ApplicationManager.getApplication().service<CursingPreferenceService>()
        val colorNumber = cursingPreferenceService.mapToCode(colorAndShape!!.color)
        val shapeNumber = cursingPreferenceService.mapToCode(colorAndShape.shape)
        // When we run the select command with the numbers and character
        val response = CursingSelectCommand.run(listOf("$colorNumber", "$shapeNumber", "$character"), project, editor)
        // then the response should be Okay
        assertEquals(CursingCommandService.OkayResponse, response)
        // and the correct text should be selected
        var selectedText = ""
        runInEdtAndWait { selectedText = editor.selectionModel.selectedText ?: "" }
        assertEquals(expected, selectedText)
    }

    companion object {
        @JvmStatic
        fun selectParams() = listOf(
            Arguments.of(0, '<', "<"),
            Arguments.of(1, '!', "!--"),
            Arguments.of(4, 't', "test"),
            Arguments.of(9, 't', "this"),
            Arguments.of(14, 'w', "works"),
            Arguments.of(19, '-', "--"),
            Arguments.of(21, '>', "><"),
            Arguments.of(23, 'f', "foo"),
            Arguments.of(26, '>', ">"),
            Arguments.of(27, 'b', "bar"),
            Arguments.of(30, '<', "<"),
            Arguments.of(31, '/', "/"),
            Arguments.of(32, 'f', "foo"),
            Arguments.of(35, '>', ">"),

        )
    }



    private fun getCursingColorShape(editor: Editor, offset: Int): CursingColorShape? {
        var data: CursingColorShape? = null
        runInEdtAndWait {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

            data = editor.inlayModel.getInlineElementsInRange(offset, offset)
                .firstNotNullOf { it.getUserData(INLAY_KEY) }
        }
        return data
    }
}
