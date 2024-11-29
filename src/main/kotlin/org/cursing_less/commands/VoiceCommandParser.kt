package org.cursing_less.commands

import com.intellij.openapi.components.Service
import kotlin.reflect.full.createInstance

@Service(Service.Level.PROJECT)
class VoiceCommandParser {

    private val knownCommands: List<VoiceCommand> = VoiceCommand::class.sealedSubclasses
        .map { it.createInstance() }

    fun fromRequestUri(command: String): VoiceCommand? {
        return knownCommands
            .filter { it.matches(command) }
            .firstOrNull()
    }
}