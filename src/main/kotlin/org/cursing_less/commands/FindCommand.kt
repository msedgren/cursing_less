package org.cursing_less.commands

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.services.CursingCommandService

data object FindCommand : VoiceCommand {

    override fun matches(command: String) = command == "find"


    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if(editor != null) {
            withContext(Dispatchers.EDT) {
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
        }
        return CursingCommandService.OkayResponse
    }
}
