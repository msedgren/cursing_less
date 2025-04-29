package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingSelectionService

data object CursingSelectToCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_select_to"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 4) {
            val mode = commandParameters[0]
            val cursingSelectionService = getApplication().getService(CursingSelectionService::class.java)

            val consumedData = cursingSelectionService.find(commandParameters.drop(1), editor)

            if (consumedData != null) {
                // Get current caret position and set selection on EDT
                withContext(Dispatchers.EDT) {
                    val currentOffset = editor.caretModel.offset

                    // Select from current position to the token
                    val startOffset = currentOffset
                    val endOffset =
                        if (currentOffset <= consumedData.startOffset) consumedData.endOffset else consumedData.startOffset

                    // Make sure we're selecting in the right direction
                    val selectionStart = minOf(startOffset, endOffset)
                    val selectionEnd = maxOf(startOffset, endOffset)

                    // Set the selection
                    editor.selectionModel.setSelection(selectionStart, selectionEnd)

                    // Perform action based on mode
                    cursingSelectionService.handleMode(mode, selectionStart, selectionEnd, editor, project)
                }
                return CursingCommandService.OkayResponse
            }
        }
        return CursingCommandService.BadResponse
    }
}
