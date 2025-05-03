package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.apache.http.HttpStatus
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingSelectionService

data object CursingOffsetCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_offset"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 3) {
            val cursingSelectionService = getApplication().getService(CursingSelectionService::class.java)

            val consumedData = cursingSelectionService.find(commandParameters, editor)

            if (consumedData != null) {
                // Return a response with the start and end offsets
                return VoiceCommandResponse(
                    HttpStatus.SC_OK,
                    "(${consumedData.startOffset},${consumedData.endOffset})"
                )
            }
        }
        return CursingCommandService.BadResponse
    }
}
