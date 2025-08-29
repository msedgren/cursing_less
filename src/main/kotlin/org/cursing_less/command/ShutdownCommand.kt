package org.cursing_less.command

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.flow.debounceBatch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.launch
import org.cursing_less.service.CursingCommandService
import org.cursing_less.service.CursingScopeService
import kotlin.time.Duration.Companion.milliseconds

/**
 * Command to shut down the CursingCommandService.
 * This will stop the HTTP server and clean up resources.
 */
@Suppress("unused")
data object ShutdownCommand : VoiceCommand {

    private val cursingCommandService: CursingCommandService by lazy {
        getApplication().getService(CursingCommandService::class.java)
    }
    private val cursingScopeService: CursingScopeService by lazy {
        getApplication().getService(CursingScopeService::class.java)
    }

    private val flow =
        MutableSharedFlow<Unit>(
            0,
            1,
            BufferOverflow.DROP_OLDEST
        )

    init {
        cursingScopeService.coroutineScope.launch {
            var debouncedFlow = flow.debounceBatch(100.milliseconds)
            debouncedFlow
                .cancellable()
                .collect {
                    cursingCommandService.shutdown()
                }
        }
    }

    override fun matches(command: String) = command == "shutdown"

    override suspend fun run(commandParameters: List<String>, project: Project, editor: Editor?): VoiceCommandResponse {
        flow.emit(Unit)
        return CursingCommandService.OkayResponse
    }
}
