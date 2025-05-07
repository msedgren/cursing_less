package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService

data object CursingRemoveAllSecondaryCursorsCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_remove_all_secondary_cursors"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null) {
            // Remove all secondary cursors on EDT
            return withContext(Dispatchers.EDT) {
                // Check if there are secondary carets to remove
                if (editor.caretModel.caretCount > 1) {
                    // Remove all secondary carets
                    editor.caretModel.removeSecondaryCarets()

                    // Update the markup to reflect the changes
                    val cursingMarkupService =
                        ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
                    cursingMarkupService.updateCursingTokens(editor, editor.caretModel.offset)
                    CursingCommandService.OkayResponse
                } else {
                    CursingCommandService.BadResponse
                }
            }
        }
        return CursingCommandService.BadResponse
    }
}
