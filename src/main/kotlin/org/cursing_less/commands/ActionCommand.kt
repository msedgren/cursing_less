package org.cursing_less.commands

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.commands.ActionCommand
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.datatransfer.StringSelection

data object ActionCommand : VoiceCommand {

    override fun matches(command: String) = command == "action"


    override fun run(commandParameters: List<String>, project: Project, editor: Editor?): String {
        val actionId = commandParameters[0]
        val selectionModel = editor?.selectionModel

        // If we are attempting to perform an editor copy and we have no selection then we do nothing
        if (runAction(actionId, selectionModel)) {
            executeAction(project, actionId)
        }
        return "OK"
    }

    private fun runAction(actionId: String, selectionModel: SelectionModel?) =
        actionId != "EditorCopy" || selectionModel == null || selectionModel.hasSelection()

    private fun clearClipboard() {
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val transferable = StringSelection("")
        clipboard.setContents(transferable, transferable)
    }

    private fun executeAction(project: Project, actionId: String) {
        val action = ActionManager.getInstance().getAction(actionId)
        val event = ActionCommand.getInputEvent(actionId)
        if (action != null && event != null) {
            val toolWindow = pullToolWindow(project)
            val component = toolWindow?.component
            ActionManager.getInstance().tryToExecute(action, event, component, null, true)
        }
    }

    private fun pullToolWindow(project: Project): ToolWindow? {
        //TODO toolwindowmanager lock.
        val twm = ToolWindowManager.getInstance(project)
        val tw = twm.getToolWindow(twm.activeToolWindowId)
        return tw
    }

}
