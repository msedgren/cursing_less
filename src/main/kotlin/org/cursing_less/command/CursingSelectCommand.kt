package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingColorShapeLookupService
import org.cursing_less.service.CursingSelectionService

data object CursingSelectCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_select"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.isNotEmpty()) {
            val mode = commandParameters[0]
            val remainingParams = commandParameters.drop(1)

            if (remainingParams.size == 3) {
                val cursingColorShapeLookupService = ApplicationManager.getApplication()
                    .getService(CursingColorShapeLookupService::class.java)
                val cursingSelectionService = ApplicationManager.getApplication()
                    .getService(CursingSelectionService::class.java)

                val colorShape = cursingColorShapeLookupService.parseToColorShape(remainingParams[0], remainingParams[1])
                val character = remainingParams[2].firstOrNull()

                if (colorShape != null && character != null) {
                    val consumedData = cursingSelectionService.find(colorShape, character, editor)

                    if (consumedData != null) {
                        when (mode) {
                            "select" -> {
                                cursingSelectionService.select(consumedData.startOffset, consumedData.endOffset, editor)
                            }
                            "copy" -> {
                                cursingSelectionService.select(consumedData.startOffset, consumedData.endOffset, editor)
                                cursingSelectionService.copySelectionToClipboard(editor)
                            }
                            "cut" -> {
                                cursingSelectionService.select(consumedData.startOffset, consumedData.endOffset, editor)
                                cursingSelectionService.copySelectionToClipboard(editor)
                                cursingSelectionService.deleteText(
                                    consumedData.startOffset,
                                    consumedData.endOffset,
                                    editor,
                                    project
                                )
                            }
                            "clear" -> {
                                // Then delete the text directly without selecting it
                                cursingSelectionService.deleteText(
                                    consumedData.startOffset,
                                    consumedData.endOffset,
                                    editor,
                                    project
                                )
                            }
                        }
                        return CursingCommandService.OkayResponse
                    }
                }
            }
        }
        return CursingCommandService.BadResponse
    }
}
