package org.cursing_less.commands

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager

data object FindCommand : VoiceCommand {

    override fun matches(command: String) = command == "find"


    override fun run(commandParameters: List<String>, project: Project, editor: Editor): String {
        ApplicationManager.getApplication().invokeAndWait {
            val document = editor.document
            val selection = editor.selectionModel
            val findManager = FindManager.getInstance(project)
            val findModel = FindModel()

            val direction = commandParameters[0]
            val searchTerm = commandParameters.subList(1, commandParameters.size).joinToString(separator = " ")

            findModel.stringToFind = searchTerm
            findModel.isCaseSensitive = false
            findModel.isRegularExpressions = true
            findModel.isForward = direction == "next"
            val result = findManager.findString(
                document.charsSequence,
                editor.caretModel.offset,
                findModel
            )
            if (result.isStringFound) {
                if (direction == "next") {
                    editor.caretModel.moveToOffset(result.endOffset)
                } else {
                    editor.caretModel.moveToOffset(result.startOffset)
                }
                selection.setSelection(result.startOffset, result.endOffset)
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
            }
        }
        return "OK"
    }
}
