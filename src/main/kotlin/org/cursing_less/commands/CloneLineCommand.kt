package org.cursing_less.commands

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange

class CloneLineCommand : VoiceCommand {

    override fun matches(command: String) = command == "clone"

    override fun run(commandParameters: List<String>, project: Project, editor: Editor): String {
        val application = ApplicationManager.getApplication()
        application.invokeAndWait {
            val document = editor.document
            val writable = document.isWritable
            document.setReadOnly(false)
            try {
                val sourceLine = commandParameters[0].toInt()
                val startOffset = document.getLineStartOffset(sourceLine)
                val endOffset = document.getLineEndOffset(sourceLine)
                val text = document.getText(TextRange(startOffset, endOffset)).trim { it <= ' ' }
                application.runWriteAction {
                    val originalOffset = editor.caretModel.offset
                    val cp = CommandProcessor.getInstance()
                    cp.executeCommand(project, { document.insertString(originalOffset, text) }, "Clone", "cloneGroup")
                }
            } finally {
                document.setReadOnly(!writable)
            }
        }
        return "OK"
    }

}
