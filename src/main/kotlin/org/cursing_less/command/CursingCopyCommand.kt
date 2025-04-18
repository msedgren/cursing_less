package org.cursing_less.command

import com.intellij.openapi.application.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.cursing_less.service.CursingColorShapeLookupService
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingSelectionService

data object CursingCopyCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_copy"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 4) {
            val cursingColorShapeLookupService = ApplicationManager.getApplication()
                .getService(CursingColorShapeLookupService::class.java)
            val cursingSelectionService = ApplicationManager.getApplication()
                .getService(CursingSelectionService::class.java)

            val cut = commandParameters[0] == "cut"
            val colorShape =
                cursingColorShapeLookupService.parseToColorShape(commandParameters[1], commandParameters[2])
            val character = commandParameters[3].firstOrNull()

            if (colorShape != null && character != null) {
                val consumedData = cursingSelectionService.findAndSelect(colorShape, character, editor)

                if (consumedData != null) {
                    cursingSelectionService.copySelectionToClipboard(editor)

                    if (cut) {
                        cursingSelectionService.cutSelectedText(consumedData, editor, project)
                    }
                    return CursingCommandService.OkayResponse
                }
            }
        }
        return CursingCommandService.BadResponse
    }
}
