package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingColorShapeLookupService

class CursingMoveCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_to_location"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 4) {
            val cursingColorShapeLookupService = ApplicationManager.getApplication()
                .getService(CursingColorShapeLookupService::class.java)
            val pre = commandParameters[0] == "pre"
            val colorShape = cursingColorShapeLookupService.parseToColorShape(commandParameters[1], commandParameters[2])
            val character = commandParameters[3].firstOrNull()
            if (colorShape != null && character != null) {
                withContext(Dispatchers.EDT) {
                    val consumedData = cursingColorShapeLookupService.parseToColorShape(colorShape, character, editor)
                    if (consumedData != null) {
                        val offset = if (pre) consumedData.startOffset else consumedData.endOffset
                        editor.caretModel.moveToOffset(offset)
                        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                    }
                }
            }
        }
        return CursingCommandService.OkayResponse
    }
}
