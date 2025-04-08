package org.cursing_less.command

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

sealed interface VoiceCommand {

    fun matches(command: String): Boolean
    suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse

}

data class VoiceCommandResponse(val responseCode: Int, val response: String)
