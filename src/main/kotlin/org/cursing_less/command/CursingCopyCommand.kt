package org.cursing_less.command

import com.intellij.openapi.application.*
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.http.HttpStatus
import org.cursing_less.service.CursingColorShapeLookupService
import org.cursing_less.service.CursingCommandService

data object CursingCopyCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_copy"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null && commandParameters.size == 4) {
            val cursingColorShapeLookupService = ApplicationManager.getApplication()
                .getService(CursingColorShapeLookupService::class.java)
            val cut = commandParameters[0] == "cut"
            val colorShape =
                cursingColorShapeLookupService.parseToColorShape(commandParameters[1], commandParameters[2])
            val character = commandParameters[3].firstOrNull()
            if (colorShape != null && character != null) {
                val consumedData = cursingColorShapeLookupService.findConsumed(colorShape, character, editor)
                withContext(Dispatchers.EDT) {
                    if (consumedData != null) {
                        editor.caretModel.moveToOffset(consumedData.endOffset)
                        editor.selectionModel.setSelection(consumedData.startOffset, consumedData.endOffset)
                        editor.selectionModel.copySelectionToClipboard()
                    }
                }
                if (consumedData != null && cut) {
                    readAndWriteAction {
                        val document = editor.document
                        val writable = document.isWritable
                        val cp = CommandProcessor.getInstance()

                        writeAction {
                            document.setReadOnly(false)
                            try {
                                cp.executeCommand(
                                    project,
                                    { document.deleteString(consumedData.startOffset, consumedData.endOffset) },
                                    "Cut",
                                    "cutGroup"
                                )
                            } finally {
                                document.setReadOnly(!writable)
                            }
                        }
                    }
                }
            }
            return CursingCommandService.OkayResponse
        }
        return CursingCommandService.BadResponse
    }
}