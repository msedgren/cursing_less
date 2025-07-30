package org.cursing_less.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.JBColor
import com.intellij.util.application
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingShape
import org.cursing_less.util.CursingTestUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class CursingTokenServiceTest {

    private lateinit var codeInsightFixture: CodeInsightTestFixture
    private lateinit var tokenService: CursingTokenService
    private lateinit var colorAndShapeManager: ColorAndShapeManager


    @BeforeEach
    fun setUp() {
        codeInsightFixture = CursingTestUtils.setupTestFixture()

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
            .map { it.text }
            .toList()

        // Check specific tokens
        assertEquals(
            listOf("function", "test", "()", "{", "return", "x", "+", "y", ";", "}"),
            tokens)
    }

    @ParameterizedTest
    @MethodSource("provideTokenTestCases")
    fun testTokenPatternMatching(input: String, expectedTokens: List<String>) {
        val startOffset = 0
        val tokens = tokenService
            .findAllCursingTokensWithin(input, startOffset)
            .map { it.text }
            .toList()
        assertEquals(expectedTokens, tokens)
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
                Arguments.of("hey you, over there.", listOf("hey", "you", ",", "over", "there")),
                Arguments.of("foo(a!!, b)", listOf("foo", "(", "a", "!!", ",", "b", ")")),
            )
        }
    }
}
