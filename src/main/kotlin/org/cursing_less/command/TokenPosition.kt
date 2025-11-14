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
         * Checks if the given string represents the START position.
         * @param code The string value to check.
         * @return True if the string represents the START position, false otherwise.
         */
        fun isStart(code: String): Boolean = code == START.code
    }
}
