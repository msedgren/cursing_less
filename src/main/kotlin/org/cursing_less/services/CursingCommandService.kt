package org.cursing_less.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.remoteDev.util.getPathSelectorPrefixByProductCode
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.http.HttpStatus
import org.cursing_less.MyBundle
import org.cursing_less.commands.VoiceCommandResponse
import org.cursing_less.listeners.CursingApplicationListener
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class CursingCommandService(private val coroutineScope: CoroutineScope) : Disposable {

    private var pathToNonce: Path? = null
    private var server: HttpServer? = null
    private var initialized = AtomicBoolean(false)

    companion object {
        val OkayResponse = VoiceCommandResponse(HttpStatus.SC_OK, "OK")
        val BadResponse = VoiceCommandResponse(HttpStatus.SC_BAD_REQUEST, "ERROR")


        const val DEFAULT_PORT: Int = 8652

        // See com.intellij.openapi.util.BuildNumber.PRODUCT_CODES_TO_PREFIXES
        // TODO this was altered from the original plugin to remove warnings about using JetBrains internal
        //      only libraries. This should work but additional products should be defined like Rust Rover...
        private val PLATFORM_TO_PORT: Map<String, Int> = mapOf(
            "IntelliJIdea" to 8653, // IntelliJ IDEA Ultimate
            "IdeaIC" to 8654, // IntelliJ IDEA Community
            "AppCode" to 8655, //AppCode
            "CLion" to 8657, // CLion
            "PyCharm" to 8658, //PyCharm
            "PyCharmCE" to 8658, //PyCharm Community
            "PyCharmEdu" to 8658, // PyCharm Edu
            "RubyMine" to 8661, //RubyMine
            "PhpStorm" to 8662, //PhpStorm
            "WebStorm" to 8663, // WebStorm
            "DataGrip" to 8664, // DataGrip
            "Rider" to 8660, // Rider
            "GoLand" to 8659, // GoLand
        )
    }


    override fun dispose() {
        shutdown()
    }

    fun getPortForCurrentIde(): Int {
        val buildNumber = ApplicationInfo.getInstance().build
        val prefix = getPathSelectorPrefixByProductCode(buildNumber.productCode) ?: ""
        return PLATFORM_TO_PORT[prefix] ?: DEFAULT_PORT
    }

    fun startup(): Boolean {
        try {
            if (!initialized.get()) {
                val random = SecureRandom()
                val bytes = ByteArray(20)
                random.nextBytes(bytes)
                val nonce = String(Base64.getUrlEncoder().encode(bytes))
                val port: Int = getPortForCurrentIde()
                try {
                    val noncePath =
                        FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"), "vcidea_$port")
                    pathToNonce = noncePath
                    Files.write(noncePath, nonce.toByteArray())
                } catch (e: IOException) {
                    thisLogger().error("Failed to write nonce file", e)
                }

                // https://stackoverflow.com/questions/3732109/simple-http-server-in-java-using-only-java-se-api#3732328
                val loopbackSocket = InetSocketAddress(InetAddress.getLoopbackAddress(), port)
                server = HttpServer.create(loopbackSocket, -1)
                server?.createContext("/$nonce", RequestHandler())
                server?.executor = null
                server?.start()
                initialized.set(true)
            }
        } catch (e: Exception) {
            notifyStartupFailure(e)
            thisLogger().error("Failed to start server to listen for commands", e)
        }
        return initialized.get()
    }

    fun shutdown() {
        try {
            try {
                val nonce = pathToNonce
                if (nonce != null) {
                    Files.delete(nonce)
                }
            } catch (e: IOException) {
                thisLogger().error("Failed to cleanup nonce file", e)
            }

            if (initialized.get()) {
                server?.stop(1)
                thisLogger().info("Completed cleanup of plugin")
            }
        } finally {
            pathToNonce = null
            server = null
            initialized.set(false)
        }
    }

    private fun notifyStartupFailure(exception: Exception) {
        val note = NotificationGroupManager.getInstance()
            .getNotificationGroup("cursing_less")
            .createNotification(
                MyBundle.message("cursing_less.error", exception.message ?: ""),
                NotificationType.ERROR
            )
        Notifications.Bus.notify(note)
    }


    fun handleCommandRequest(httpExchange: HttpExchange) {
        coroutineScope.launch {
            echoAndLog(httpExchange)

            if (CursingApplicationListener.handler.initialized.get()) {
                try {
                    val inputStream = httpExchange.requestBody
                    InputStreamReader(inputStream).readText()
                    val (project, editor) = withContext(Dispatchers.EDT) {
                        pullProjectAndEditor()
                    }
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
        val editor = ApplicationManager.getApplication().getService(CursingColorShapeLookupService::class.java)
            .getFocusedEditor()
        val project = editor?.project ?: ProjectManager.getInstance().defaultProject
        return Pair(project, editor)
    }


    class RequestHandler : HttpHandler {

        override fun handle(httpExchange: HttpExchange) {
            val cursingCommandService =
                ApplicationManager.getApplication().getService(CursingCommandService::class.java)
            cursingCommandService.handleCommandRequest(httpExchange)
        }

    }
}