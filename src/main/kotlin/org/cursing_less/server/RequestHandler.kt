package org.cursing_less.server

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.cursing_less.commands.ActionCommand
import org.cursing_less.services.VoiceCommandParserService
import java.io.InputStreamReader
import java.net.URLDecoder

class RequestHandler : HttpHandler {

    override fun handle(httpExchange: HttpExchange) {
        try {
            thisLogger().debug("Handling ${httpExchange.requestMethod} ${httpExchange.requestURI}")
            val inputStream = httpExchange.requestBody
            InputStreamReader(inputStream).readText()
            val (project, editor) = pullProjectAndEditor()
            if (project != null && editor != null) {
                val response = processCommand(URLDecoder.decode(httpExchange.requestURI.toString().substring(1), "UTF-8"), project, editor)

                httpExchange.sendResponseHeaders(response.responseCode, response.response.length.toLong())
                val os = httpExchange.responseBody
                os.write(response.response.toByteArray())
                os.close()
            } else {
                thisLogger().warn("Given command but could not find a project or editor")
            }
        } catch (e: Exception) {
            thisLogger().error("Failed to process command... ", e)
            val response = e.toString()
            httpExchange.sendResponseHeaders(500, response.length.toLong())
            val os = httpExchange.responseBody
            os.write(response.toByteArray())
            os.close()
        }
    }

    private fun processCommand(command: String, project: Project, editor: Editor): VoicePluginResponse {
        val voiceCommandParserService = project.getService(VoiceCommandParserService::class.java)
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

    private fun pullProjectAndEditor(): Pair<Project?, Editor?> {
        val project = IdeFocusManager.findInstance().lastFocusedFrame?.project
        val editor = if (project != null) FileEditorManager.getInstance(project).selectedTextEditor else null
        return Pair(project, editor)
    }

    data class VoicePluginResponse(val responseCode: Int, val response: String)

}