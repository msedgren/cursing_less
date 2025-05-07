package org.cursing_less.service

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import org.cursing_less.settings.CursingPreferenceState

/**
 * This class is responsible for loading and saving the settings to disk.
 */
@Service(Service.Level.APP)
@State(
    name = "org.cursing_less.service.CursingPreferencePersistenceService",
    storages = [Storage("CursingLessSettings.xml")]
)
class CursingPreferencePersistenceService : SerializablePersistentStateComponent<CursingPreferenceState>(defaultState) {

    companion object {
        val defaultState = CursingPreferenceState()
    }

    fun update(state: CursingPreferenceState) {
        updateState { state }
    }
}