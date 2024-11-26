package org.cursing_less.commands

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

sealed interface VoiceCommand {

    fun matches(command: String): Boolean
    fun run(commandParameters: List<String>, project: Project, editor: Editor): String

}
