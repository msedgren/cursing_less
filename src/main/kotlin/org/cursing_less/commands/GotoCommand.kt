package org.cursing_less.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import kotlin.math.max

data object GotoCommand : VoiceCommand {

    override fun matches(command: String) = command == "goto"

    override fun run(commandParameters: List<String>, project: Project, editor: Editor?): String {
        if(editor != null) {
            // Both count from 0, so adjust.
            val line = max((commandParameters[0].toInt() - 1).toDouble(), 0.0).toInt()
            val column = max((commandParameters[1].toInt() - 1).toDouble(), 0.0).toInt()
            val pos = LogicalPosition(line, column)
            ApplicationManager.getApplication().invokeAndWait {
                editor.caretModel.removeSecondaryCarets()
                editor.caretModel.moveToLogicalPosition(pos)
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                editor.selectionModel.removeSelection()
                IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
            }
        }
        return "OK"
    }
}
