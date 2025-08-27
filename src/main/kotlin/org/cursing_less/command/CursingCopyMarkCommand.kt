package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingMarkStorageService
import java.awt.datatransfer.StringSelection

/**
 * Command to copy the content from a given numeric mark to the system clipboard.
 * If a mark number is not provided, it defaults to 1.
 */
data object CursingCopyMarkCommand : VoiceCommand {

    private val markStorageService: CursingMarkStorageService by lazy {
        ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
    }

    override fun matches(command: String) = command == "curse_copy_mark"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        val givenMark = pullMark(commandParameters)
        val markNumber = givenMark ?: 1
        val markedTextInfo = markStorageService.getMarkedTextInfo(markNumber)
        if (markedTextInfo == null) {
            return CursingCommandService.BadResponse
        } else {
            withContext(Dispatchers.EDT) {
                CopyPasteManager.getInstance().setContents(StringSelection(markedTextInfo.text))
            }
            return CursingCommandService.OkayResponse
        }
    }

    private fun pullMark(commandParameters: List<String>): Int? {
        return commandParameters.firstOrNull()?.toIntOrNull()
    }
}
