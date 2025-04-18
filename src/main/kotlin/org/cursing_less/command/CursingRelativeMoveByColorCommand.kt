package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingColorShapeLookupService
import org.cursing_less.service.CursingCommandService

data object  CursingRelativeMoveByColorCommand : VoiceCommand {
    override fun matches(command: String) = command == "curse_to_relative_location_by_color"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 3) {
            val cursingColorShapeLookupService = ApplicationManager.getApplication()
                .getService(CursingColorShapeLookupService::class.java)
            val next = commandParameters[0] == "next"
            val pre = commandParameters[1] == "pre"
            val color = cursingColorShapeLookupService.parseColor(commandParameters[2])

            if (color != null) {
                withContext(Dispatchers.EDT) {
                    val consumedData = cursingColorShapeLookupService.findConsumed(color, next, editor)
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