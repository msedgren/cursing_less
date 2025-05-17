package org.cursing_less.service

import com.intellij.openapi.util.ProperTextRange
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingColorShape
import org.cursing_less.color_shape.CursingShape
import org.cursing_less.util.OffsetDistanceComparator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import com.intellij.ui.JBColor

/**
 * Test-specific implementation of CursingTokenService that doesn't rely on CursingPreferenceService
 */
class TestCursingTokenService {
    // Use a pattern that treats parentheses, braces, angle brackets, and square brackets as individual tokens
    private val tokenPattern: Regex = Regex("([\\w]+)|([()])|([{}])|([<>])|([\\[\\]])|(/\\*)|(\\*/)|([,\"'`:#])|([?][:])|([^\\w(){}<>\\[\\]\\s.\"'`:#]+)")

    /**
     * Represents a token found in text with its start and end offsets and the text content.
     */
    data class CursingToken(val startOffset: Int, val endOffset: Int, val text: String)

    /**
     * Finds all tokens within the given text starting from the specified offset.
     */
    fun findAllCursingTokensWithin(text: String, startOffset: Int): List<CursingToken> {
        return tokenPattern.findAll(text).iterator().asSequence()
            .filter { it.value.isNotBlank() && !it.value[0].isWhitespace() }
            .map { CursingToken(startOffset + it.range.first, startOffset + it.range.last + 1, it.value) }.toList()
    }

    /**
     * Consumes tokens in the visible area of text, filtering out already known tokens.
     */
    fun consumeVisible(
        colorAndShapeManager: ColorAndShapeManager,
        currentOffset: Int,
        alreadyKnown: Set<Int>,
        text: String,
        visibleArea: ProperTextRange
    ): List<Pair<Int, Pair<Char, CursingColorShape>>> {
        val tokens = findAllCursingTokensWithin(text, visibleArea.startOffset)
        val offsetComparator = OffsetDistanceComparator<TestCursingTokenService.CursingToken>(currentOffset) { it.startOffset }
        return tokens
            .filterNot { alreadyKnown.contains(it.startOffset) }
            .sortedWith(offsetComparator)
            .mapNotNull {
                colorAndShapeManager.consume(it.startOffset, it.text)?.let { consumed ->
                    Pair(it.startOffset, Pair(it.text[0], consumed))
                }
            }.toList()
    }
}

class CursingTokenServiceTest {
    private lateinit var tokenService: TestCursingTokenService
    private lateinit var colorAndShapeManager: ColorAndShapeManager

    @BeforeEach
    fun setUp() {
        tokenService = TestCursingTokenService()

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
