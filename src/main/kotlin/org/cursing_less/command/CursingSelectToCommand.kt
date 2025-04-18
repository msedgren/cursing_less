package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.http.HttpStatus
import org.cursing_less.service.CursingColorShapeLookupService
import org.cursing_less.service.CursingCommandService

data object CursingSelectToCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_select_to"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 3) {
            val cursingColorShapeLookupService = ApplicationManager.getApplication()
                .getService(CursingColorShapeLookupService::class.java)

            val colorShape =
                cursingColorShapeLookupService.parseToColorShape(commandParameters[0], commandParameters[1])
            val character = commandParameters[2].firstOrNull()

            if (colorShape != null && character != null) {
                val consumedData = cursingColorShapeLookupService.findConsumed(colorShape, character, editor)

                if (consumedData != null) {
                    // Get current caret position and set selection on EDT
                    withContext(Dispatchers.EDT) {
                        val currentOffset = editor.caretModel.offset

                        // Select from current position to the token
                        val startOffset = currentOffset
                        val endOffset = consumedData.endOffset

                        // Make sure we're selecting in the right direction
                        val selectionStart = minOf(startOffset, endOffset)
                        val selectionEnd = maxOf(startOffset, endOffset)

                        // Set the selection
                        editor.selectionModel.setSelection(selectionStart, selectionEnd)
                    }

                    return CursingCommandService.OkayResponse
                }
            }
        }
        return CursingCommandService.BadResponse
    }
}
