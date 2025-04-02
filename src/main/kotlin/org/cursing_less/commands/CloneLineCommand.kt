package org.cursing_less.commands

import com.intellij.openapi.application.*
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.cursing_less.services.CursingCommandService

data object CloneLineCommand : VoiceCommand {

    override fun matches(command: String) = command == "clone"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        if (editor != null) {
            readAndWriteAction {
                val document = editor.document
                val writable = document.isWritable
                val sourceLine = commandParameters[0].toInt()
                val startOffset = document.getLineStartOffset(sourceLine)
                val endOffset = document.getLineEndOffset(sourceLine)
                val text = document.getText(TextRange(startOffset, endOffset)).trim { it <= ' ' }

                val originalOffset = editor.caretModel.offset
                val cp = CommandProcessor.getInstance()
                writeAction {
                    document.setReadOnly(false)
                    try {
                        cp.executeCommand(
                            project,
                            { document.insertString(originalOffset, text) },
                            "Clone",
                            "cloneGroup"
                        )
                    } finally {
                        document.setReadOnly(!writable)
                    }
                }
            }
        }

        return CursingCommandService.OkayResponse
    }

}
