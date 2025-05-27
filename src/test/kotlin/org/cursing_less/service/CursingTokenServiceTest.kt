package org.cursing_less.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingShape
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import com.intellij.ui.JBColor
import com.intellij.util.application
import org.cursing_less.listener.CursingApplicationListener
import org.junit.jupiter.api.AfterEach

class CursingTokenServiceTest {

    private lateinit var projectTestFixture: IdeaProjectTestFixture
    private lateinit var codeInsightFixture: CodeInsightTestFixture
    private lateinit var tokenService: CursingTokenService
    private lateinit var colorAndShapeManager: ColorAndShapeManager


    @BeforeEach
    fun setUp() {
        CursingApplicationListener.skipServer = true

        projectTestFixture =
            IdeaTestFixtureFactory.getFixtureFactory().createLightFixtureBuilder(LightProjectDescriptor(), "bar")
                .fixture

        codeInsightFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectTestFixture)
        codeInsightFixture.setUp()

        tokenService = application.service<CursingTokenService>()

        // Create colors and shapes for testing
        val colors = listOf(
            CursingColor("red", JBColor.RED),
            CursingColor("blue", JBColor.BLUE)
        )
        val shapes = listOf(
            CursingShape.Circle,
            CursingShape.Square
        )

        colorAndShapeManager = ColorAndShapeManager(colors, shapes)
    }

    @AfterEach
    fun tearDown() {
        runInEdtAndWait(true) {
            codeInsightFixture.tearDown()
        }
    }

    @Test
    fun testFindAllCursingTokensWithin() {
        // Test with a simple string containing various token types
        val text = "function test() { return x + y; }"
        val startOffset = 10

        val tokens = tokenService
            .findAllCursingTokensWithin(text, startOffset)
            .sortedBy { it.startOffset }

        // Check specific tokens
        assertEquals(
            listOf("function", "test", "(", ")", "{", "return", "x", "+", "y", ";", "}"),
            tokens.map { it.text })

        // Verify offsets are calculated correctly
        for (token in tokens) {
            assertEquals(token.endOffset - token.startOffset, token.text.length)
            assertTrue(token.startOffset >= startOffset)
        }
    }

    @ParameterizedTest
    @MethodSource("provideTokenTestCases")
    fun testTokenPatternMatching(input: String, expectedTokens: List<String>) {
        val startOffset = 0
        val tokens = tokenService.findAllCursingTokensWithin(input, startOffset)
        assertEquals(expectedTokens, tokens.map { it.text })
    }

    companion object {
        @JvmStatic
        fun provideTokenTestCases(): Stream<Arguments> {
            return Stream.of(
                Arguments.of("abc", listOf("abc")),
                Arguments.of("abc def", listOf("abc", "def")),
                Arguments.of("abc+def", listOf("abc", "+", "def")),
                Arguments.of("abc(def)", listOf("abc", "(", "def", ")")),
                Arguments.of("abc{def}", listOf("abc", "{", "def", "}")),
                Arguments.of("abc[def]", listOf("abc", "[", "def", "]")),
                Arguments.of("abc<def>", listOf("abc", "<", "def", ">")),
                Arguments.of("abc,def", listOf("abc", ",", "def")),
                Arguments.of("abc:def", listOf("abc", ":", "def")),
                Arguments.of("abc ?: def", listOf("abc", "?:", "def")),
                Arguments.of("/*comment*/", listOf("/*", "comment", "*/")),
                Arguments.of("# hello", listOf("#", "hello")),
                Arguments.of("// foo", listOf("//", "foo")),
                Arguments.of("hey you, over there.", listOf("hey", "you", ",", "over", "there"))// dot is ignored
            )
        }
    }
}
