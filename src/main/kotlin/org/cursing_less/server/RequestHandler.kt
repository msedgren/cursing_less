package org.cursing_less.server

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFocusManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.cursing_less.listeners.CursingApplicationListener
import org.cursing_less.services.VoiceCommandParserService
import java.io.InputStreamReader
import java.net.URLDecoder

class RequestHandler : HttpHandler {


    override fun handle(httpExchange: HttpExchange) {
        thisLogger().debug("Handling ${httpExchange.requestMethod} ${httpExchange.requestURI}")
        if (CursingApplicationListener.handler.initialized) {
            ApplicationManager.getApplication().invokeLaterOnWriteThread {
                try {
                    val inputStream = httpExchange.requestBody
                    InputStreamReader(inputStream).readText()
                    val (project, editor) = pullProjectAndEditor()
                    val response = processCommand(
                        URLDecoder.decode(
                            httpExchange.requestURI.toString().substring(1),
                            "UTF-8"
                        ), project, editor
                    )

                    httpExchange.sendResponseHeaders(response.responseCode, response.response.length.toLong())
                    val os = httpExchange.responseBody
                    os.write(response.response.toByteArray())
                    os.close()
                } catch (e: Exception) {
                    thisLogger().error("Failed to process command... ", e)
                    val response = e.toString()
                    httpExchange.sendResponseHeaders(500, response.length.toLong())
                    val os = httpExchange.responseBody
                    os.write(response.toByteArray())
                    os.close()
                }
            }
        }
    }

    private fun processCommand(command: String, project: Project, editor: Editor?): VoicePluginResponse {
        val voiceCommandParserService =
            ApplicationManager.getApplication().getService(VoiceCommandParserService::class.java)
        val split = command.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val commandInformation = split[1].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val voiceCommand = voiceCommandParserService.fromRequestUri(commandInformation[0])
        val commandParams = commandInformation.toList().subList(1, commandInformation.size)

        thisLogger().debug("Running command using $voiceCommand with params $commandParams")

        val response = voiceCommand
            ?.run(commandParams, project, editor)
            ?.let { resp -> VoicePluginResponse(200, resp) }
            ?: VoicePluginResponse(502, "BAD")

        thisLogger().debug("Response: $response")

        return response
    }

    private fun pullProjectAndEditor(): Pair<Project, Editor?> {
        val project = IdeFocusManager.findInstance().lastFocusedFrame?.project
            ?: ProjectManager.getInstance().openProjects.firstOrNull() ?: ProjectManager.getInstance().defaultProject
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        return Pair(project, editor)
    }

    data class VoicePluginResponse(val responseCode: Int, val response: String)

}