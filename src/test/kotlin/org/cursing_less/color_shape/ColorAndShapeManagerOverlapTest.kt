package org.cursing_less.color_shape

import com.intellij.ui.JBColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Tests for the ColorAndShapeManager focusing on overlapping token scenarios.
 * This test class uses parameterized tests to verify behavior with multiple overlapping tokens.
 */
class ColorAndShapeManagerOverlapTest {

    private lateinit var colors: List<CursingColor>
    private lateinit var shapes: List<CursingShape>

    @BeforeEach
    fun setUp() {
        colors = listOf(
            CursingColor("red", JBColor.RED),
            CursingColor("blue", JBColor.BLUE)
        )
        shapes = listOf(
            CursingShape.Circle,
            CursingShape.Square
        )
    }

    /**
     * Data class representing a token to consume with its offset and text.
     */
    data class TokenToConsume(val offset: Int, val text: String)

    /**
     * Data class representing the expected state of a consumed token.
     */
    data class ExpectedToken(val offset: Int, val consumedText: String)

    /**
     * Parameterized test for consuming multiple overlapping tokens.
     * Tests various scenarios of token overlap and verifies the expected state after consumption.
     */
    @ParameterizedTest
    @MethodSource("provideOverlapConsumptionTestCases")
    fun testOverlappingTokenConsumption(
        tokensToConsume: List<TokenToConsume>,
        expectedTokens: List<ExpectedToken>
    ) {
        val manager = ColorAndShapeManager(colors, shapes)

        // Consume all tokens in the provided order
        tokensToConsume.forEach { token -> manager.consume(token.offset, token.text) }

        // Verify the state of all consumed tokens
        val allConsumed = manager.getAllConsumed()
            .values
            .map { ExpectedToken(it.startOffset, it.consumedText) }
            .sortedBy { it.offset }

        // Check that we have the expected number of tokens
        assertEquals(expectedTokens, allConsumed)
    }

    /**
     * Parameterized test for freeing tokens in overlapping scenarios.
     * Tests what happens when tokens are freed in various orders and verifies the expected state.
     */
    @ParameterizedTest
    @MethodSource("provideOverlapFreeingTestCases")
    fun testOverlappingTokenFreeing(
        tokensToConsume: List<TokenToConsume>,
        tokensToFree: List<Int>,
        expectedTokens: List<ExpectedToken>
    ) {
        val manager = ColorAndShapeManager(colors, shapes)

        // Consume all tokens in the provided order
        tokensToConsume.forEach { token -> manager.consume(token.offset, token.text) }

        // Free tokens in the specified order
        tokensToFree.forEach { offset -> manager.free(offset) }

        // Verify the state of all consumed tokens
        val allConsumed = manager.getAllConsumed()
            .values
            .map { ExpectedToken(it.startOffset, it.consumedText) }
            .sortedBy { it.offset }

        // Check that we have the expected number of tokens
        assertEquals(expectedTokens, allConsumed)
    }

