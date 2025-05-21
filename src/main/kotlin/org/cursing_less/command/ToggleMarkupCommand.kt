package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkupService

data object ToggleMarkupCommand : VoiceCommand {
    private val markupService: CursingMarkupService by lazy {
        getApplication().getService(CursingMarkupService::class.java)
    }

    override fun matches(command: String) = command == "toggle_markup"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        markupService.toggleEnabled()
        return CursingCommandService.OkayResponse
    }
}
