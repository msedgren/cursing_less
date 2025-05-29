package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkStorageService

/**
 * Command to remove all marks.
 * This command clears all marked text stored in the CursingMarkStorageService.
 */
data object CursingClearAllMarksCommand : VoiceCommand {

    private val markStorageService: CursingMarkStorageService by lazy {
        ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
    }

    override fun matches(command: String) = command == "curse_clear_all_marks"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        // Clear all marked text
        markStorageService.clearAllMarkedText()
        
        return CursingCommandService.OkayResponse
    }
}