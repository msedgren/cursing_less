package org.cursing_less.commands

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.Notification


class EchoCommand : VoiceCommand {

    override fun matches(command: String) = command == "echo"

    override fun run(commandParameters: List<String>, project: Project, editor: Editor?): String {
        val message = commandParameters.joinToString(" ")
        val notification = Notification("cursing_less", "Echo Command", message, NotificationType.INFORMATION)
        Notifications.Bus.notify(notification, project)
        return "OK"
    }
}