package org.cursing_less.commands

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.commands.ActionCommand
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.datatransfer.StringSelection

data object ActionCommand : VoiceCommand {

    override fun matches(command: String) = command == "action"


    override fun run(commandParameters: List<String>, project: Project, editor: Editor): String {
        val actionId = commandParameters[0]
        val selectionModel = editor.selectionModel

        // If we are attempting to perform an editor copy and we have no selection then we should make
        // sure to clear the clipboard instead. This is to prevent a bug that causes intellij to do interesting
        // things when we try to perform an editor copy without anything selected
        if (actionId == "EditorCopy" && !selectionModel.hasSelection()) {
                clearClipboard()
                return "OK"
        }

        ApplicationManager.getApplication().invokeAndWait {
            executeAction(project, actionId)
        }
        return "OK"
    }

    private fun clearClipboard() {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val transferable = StringSelection("")
        clipboard.setContents(transferable, transferable)
    }

    private fun executeAction(project: Project, actionId: String) {
        val action = ActionManager.getInstance().getAction(actionId)
        val event = ActionCommand.getInputEvent(actionId)
        val toolWindow = pullToolWindow(project)
        val component = toolWindow?.component
        ActionManager.getInstance().tryToExecute(action, event, component, null, true)
    }

    private fun pullToolWindow(project: Project): ToolWindow? {
        val twm = ToolWindowManager.getInstance(project)
        val tw = twm.getToolWindow(twm.activeToolWindowId)
        if (tw == null) {
            thisLogger().debug("No selected tool window")
        }
        return tw
    }

}
