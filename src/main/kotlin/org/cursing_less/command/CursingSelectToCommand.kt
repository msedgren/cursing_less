package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingColorShapeLookupService
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingSelectionService

data object CursingSelectToCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_select_to"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 4) {
            val mode = commandParameters[0]

            val cursingColorShapeLookupService = ApplicationManager.getApplication()
                .getService(CursingColorShapeLookupService::class.java)
            val cursingSelectionService = ApplicationManager.getApplication()
                .getService(CursingSelectionService::class.java)

            val colorShape =
                cursingColorShapeLookupService.parseToColorShape(commandParameters[1], commandParameters[2])
            val character = commandParameters[3].firstOrNull()

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

                        // Perform action based on mode
                        when (mode) {
                            "select" -> {
                                cursingSelectionService.select(selectionStart, selectionEnd, editor)
                            }

                            "copy" -> {
                                cursingSelectionService.select(selectionStart, selectionEnd, editor)
                                editor.selectionModel.copySelectionToClipboard()
                            }

                            "cut" -> {
                                cursingSelectionService.select(selectionStart, selectionEnd, editor)
                                editor.selectionModel.copySelectionToClipboard()

                                // Then delete the text
                                withContext(Dispatchers.Default) {
                                    cursingSelectionService.deleteText(
                                        selectionStart,
                                        selectionEnd,
                                        editor,
                                        project
                                    )
                                }
                            }

                            "clear" -> {
                                // Then delete the text directly without selecting it
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
                    }
                    return CursingCommandService.OkayResponse
                }
            }
        }
        return CursingCommandService.BadResponse
    }
}
