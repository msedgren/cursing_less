package org.cursing_less.command

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingPreferenceService


data object  EchoCommand : VoiceCommand {

    override fun matches(command: String) = command == "toggle_echo"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {

        val cursingPreferenceService =
            ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
        cursingPreferenceService.toggleEchoCommands()
        return CursingCommandService.OkayResponse
    }
}