    companion object {
        /**
         * Provides test cases for overlapping token consumption.
         * Each test case consists of:
         * 1. A list of tokens to consume (offset and text)
         * 2. The expected state of consumed tokens after all operations
         */
        @JvmStatic
        fun provideOverlapConsumptionTestCases(): Stream<Arguments> {
            return Stream.of(
                // Case 1: Simple non-overlapping tokens
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Let us"),
                        TokenToConsume(7, "remember"),
                        TokenToConsume(16, "how the")
                    ),
                    listOf(
                        ExpectedToken(0, "Let us"),
                        ExpectedToken(7, "remember"),
                        ExpectedToken(16, "how the")
                    )
                ),
                // Case 2: Two tokens with start overlap
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Let us remember"),
                        TokenToConsume(7, "remember how")
                    ),
                    listOf(
                        ExpectedToken(0, "Let us "),
                        ExpectedToken(7, "remember how")
                    )
                ),
                // Case 3: Three tokens with overlaps
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Let us remember"),
                        TokenToConsume(7, "remember how the"),
                        TokenToConsume(16, "how the sky")
                    ),
                    listOf(
                        ExpectedToken(0, "Let us "),
                        ExpectedToken(7, "remember "),
                        ExpectedToken(16, "how the sky")
                    )
                ),
                // Case 4: Token completely contained within another
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Let us remember how the sky"),
                        TokenToConsume(7, "remember how the"),
                        TokenToConsume(16, "how the sky")
                    ),
                    listOf(
                        ExpectedToken(0, "Let us "),
                        ExpectedToken(7, "remember "),
                        ExpectedToken(16, "how the sky")
                    )
                ),

                // Case 5: Multiple overlapping tokens in reverse order
                Arguments.of(
                    listOf(
                        TokenToConsume(16, "how the sky"),
                        TokenToConsume(7, "remember how the"),
                        TokenToConsume(0, "Let us remember how the sky")
                    ),
                    listOf(
                        ExpectedToken(0, "Let us "),
                        ExpectedToken(7, "remember "),
                        ExpectedToken(16, "how the sky")
                    )
                ),

                // Case 6: Complex overlapping scenario
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Let us remember how"),
                        TokenToConsume(16, "how the sky went"),
                        TokenToConsume(7, "remember how the"),
                        TokenToConsume(28, "went dark")
                    ),
                    listOf(
                        ExpectedToken(0, "Let us "),
                        ExpectedToken(7, "remember "),
                        ExpectedToken(16, "how the sky "),
                        ExpectedToken(28, "went dark")
                    )
                ),

                // Case 8: Tokens with special characters and whitespace
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Hello, world!"),
                        TokenToConsume(7, "world! How are you?")
                    ),
                    listOf(
                        ExpectedToken(0, "Hello, "),
                        ExpectedToken(7, "world! How are you?")
                    )
                ),

                // Case 9: Multiple tokens with complex overlaps
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "The quick brown"),
                        TokenToConsume(4, "quick brown fox"),
                        TokenToConsume(16, "fox jumps over"),
                        TokenToConsume(10, "brown fox jumps")
                    ),
                    listOf(
                        ExpectedToken(0, "The "),
                        ExpectedToken(4, "quick "),
                        ExpectedToken(10, "brown "),
                        ExpectedToken(16, "fox jumps over")
                    )
                ),

                // Case 10: Consuming a token with whitespace at the beginning or end
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Hello world"),
                        TokenToConsume(5, " world ") // Has whitespace at beginning and end
                    ),
                    listOf(
                        ExpectedToken(0, "Hello"),
                        ExpectedToken(5, " world ")
                    )
                )
            )
        }

        /**
         * Provides test cases for freeing overlapping tokens.
         * Each test case consists of:
         * 1. A list of tokens to consume (offset and text)
         * 2. A list of token offsets to free
         * 3. The expected state of consumed tokens after freeing
         */
        @JvmStatic
        fun provideOverlapFreeingTestCases(): Stream<Arguments> {
            return Stream.of(
                // Case 1: Free last
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Let us remember"),
                        TokenToConsume(7, "remember how")
                    ),
                    listOf(7), // Free the second token
                    listOf(
                        ExpectedToken(0, "Let us remember")
                    )
                ),
                // Case 2: Free first
                Arguments.of(
                    listOf(
                        TokenToConsume(7, "remember how"),
                        TokenToConsume(16, "how the sky")
                    ),
                    listOf(7), // Free the first token
                    listOf(
                        ExpectedToken(16, "how the sky")
                    )
                ),
                // Case 3: Free tokens in a chain of overlaps
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Let us remember"),
                        TokenToConsume(7, "remember how"),
                        TokenToConsume(16, "how the sky")
                    ),
                    listOf(7), // Free the middle token
                    listOf(
                        ExpectedToken(0, "Let us remember"),
                        ExpectedToken(16, "how the sky")
                    )
                ),
                // Case 4: Free multiple tokens in sequence
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Let us remember"),
                        TokenToConsume(7, "remember how"),
                        TokenToConsume(16, "how the sky")
                    ),
                    listOf(0, 16), // Free first and last tokens
                    listOf(
                        ExpectedToken(7, "remember how")
                    )
                ),
                // Case 5: Complex scenario with multiple overlapping tokens
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Let us remember how"),
                        TokenToConsume(16, "how the sky went"),
                        TokenToConsume(7, "remember how the"),
                        TokenToConsume(28, "went dark")
                    ),
                    listOf(7, 28), // Free middle and last tokens
                    listOf(
                        ExpectedToken(0, "Let us remember "),
                        ExpectedToken(16, "how the sky went")
                    )
                ),
                // Case 6: Free all tokens
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "Let us remember"),
                        TokenToConsume(7, "remember how"),
                        TokenToConsume(16, "how the sky")
                    ),
                    listOf(16, 7, 0), // Free in reverse order
                    listOf<ExpectedToken>() // All tokens should be freed
                ),
                // Case 7: Complex scenario with multiple freeing operations
                Arguments.of(
                    listOf(
                        TokenToConsume(0, "The quick brown"),
                        TokenToConsume(4, "quick brown fox"),
                        TokenToConsume(16, "fox jumps over"),
                        TokenToConsume(10, "brown fox jumps")
                    ),
                    listOf(4, 16), // Free two tokens
                    listOf(
                        ExpectedToken(0, "The quick "),
                        ExpectedToken(10, "brown fox jumps")
                    )
                ),
            )
        }
    }
}
