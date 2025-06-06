package org.cursing_less.command

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.apache.http.HttpStatus

/**
 * Simple command to check if the service is alive.
 * Returns "ping" with an OK status code.
 */
@Suppress("unused")
data object PingCommand : VoiceCommand {

    override fun matches(command: String) = command == "ping"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        return VoiceCommandResponse(HttpStatus.SC_OK, "ping")
    }
}
