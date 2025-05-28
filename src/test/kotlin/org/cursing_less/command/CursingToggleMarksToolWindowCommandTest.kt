package org.cursing_less.command

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CursingToggleMarksToolWindowCommandTest {

    @Test
    fun testMatches() {
        // Test that the command matches the correct string
        assertTrue(CursingToggleMarksToolWindowCommand.matches("curse_toggle_marks_window"))
        assertFalse(CursingToggleMarksToolWindowCommand.matches("curse_mark"))
        assertFalse(CursingToggleMarksToolWindowCommand.matches("curse_clear_all_marks"))
    }
}