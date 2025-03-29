package org.cursing_less.commands

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.Notification
import com.intellij.openapi.application.ApplicationManager
import org.cursing_less.services.CommandService
import org.cursing_less.services.CursingMarkupService
import org.cursing_less.services.CursingPreferenceService


class EchoCommand : VoiceCommand {

    override fun matches(command: String) = command == "toggle_echo"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {

        val cursingPreferenceService =
            ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
        cursingPreferenceService.toggleEchoCommands()
        return CommandService.OkayResponse
    }
}