package org.cursing_less.command

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import org.cursing_less.service.CursingCommandService

/**
 * Command to toggle the visibility of the Cursing Marks tool window.
 * This command shows or hides the tool window depending on its current state.
 */
data object CursingToggleMarksToolWindowCommand : VoiceCommand {

    override fun matches(command: String) = command == "curse_toggle_marks_window"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        // Get the tool window manager for the project
        val toolWindowManager = ToolWindowManager.getInstance(project)
        
        // Get the Cursing Marks tool window
        val toolWindow = toolWindowManager.getToolWindow("Cursing Marks")
        
        if (toolWindow != null) {
            // Toggle the tool window visibility
            if (toolWindow.isVisible) {
                toolWindow.hide()
            } else {
                toolWindow.show()
            }
            
            return CursingCommandService.OkayResponse
        }
        
        return CursingCommandService.BadResponse
    }
}