package org.cursing_less.commands

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.commands.ActionCommand
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager

data object ActionCommand : VoiceCommand {

    override fun matches(command: String) = command == "action"


    override fun run(commandParameters: List<String>, project: Project, editor: Editor): String {
        val actionId = commandParameters[0]
        ApplicationManager.getApplication().invokeAndWait {
            val action = ActionManager.getInstance().getAction(actionId)
            val event = ActionCommand.getInputEvent(actionId)
            val toolWindow = pullToolWindow(project)
            val component = toolWindow?.component
            ActionManager.getInstance().tryToExecute(action, event, component, null, true)
        }
        return "OK"
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
