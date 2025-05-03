package org.cursing_less.command

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.commands.ActionCommand
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingCommandService

@Suppress("unused")
data object ActionCommand : VoiceCommand {

    override fun matches(command: String) = command == "action"


    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        val actionId = commandParameters[0]
        val execute = actionShouldExecute(actionId, editor?.selectionModel)

        // If we are attempting to perform an editor copy and we have no selection then we do nothing
        if (execute) {
            executeAction(project, actionId)
        }
        return CursingCommandService.OkayResponse
    }

    private suspend fun actionShouldExecute(actionId: String, selectionModel: SelectionModel?): Boolean {
     return readAction {
            actionId != "EditorCopy" || selectionModel == null || selectionModel.hasSelection()
        }
    }

    private suspend fun executeAction(project: Project, actionId: String) {
        val action = ActionManager.getInstance().getAction(actionId)
        val event = ActionCommand.getInputEvent(actionId)
        if (action != null && event != null) {
            val twm = ToolWindowManager.getInstance(project)
            val toolWindow = pullToolWindow(twm)
            val component = toolWindow?.component
            withContext(Dispatchers.EDT) {
                ActionManager.getInstance().tryToExecute(action, event, component, null, true)
            }
        }
    }

    private fun pullToolWindow(twm: ToolWindowManager): ToolWindow? {
        return twm.getToolWindow(twm.activeToolWindowId)
    }

}
