package org.cursing_less.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager

data object RangeCommand : VoiceCommand {

    override fun matches(command: String) = command == "range"

    override fun run(commandParameters: List<String>, project: Project, editor: Editor): String {
        ApplicationManager.getApplication().invokeAndWait {
            val startLine = commandParameters[0].toInt() - 1
            val endLine = commandParameters[1].toInt() - 1
            val selection = editor.selectionModel
            editor.caretModel.moveToLogicalPosition(LogicalPosition(startLine, 0))
            val startOffset = editor.caretModel.offset
            editor.caretModel.moveToLogicalPosition(LogicalPosition(endLine + 1, 0))
            val endOffset = editor.caretModel.offset - 1
            selection.setSelection(startOffset, endOffset)
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
        }

        return "OK"
    }
}
