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
        // Determine mark number, defaulting to 1 if not provided
        val markNumber = pullMark(commandParameters) ?: return CursingCommandService.BadResponse

        // Retrieve marked text
        val markedText = markStorageService.getMarkedText(markNumber)
        if (markedText.isNullOrEmpty()) {
            return CursingCommandService.BadResponse
        }

        // Copy to clipboard on EDT
        return withContext(Dispatchers.EDT) {
            CopyPasteManager.getInstance().setContents(StringSelection(markedText))
            CursingCommandService.OkayResponse
        }
    }

    private fun pullMark(commandParameters: List<String>): Int? {
        if (commandParameters.isEmpty()) return 1
        val markNumber = commandParameters[0].toIntOrNull() ?: return null
        if (markNumber < 1) return null
        return markNumber
    }
}
