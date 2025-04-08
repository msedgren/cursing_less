package org.cursing_less.command

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.apache.http.HttpStatus

data object LocationCommand : VoiceCommand {

    override fun matches(command: String) = command == "location"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if(editor != null) {
            val logicalPosition = editor.caretModel.logicalPosition
            return VoiceCommandResponse(HttpStatus.SC_OK, String.format("%d %d", logicalPosition.line + 1, logicalPosition.column + 1))
        } else {
            return VoiceCommandResponse(HttpStatus.SC_OK, "0 0")
        }
    }
}
