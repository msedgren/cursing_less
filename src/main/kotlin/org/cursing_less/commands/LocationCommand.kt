package org.cursing_less.commands

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

class LocationCommand : VoiceCommand {

    override fun matches(command: String) = command == "location"

    override fun run(commandParameters: List<String>, project: Project, editor: Editor): String {
        val logicalPosition = editor.caretModel.logicalPosition
        return String.format("%d %d", logicalPosition.line + 1, logicalPosition.column + 1)
    }
}
