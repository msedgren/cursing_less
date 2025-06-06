package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingColorShapeLookupService
import org.cursing_less.service.CursingCommandService
import org.cursing_less.command.TokenPosition

@Suppress("unused")
data object CursingRelativeMoveByColorCommand : VoiceCommand {
    private val cursingColorShapeLookupService: CursingColorShapeLookupService by lazy {
        getApplication().getService(CursingColorShapeLookupService::class.java)
    }

    override fun matches(command: String) = command == "curse_to_relative_location_by_color"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 3) {
            val next = commandParameters[0] == "next"
            val isStart = TokenPosition.isStart(commandParameters[1])
            val color = cursingColorShapeLookupService.parseColor(commandParameters[2])

            if (color != null) {
                return withContext(Dispatchers.EDT) {
                    val consumedData = cursingColorShapeLookupService.findConsumed(color, next, editor)
                    if (consumedData != null) {
                        val offset = if (isStart) consumedData.startOffset else consumedData.endOffset
                        editor.caretModel.moveToOffset(offset)
                        CursingCommandService.OkayResponse
                    } else {
                        CursingCommandService.BadResponse
                    }

                }
            }
        }
        return CursingCommandService.BadResponse
    }
}
