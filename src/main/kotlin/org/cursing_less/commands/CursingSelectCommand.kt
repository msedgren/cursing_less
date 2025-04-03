package org.cursing_less.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.services.CursingCommandService
import org.cursing_less.services.CursingColorShapeLookupService

class CursingSelectCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_select"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size >= 3) {
            val cursingColorShapeLookupService = ApplicationManager.getApplication()
                .getService(CursingColorShapeLookupService::class.java)
            val colorShape = cursingColorShapeLookupService.parseToColorShape(commandParameters[0], commandParameters[1])
            val character = commandParameters[2].firstOrNull()
            if (colorShape != null && character != null) {
                withContext(Dispatchers.EDT) {
                    val consumedData = cursingColorShapeLookupService.parseToColorShape(colorShape, character, editor)
                    if (consumedData != null) {
                        editor.selectionModel.setSelection(consumedData.startOffset, consumedData.endOffset)
                        editor.selectionModel.copySelectionToClipboard()
                        CursingCommandService.OkayResponse
                    }
                }
            }
        }
        return CursingCommandService.BadResponse

    }
}