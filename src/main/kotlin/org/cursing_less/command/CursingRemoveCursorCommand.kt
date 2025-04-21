package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.util.CARET_NUMBER_KEY

data object CursingRemoveCursorCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_remove_cursor"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 1) {
            val cursorNumber = commandParameters[0].toIntOrNull()
            
            if (cursorNumber != null) {
                // Remove cursor with the matching number on EDT
                return withContext(Dispatchers.EDT) {
                    val carets = editor.caretModel.allCarets
                    
                    // Find the caret with the matching number
                    val caretToRemove = if(cursorNumber == 1) {
                        editor.caretModel.primaryCaret
                    } else {
                        carets.find { caret ->
                            val caretNumber = caret.getUserData(CARET_NUMBER_KEY)
                            caretNumber == cursorNumber
                        }
                    }
                    
                    // Remove the caret if found
                    if (caretToRemove != null && carets.size > 1) {
                        editor.caretModel.removeCaret(caretToRemove)
                        
                        // Update the markup to reflect the changes
                        val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
                        cursingMarkupService.updateCursingTokens(editor, editor.caretModel.offset)
                        CursingCommandService.OkayResponse
                    } else {
                        CursingCommandService.BadResponse
                    }
                }
            }
        }
        return CursingCommandService.BadResponse
    }
}