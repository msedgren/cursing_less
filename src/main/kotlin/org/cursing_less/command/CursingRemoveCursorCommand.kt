package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService

data object CursingRemoveCursorCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_remove_cursor"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 1) {
            val cursorIndex = commandParameters[0].toIntOrNull()

            if (cursorIndex != null && cursorIndex > 0) {
                // Remove cursor with the matching index on EDT
                return withContext(Dispatchers.EDT) {
                    val carets = editor.caretModel.allCarets

                    // Check if the cursor index is valid
                    if (cursorIndex <= carets.size) {
                        // Get the caret at the specified index (1-based)
                        val caretToRemove = if (cursorIndex == 1) {
                            editor.caretModel.primaryCaret
                        } else {
                            // Convert to 0-based index for the list
                            carets[cursorIndex - 1]
                        }

                        // Remove the caret if found and there's more than one caret
                        if (carets.size > 1) {
                            editor.caretModel.removeCaret(caretToRemove)

                            // Update the markup to reflect the changes
                            val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
                            cursingMarkupService.updateCursingTokens(editor, editor.caretModel.offset)
                            CursingCommandService.OkayResponse
                        } else {
                            CursingCommandService.BadResponse
                        }
                    } else {
                        CursingCommandService.BadResponse
                    }
                }
            }
        }
        return CursingCommandService.BadResponse
    }
}
