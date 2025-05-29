package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkStorageService

/**
 * Command to mark (store) the text selected by a cursor.
 * The text is stored in the CursingMarkStorageService associated with a given mark number.
 * The starting offset of the selection is also stored.
 */
data object CursingMarkCommand : VoiceCommand {

    private val markStorageService: CursingMarkStorageService by lazy {
        ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
    }

    override fun matches(command: String) = command == "curse_mark"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor == null) {
            return CursingCommandService.BadResponse
        }

        // Parse the mark number from command parameters
        val markNumber = if (commandParameters.isNotEmpty()) {
            commandParameters[0].toInt()
        } else {
            // Default to 1 if no parameter is provided
            1
        }

        // Use readAction to safely access the editor model
        val result = readAction {
            // Get the current selection
            val selectedText = editor.selectionModel.selectedText
            if (selectedText.isNullOrEmpty()) {
                null
            } else {
                // Get the selection start and end offsets
                val selectionStart = editor.selectionModel.selectionStart
                val selectionEnd = editor.selectionModel.selectionEnd

                // Return the selected text and selection start offset
                Triple(selectedText, selectionStart, selectionEnd)
            }
        }

        // If no selection or error occurred
        if (result == null) {
            return CursingCommandService.BadResponse
        } else {
            // Store the selected text and its starting offset in the mark storage service
            val (selectedText, startOffset, _) = result
            markStorageService.storeMarkedText(markNumber, selectedText, startOffset)

            return CursingCommandService.OkayResponse
        }
    }
}
