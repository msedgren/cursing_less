package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.cursing_less.service.CursingCommandService

/**
 * Command to shut down the CursingCommandService.
 * This will stop the HTTP server and clean up resources.
 */
@Suppress("unused")
data object ShutdownCommand : VoiceCommand {

    private val cursingCommandService: CursingCommandService by lazy {
        getApplication().getService(CursingCommandService::class.java)
    }

    private val updateQueue = MergingUpdateQueue("cursing_less_shutdown_delay", 100, true, null)

    override fun matches(command: String) = command == "shutdown"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        val task = object : Update("shutdown") {
            override fun run() {
                cursingCommandService.shutdown()
            }
        }
        updateQueue.queue(task)

        return CursingCommandService.OkayResponse
    }
}
