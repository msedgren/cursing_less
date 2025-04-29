package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingSelectionService

data object CursingSelectCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_select"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 4) {
            val mode = commandParameters[0]
            val cursingSelectionService = getApplication().getService(CursingSelectionService::class.java)
            val consumedData = cursingSelectionService.find(commandParameters.drop(1), editor)

            if (consumedData != null) {
                withContext(Dispatchers.EDT) {
                    cursingSelectionService
                        .handleMode(mode, consumedData.startOffset, consumedData.endOffset, editor, project)
                }
                return CursingCommandService.OkayResponse
            }
        }
        return CursingCommandService.BadResponse
    }
}
