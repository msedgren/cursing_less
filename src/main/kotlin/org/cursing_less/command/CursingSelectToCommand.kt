package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingSelectionService

data object CursingSelectToCommand : VoiceCommand {

    private val cursingSelectionService: CursingSelectionService by lazy {
        getApplication().getService(CursingSelectionService::class.java)
    }

    override fun matches(command: String) = command == "curse_select_to"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null) {
            when (commandParameters.size) {
                4 -> return runWithDefaultBehavior(commandParameters, project, editor)
                5 -> return runWithExplicitStartSelection(commandParameters, project, editor)
            }
        }
        return CursingCommandService.BadResponse
    }

    private suspend fun runWithDefaultBehavior(
        commandParameters: List<String>,
        project: Project,
        editor: Editor
    ): VoiceCommandResponse {
        val mutable = commandParameters.toMutableList()
        mutable.add(1, TokenPosition.END.code)
        return runWithExplicitStartSelection(mutable, project, editor = editor)
    }

    private suspend fun runWithExplicitStartSelection(
        commandParameters: List<String>,
        project: Project,
        editor: Editor
    ): VoiceCommandResponse {
        val mode = commandParameters[0]
        val isStart = TokenPosition.isStart(commandParameters[1])

        val consumedData = cursingSelectionService.find(commandParameters.drop(2), editor)

        if (consumedData != null) {
            // Get current caret position and calculate endpoint within a read action
            val currentOffset = com.intellij.openapi.application.readAction { editor.caretModel.offset }
            val endOffset = if(isStart) consumedData.startOffset else consumedData.endOffset

            return performSelection(mode, currentOffset, endOffset, editor, project, cursingSelectionService)
        }
        return CursingCommandService.BadResponse
    }

    private suspend fun performSelection(
        mode: String,
        startOffset: Int,
        endOffset: Int,
        editor: Editor,
        project: Project,
        cursingSelectionService: CursingSelectionService
    ): VoiceCommandResponse {
        // Get current caret position and set selection on EDT
        withContext(Dispatchers.EDT) {
            // Make sure we're selecting in the right direction
            val selectionStart = minOf(startOffset, endOffset)
            val selectionEnd = maxOf(startOffset, endOffset)

            // Set the selection
            editor.selectionModel.setSelection(selectionStart, selectionEnd)

            // Perform action based on mode
            cursingSelectionService.handleMode(mode, selectionStart, selectionEnd, editor, project)
        }
        return CursingCommandService.OkayResponse
    }
}
