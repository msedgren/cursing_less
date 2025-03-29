package org.cursing_less.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFocusManager
import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.apache.http.HttpStatus
import org.cursing_less.commands.VoiceCommandResponse
import org.cursing_less.listeners.CursingApplicationListener
import java.io.InputStreamReader
import java.net.URLDecoder

@Service(Service.Level.APP)
class CommandService(private val coroutineScope: CoroutineScope) {

    companion object {
        val OkayResponse = VoiceCommandResponse(HttpStatus.SC_OK, "OK")
        val BadResponse = VoiceCommandResponse(HttpStatus.SC_BAD_REQUEST, "ERROR")
    }

    fun handleCommandRequest(httpExchange: HttpExchange) {
        coroutineScope.launch {
            echoAndLog(httpExchange)

            if (CursingApplicationListener.handler.initialized.get()) {
                try {
                    val inputStream = httpExchange.requestBody
                    InputStreamReader(inputStream).readText()
                    val (project, editor) = pullProjectAndEditor()
                    val response = processCommand(
                        URLDecoder.decode(
                            httpExchange.requestURI.toString().substring(1),
                            "UTF-8"
                        ),
                        project,
                        editor
                        )
                    completeResponse(response.responseCode, response.response, httpExchange)
                } catch (e: Exception) {
                    thisLogger().error("Failed to process command... ", e)
                    completeResponse(500, e.toString(), httpExchange)
                }
            }
        }
    }

    private fun echoAndLog(httpExchange: HttpExchange) {
        thisLogger().debug("Handling ${httpExchange.requestMethod} ${httpExchange.requestURI}")

        val preferenceService = ApplicationManager.getApplication().getService(CursingPreferenceService::class.java)
        if (preferenceService.echoCommands) {
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("cursing_less")
                .createNotification(
                    "Echo",
                    "Processing request: ${httpExchange.requestMethod} ${httpExchange.requestURI}",
                    NotificationType.INFORMATION
                )
            Notifications.Bus.notify(notification)
        }
    }

    private fun completeResponse(code: Int, response: String, httpExchange: HttpExchange) {
        httpExchange.sendResponseHeaders(code, response.length.toLong())
        val os = httpExchange.responseBody
        os.write(response.toByteArray())
        os.close()
    }

    private suspend fun processCommand(command: String, project: Project, editor: Editor?): VoiceCommandResponse {
        val voiceCommandParserService =
            ApplicationManager.getApplication().getService(VoiceCommandParserService::class.java)
        val split = command.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val commandInformation = split[1].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val voiceCommand = voiceCommandParserService.fromRequestUri(commandInformation[0])
        val commandParams = commandInformation.toList().subList(1, commandInformation.size)

        thisLogger().debug("Running command using $voiceCommand with params $commandParams")

        val response = voiceCommand
            ?.run(commandParams, project, editor)
            ?: VoiceCommandResponse(502, "BAD")

        thisLogger().debug("Response: $response")

        return response
    }

    private fun pullProjectAndEditor(): Pair<Project, Editor?> {
        val project = IdeFocusManager.findInstance().lastFocusedFrame?.project
            ?: ProjectManager.getInstance().openProjects.firstOrNull() ?: ProjectManager.getInstance().defaultProject
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        return Pair(project, editor)
    }
}