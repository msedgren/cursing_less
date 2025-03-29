package org.cursing_less.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.services.CommandService
import org.cursing_less.services.CursingLookupService

class CursingSelectCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_select"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size >= 3) {
            val cursingLookupService = ApplicationManager.getApplication()
                .getService(CursingLookupService::class.java)
            val colorShape = cursingLookupService.lookup(commandParameters[0], commandParameters[1])
            val character = commandParameters[2].firstOrNull()
            if (colorShape != null && character != null) {
                withContext(Dispatchers.EDT) {
                    val consumedData = cursingLookupService.lookup(colorShape, character, editor)
                    if (consumedData != null) {
                        editor.selectionModel.setSelection(consumedData.startOffset, consumedData.endOffset)
                        editor.selectionModel.copySelectionToClipboard()
                        CommandService.OkayResponse
                    }
                }
            }
        }
        return CommandService.BadResponse

    }
}