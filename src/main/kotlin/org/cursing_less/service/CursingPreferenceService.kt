package org.cursing_less.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingShape
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class CursingPreferenceService {

    // Get the settings component instance
    private val settingsComponent = ApplicationManager.getApplication()
        .getService(CursingPreferencePersistenceService::class.java)

    // Get colors from settings
    val colors: List<CursingColor>
        get() = settingsComponent.state.generateCursingColors()

    // Get shapes from settings
    val shapes: List<CursingShape>
        get() = settingsComponent.state.generateCursingShapes()

    // Get scale from settings
    val scale: Double
        get() = settingsComponent.state.scale

    // Get token pattern from settings
    val tokenPattern: Regex
        get() = Regex(settingsComponent.state.tokenPattern)

    // Get usePsiTree flag from settings
    val usePsiTree: Boolean
        get() = settingsComponent.state.usePsiTree

    // Get useRegex flag from settings
    val useRegex: Boolean
        get() = settingsComponent.state.useRegex

    private val echoCommandsAtomic = AtomicBoolean(false)

    // Get echo commands flag from settings
    val echoCommands: Boolean
        get() = echoCommandsAtomic.get()

    fun toggleEchoCommands() {
        echoCommandsAtomic.set(!echoCommandsAtomic.get())
    }
}
