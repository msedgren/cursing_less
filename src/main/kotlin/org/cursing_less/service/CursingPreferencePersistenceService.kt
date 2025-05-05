package org.cursing_less.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.cursing_less.settings.CursingPreferenceState

/**
 * Persistent settings component for the Cursing Less plugin.
 * This class is responsible for loading and saving the settings to disk.
 */
@Service(Service.Level.APP)
@State(
    name = "org.cursing_less.settings.CursingPreferencePersistenceService",
    storages = [Storage("CursingLessSettings.xml")]
)
class CursingPreferencePersistenceService : PersistentStateComponent<CursingPreferenceState> {

    private var myState = CursingPreferenceState()

    override fun getState(): CursingPreferenceState {
        return myState
    }

    override fun loadState(state: CursingPreferenceState) {
        myState = state.copy()
    }
}