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

    private val cursingMarkupService: CursingMarkupService by lazy {
        ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
    }

    override fun matches(command: String) = command == "curse_remove_cursor"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 1) {
            val cursorToRemove = commandParameters[0].toIntOrNull()

            // Remove cursor with the matching index on EDT
            return withContext(Dispatchers.EDT) {
                val carets = editor.caretModel.allCarets

                // Check if the cursor index is valid
                if (cursorToRemove != null && cursorToRemove > 0 && cursorToRemove <= carets.size) {
                    editor.caretModel.removeCaret(carets[cursorToRemove - 1])
                    val primaryOffset = editor.caretModel.primaryCaret.offset
                    // Update the markup to reflect the changes
                    cursingMarkupService.updateCursingTokens(editor, primaryOffset)
                    CursingCommandService.OkayResponse
                } else {
                    CursingCommandService.BadResponse
                }
            }
        }
        return CursingCommandService.BadResponse
    }
}
