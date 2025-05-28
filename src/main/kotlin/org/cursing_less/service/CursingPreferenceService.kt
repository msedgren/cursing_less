package org.cursing_less.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import org.cursing_less.color_shape.CursingColor
import org.cursing_less.color_shape.CursingShape
import org.cursing_less.settings.CursingPreferenceState
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class CursingPreferenceService {

    // Get the settings component instance
    private val settingsComponent by lazy {
        ApplicationManager.getApplication().getService(CursingPreferencePersistenceService::class.java)
    }

    private val testing = ApplicationManager.getApplication().isUnitTestMode

    // Get colors from settings
    val colors: List<CursingColor>
        get() = state.generateCursingColors()

    // Get shapes from settings
    val shapes: List<CursingShape>
        get() = state.generateCursingShapes()

    // Get scale from settings
    val scale: Double
        get() = state.scale

    // Get token pattern from settings
    val tokenPattern: Regex
        get() = Regex(state.tokenPattern)

    // Get usePsiTree flag from settings
    val usePsiTree: Boolean
        get() = state.usePsiTree

    // Get useRegex flag from settings
    val useRegex: Boolean
        get() = state.useRegex

    private val echoCommandsAtomic = AtomicBoolean(false)

    // Get echo commands flag from settings
    val echoCommands: Boolean
        get() = echoCommandsAtomic.get()

    fun toggleEchoCommands() {
        echoCommandsAtomic.set(!echoCommandsAtomic.get())
    }

    private val state: CursingPreferenceState
        get() = if (!testing) {
            settingsComponent.state
        } else {
            CursingPreferencePersistenceService.defaultState
        }
}
