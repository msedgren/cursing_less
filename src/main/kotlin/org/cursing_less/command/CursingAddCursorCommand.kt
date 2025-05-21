package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingSelectionService

data object CursingAddCursorCommand : VoiceCommand {

    private val cursingMarkupService by lazy {
        getApplication().getService(CursingMarkupService::class.java)
    }

    override fun matches(command: String) = command == "curse_add_cursor"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 3) {
            val cursingSelectionService = getApplication().getService(CursingSelectionService::class.java)

            val consumedData = cursingSelectionService.find(commandParameters, editor)

            if (consumedData != null) {
                // Add cursor at the token position on EDT
                withContext(Dispatchers.EDT) {
                    // Get the logical position for the offset
                    val offset = consumedData.startOffset
                    val logicalPosition = editor.offsetToLogicalPosition(offset)
                    val visualPosition = editor.logicalToVisualPosition(logicalPosition)

                    // Add a secondary caret at the position
                    val caret = editor.caretModel.addCaret(visualPosition)

                    if (caret != null) {
                        // Update the markup to reflect the changes
                        cursingMarkupService.updateCursingTokens(editor, offset)
                    }
                }
                return CursingCommandService.OkayResponse
            }
        }
        return CursingCommandService.BadResponse
    }
}
