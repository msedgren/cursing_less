package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkStorageService

/**
 * Command to remove the marked content for a specific mark number.
 * If no mark number is provided, it removes the content for mark number 1.
 */
data object CursingRemoveMarkCommand : VoiceCommand {

    private val markStorageService: CursingMarkStorageService by lazy {
        ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
    }

    override fun matches(command: String) = command == "curse_remove_mark"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (commandParameters.isEmpty()) {
            return CursingCommandService.BadResponse
        }

        // Parse the mark number from command parameters
        val markNumber = commandParameters[0].toIntOrNull()

        if (markNumber == null || markNumber <= 0) {
            return CursingCommandService.BadResponse
        }

        // Check if the mark exists
        if (markStorageService.getMarkedText(markNumber) == null) {
            return CursingCommandService.BadResponse
        }

        // Remove the marked text
        markStorageService.clearMarkedText(markNumber)

        return CursingCommandService.OkayResponse
    }
}