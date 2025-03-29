package org.cursing_less.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.cursing_less.services.CommandService
import org.cursing_less.services.CursingMarkupService

data object ToggleMarkupCommand : VoiceCommand {
    override fun matches(command: String) = command == "toggle_markup"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        val markupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        markupService.toggleEnabled()
        return CommandService.OkayResponse
    }
}
