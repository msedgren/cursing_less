package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.apache.http.HttpStatus
import org.cursing_less.service.CursingColorShapeLookupService
import org.cursing_less.service.CursingCommandService

data object CursingOffsetCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_offset"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 3) {
            val cursingColorShapeLookupService = ApplicationManager.getApplication()
                .getService(CursingColorShapeLookupService::class.java)

            val colorShape =
                cursingColorShapeLookupService.parseToColorShape(commandParameters[0], commandParameters[1])
            val character = commandParameters[2].firstOrNull()

            if (colorShape != null && character != null) {
                val consumedData = cursingColorShapeLookupService.findConsumed(colorShape, character, editor)

                if (consumedData != null) {
                    // Return a response with the start and end offsets
                    return VoiceCommandResponse(
                        HttpStatus.SC_OK,
                        "(${consumedData.startOffset},${consumedData.endOffset})"
                    )
                }
            }
        }
        return CursingCommandService.BadResponse
    }
}
