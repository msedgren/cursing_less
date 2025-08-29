package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingSelectionService

data object CursingMoveCommand : VoiceCommand {

    private val cursingSelectionService: CursingSelectionService by lazy {
        getApplication().getService(CursingSelectionService::class.java)
    }

    override fun matches(command: String) = command == "curse_to_location"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 4) {
            val isStart = TokenPosition.isStart(commandParameters[0])
            val consumedData = cursingSelectionService.find(commandParameters.drop(1), editor)
            if (consumedData != null) {
                withContext(Dispatchers.EDT) {
                    val offset = if (isStart) consumedData.startOffset else consumedData.endOffset
                    editor.caretModel.moveToOffset(offset)
                    editor.selectionModel.removeSelection()
                }
                return CursingCommandService.OkayResponse
            }
        }
        return CursingCommandService.BadResponse
    }

}
