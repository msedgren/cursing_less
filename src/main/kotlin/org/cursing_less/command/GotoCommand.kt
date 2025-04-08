package org.cursing_less.command

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import kotlin.math.max

data object GotoCommand : VoiceCommand {

    override fun matches(command: String) = command == "goto"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if(editor != null) {
            // Both count from 0, so adjust.
            val line = max((commandParameters[0].toInt() - 1).toDouble(), 0.0).toInt()
            val column = max((commandParameters[1].toInt() - 1).toDouble(), 0.0).toInt()
            val pos = LogicalPosition(line, column)
            withContext(Dispatchers.EDT) {
                editor.caretModel.removeSecondaryCarets()
                editor.caretModel.moveToLogicalPosition(pos)
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                editor.selectionModel.removeSelection()
                IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
            }
        }
        return CursingCommandService.OkayResponse
    }
}
