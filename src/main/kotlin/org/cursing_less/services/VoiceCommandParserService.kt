package org.cursing_less.services

import com.intellij.openapi.components.Service
import org.cursing_less.commands.VoiceCommand
import kotlin.reflect.full.createInstance

@Service(Service.Level.APP)
class VoiceCommandParserService {

    private val knownCommands: List<VoiceCommand> = VoiceCommand::class.sealedSubclasses
        .map { it.objectInstance ?: it.createInstance() }

    fun fromRequestUri(command: String): VoiceCommand? {
        return knownCommands.firstOrNull { it.matches(command) }
    }
}