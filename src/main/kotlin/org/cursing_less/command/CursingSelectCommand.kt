package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingColorShapeLookupService

data object  CursingSelectCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_select"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 3) {
            val cursingColorShapeLookupService = ApplicationManager.getApplication()
                .getService(CursingColorShapeLookupService::class.java)
            val colorShape = cursingColorShapeLookupService.parseToColorShape(commandParameters[0], commandParameters[1])
            val character = commandParameters[2].firstOrNull()
            if (colorShape != null && character != null) {
                withContext(Dispatchers.EDT) {
                    val consumedData = cursingColorShapeLookupService.findConsumed(colorShape, character, editor)
                    if (consumedData != null) {
                        editor.caretModel.moveToOffset(consumedData.endOffset)
                        editor.selectionModel.setSelection(consumedData.startOffset, consumedData.endOffset)
                        CursingCommandService.OkayResponse
                    }
                }
            }
        }
        return CursingCommandService.BadResponse

    }
}