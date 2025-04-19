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
import org.cursing_less.service.CursingSelectionService

data object CursingCopyToCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_copy_to"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 4) {
            val cursingColorShapeLookupService = ApplicationManager.getApplication()
                .getService(CursingColorShapeLookupService::class.java)
            val cursingSelectionService = ApplicationManager.getApplication()
                .getService(CursingSelectionService::class.java)

            val cut = commandParameters[0] == "cut"
            val colorShape =
                cursingColorShapeLookupService.parseToColorShape(commandParameters[1], commandParameters[2])
            val character = commandParameters[3].firstOrNull()

            if (colorShape != null && character != null) {
                println("[DEBUG_LOG] Looking for token with color: $colorShape, character: $character")
                val consumedData = cursingColorShapeLookupService.findConsumed(colorShape, character, editor)
                println("[DEBUG_LOG] Found token: $consumedData")

                if (consumedData != null) {
                    // Get current caret position, set selection, and copy/cut on EDT
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

                        // Copy the selection
                        editor.selectionModel.copySelectionToClipboard()

                        // Cut if requested
                        if (cut) {
                            // We need to exit EDT context to call cutSelectedText which has its own EDT context
                            withContext(Dispatchers.Default) {
                                cursingSelectionService.deleteText(
                                    selectionStart,
                                    selectionEnd,
                                    editor,
                                    project
                                )
                            }
                        }
                    }

                    return CursingCommandService.OkayResponse
                }
            }
        }
        return CursingCommandService.BadResponse
    }
}
