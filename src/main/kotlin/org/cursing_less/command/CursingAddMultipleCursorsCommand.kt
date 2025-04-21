package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.util.CARET_NUMBER_KEY

data object CursingAddMultipleCursorsCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_add_multiple_cursors"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 2) {
            val count = commandParameters[0].toIntOrNull()
            val direction = commandParameters[1].lowercase()
            
            if (count != null && (direction == "above" || direction == "below") && count > 0) {
                // Add multiple cursors on EDT
                withContext(Dispatchers.EDT) {
                    val primaryCaret = editor.caretModel.primaryCaret
                    val currentPosition = primaryCaret.logicalPosition
                    val document = editor.document
                    
                    // Determine the direction multiplier (negative for above, positive for below)
                    val directionMultiplier = if (direction == "above") -1 else 1
                    
                    // Add the specified number of carets
                    for (i in 1..count) {
                        val targetLine = currentPosition.line + (i * directionMultiplier)
                        
                        // Skip if the target line is out of bounds
                        if (targetLine < 0 || targetLine >= document.lineCount) {
                            continue
                        }
                        
                        // Calculate the column, capped at the line length
                        val lineEndOffset = document.getLineEndOffset(targetLine)
                        val lineStartOffset = document.getLineStartOffset(targetLine)
                        val lineLength = lineEndOffset - lineStartOffset
                        if(lineLength >= currentPosition.column) {
                            // Create a new logical position
                            val newPosition = LogicalPosition(targetLine, currentPosition.column)

                            // Add a caret at the new position
                            val newCaret = editor.caretModel.addCaret(editor.logicalToVisualPosition(newPosition))

                            // Store the caret number in user data if the caret was added successfully
                            if (newCaret != null) {
                                val caretCount = editor.caretModel.caretCount
                                newCaret.putUserData(CARET_NUMBER_KEY, caretCount)
                            }
                        }
                    }
                    
                    // Update the markup to reflect the changes
                    val cursingMarkupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
                    cursingMarkupService.updateCursingTokens(editor, editor.caretModel.offset)
                }
                return CursingCommandService.OkayResponse
            }
        }
        return CursingCommandService.BadResponse
    }
}