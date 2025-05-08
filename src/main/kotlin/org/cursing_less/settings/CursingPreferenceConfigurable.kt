package org.cursing_less.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.Configurable
import kotlinx.coroutines.launch
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingPreferencePersistenceService
import org.cursing_less.service.CursingScopeService
import javax.swing.JComponent

/**
 * Configurable implementation for Cursing Less plugin settings.
 * This class provides the UI for the settings and handles the interaction with the settings component.
 */
class CursingPreferenceConfigurable : Configurable {

    private var mySettingsComponent: CursingPreferenceSettingsPanel? = null

    // Get the settings component instance
    private val settings =ApplicationManager.getApplication().getService(CursingPreferencePersistenceService::class.java)
    private val markupService = ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
    private val scopeService = ApplicationManager.getApplication().getService(CursingScopeService::class.java)

    override fun getDisplayName(): String {
        return "Cursing Less"
    }

    override fun createComponent(): JComponent {
        mySettingsComponent = CursingPreferenceSettingsPanel(settings.state)
        return mySettingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        // Check if any settings have been modified
        return mySettingsComponent?.isModified(settings.state) ?: false
    }

    override fun apply() {
        // Apply changes from the UI to the settings
        settings.update(mySettingsComponent?.generateUpdatedState() ?: CursingPreferenceState())
        scopeService.coroutineScope.launch {
            markupService.fullyRefreshAllTokens()
        }
    }

    override fun reset() {
        // Reset UI to match current settings
        mySettingsComponent?.load(settings.state)
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}