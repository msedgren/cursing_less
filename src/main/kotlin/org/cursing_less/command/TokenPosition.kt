package org.cursing_less.command

/**
 * Represents a position relative to a token.
 * Replaces hardcoded "pre" and "post"
 */
enum class TokenPosition(val code: String) {
    START("pre"),
    END("post");

    companion object {
        /**
         * Converts a string to a TokenPosition.
         * @param code The string value to convert.
         * @return The corresponding TokenPosition, or null if no match is found.
         */
        fun fromString(code: String): TokenPosition? = entries.find {
            it.code == code
        }

        /**
         * Checks if the given string represents the START position.
         * @param code The string value to check.
         * @return True if the string represents the START position, false otherwise.
         */
        fun isStart(code: String): Boolean = code == START.code

        /**
         * Checks if the given string represents the END position.
         * @param code The string value to check.
         * @return True if the string represents the END position, false otherwise.
         */
        fun isEnd(code: String): Boolean = code == END.code
    }
}
