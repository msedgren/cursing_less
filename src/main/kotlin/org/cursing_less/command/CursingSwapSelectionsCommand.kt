package org.cursing_less.command

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import kotlin.math.max
import kotlin.math.min

/**
 * Command that swaps the content of two cursor selections identified by their relative number.
 */
data object CursingSwapSelectionsCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_swap_selections"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 2) {
            // Parse the selection indices
            val firstSelectionIndex = commandParameters[0].toInt()
            val secondSelectionIndex = commandParameters[1].toInt()

            // Perform the swap on EDT
            var success = false
            withContext(Dispatchers.EDT) {
                success = swapSelections(editor, firstSelectionIndex, secondSelectionIndex, project)
            }

            return if (success) CursingCommandService.OkayResponse else CursingCommandService.BadResponse
        }
        return CursingCommandService.BadResponse
    }

    private suspend fun swapSelections(
        editor: Editor,
        firstCursor: Int,
        secondCursor: Int,
        project: Project
    ): Boolean {
        val document = editor.document

        // Read the current selections
        return readAndWriteAction {
            val caretModel = editor.caretModel
            val allCarets = caretModel.allCarets

            // Get the two carets
            val firstCaret = allCarets.getOrNull(min(firstCursor, secondCursor) - 1)
            val secondCaret = allCarets.getOrNull(max(firstCursor, secondCursor) - 1)
            // Get the selection ranges
            val firstStart = firstCaret?.selectionStart ?: 0
            val firstEnd = firstCaret?.selectionEnd ?: 0
            val secondStart = secondCaret?.selectionStart ?: 0
            val secondEnd = secondCaret?.selectionEnd ?: 0

            if (firstStart == firstEnd || secondStart == secondEnd || firstEnd > secondStart) {
                writeAction { false }
            } else {
                // Get the selected text
                val firstText = document.getText(TextRange(firstStart, firstEnd))
                val secondText = document.getText(TextRange(secondStart, secondEnd))

                writeAction {
                    WriteCommandAction.writeCommandAction(project)
                        .withName("Swap Selections")
                        .compute<Boolean, Throwable> {
                            document.replaceString(secondStart, secondEnd, firstText)
                            document.replaceString(firstStart, firstEnd, secondText)
                            true
                        }
                }
            }
        }
    }
}
