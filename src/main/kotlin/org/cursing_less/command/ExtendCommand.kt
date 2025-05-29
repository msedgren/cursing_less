package org.cursing_less.command

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import kotlin.math.max
import kotlin.math.min

@Suppress("unused")
data object ExtendCommand : VoiceCommand {

    override fun matches(command: String) = command == "extend"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null) {
            withContext(Dispatchers.EDT) {
                val selection = editor.selectionModel
                val current = editor.caretModel.logicalPosition

                val targetLine = commandParameters[0].toInt() - 1
                val startLine = min(current.line.toDouble(), targetLine.toDouble()).toInt()
                val endLine = max(current.line.toDouble(), targetLine.toDouble()).toInt()

                RangeCommand.moveAndUpdateSelection(editor, startLine, endLine, selection)
            }
        }

        return CursingCommandService.OkayResponse
    }
}
