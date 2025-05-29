package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingPreferenceService

@Suppress("unused")
data object EchoCommand : VoiceCommand {
    private val cursingPreferenceService by lazy {
        getApplication().getService(CursingPreferenceService::class.java)
    }

    override fun matches(command: String) = command == "toggle_echo"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        cursingPreferenceService.toggleEchoCommands()
        return CursingCommandService.OkayResponse
    }
}