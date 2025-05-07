package org.cursing_less.service

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
import com.intellij.util.io.HttpRequests
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.http.HttpStatus
import org.cursing_less.MyBundle
import org.cursing_less.command.VoiceCommandResponse
import org.cursing_less.listener.CursingApplicationListener
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
    private var nonce: String = ""

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

        /**
         * Generates a cryptographically secure random nonce.
         *
         * @return The generated nonce as a Base64 string
         */
        fun generateNonce(): String {
            val random = SecureRandom()
            val bytes = ByteArray(20)
            random.nextBytes(bytes)
            return String(Base64.getUrlEncoder().encode(bytes))
        }
    }

    /**
     * Writes the provided nonce to a file based on the port.
     *
     * @param nonce The nonce to write to the file
     * @param port The port number used to identify the nonce file
     * @return The path to the nonce file
     * @throws IOException If there is an error writing to the file
     */
    private fun writeNonceToFile(nonce: String, port: Int): Path {
        val noncePath = FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"), "vcidea_$port")
        Files.write(noncePath, nonce.toByteArray())
        return noncePath
    }

    /**
     * Reads the nonce from an existing file.
     *
     * @param port The port number used to identify the nonce file
     * @return The nonce read from the file, or null if the file doesn't exist or cannot be read
     */
    private fun readNonceFromFile(port: Int): String? {
        try {
            val noncePath = FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"), "vcidea_$port")
            if (Files.exists(noncePath)) {
                return String(Files.readAllBytes(noncePath))
            }
        } catch (e: IOException) {
            thisLogger().error("Failed to read nonce file", e)
        }
        return null
    }

    override fun dispose() {
        shutdown()
    }

    private fun getPortForCurrentIde(): Int {
        val buildNumber = ApplicationInfo.getInstance().build
        val prefix = getPathSelectorPrefixByProductCode(buildNumber.productCode) ?: ""
        return PLATFORM_TO_PORT[prefix] ?: DEFAULT_PORT
    }

    /**
     * Attempts to shut down an existing CursingCommandService instance if one is running.
     * First pings the service to check if it's available, then sends a shutdown command.
     *
     * @return true if an existing service was successfully shut down, false otherwise
     */
    private fun shutdownExistingService(): Boolean {
        try {
            val port = getPortForCurrentIde()
            val existingNonce = readNonceFromFile(port)

            if (existingNonce.isNullOrBlank()) {
                thisLogger().info("No existing nonce file found")
                return false
            }

            // First check if a service is running
            if (!pingExistingService(port, existingNonce)) {
                thisLogger().info("No running service found to shut down")
                return false
            }

            val shutdownUrl = "http://localhost:$port/$existingNonce/shutdown"
            val shutdownResponse = HttpRequests.request(shutdownUrl)
                .connectTimeout(2000)
                .readTimeout(2000)
                .readString()

            Thread.sleep(200)

            thisLogger().debug("Successfully sent shutdown command to existing service, response: $shutdownResponse")
            return true
        } catch (e: Exception) {
            thisLogger().debug("Error sending shutdown command to existing service", e)
            return false
        }
    }

    /**
     * Attempts to ping an existing CursingCommandService.
     *
     * @param port The port to connect to
     * @param nonce The nonce value for authentication
     * @return true if the service responded successfully, false otherwise
     */
    private fun pingExistingService(port: Int, nonce: String): Boolean {
        try {
            val url = "http://localhost:$port/$nonce/ping"
            thisLogger().debug("Attempting to ping existing service at: $url")

            val responseBody = HttpRequests.request(url)
                .connectTimeout(2000)
                .readTimeout(2000)
                .readString()

            thisLogger().debug("Ping response: $responseBody")
            return responseBody == "ping"
        } catch (e: IOException) {
            thisLogger().debug("Failed to ping existing service", e)
            return false
        }
    }

    fun startup(): Boolean {
        try {
            if (!initialized.get()) {
                // Try to shut down any existing instance before starting a new one
                shutdownExistingService()

                val port: Int = getPortForCurrentIde()

                // Generate the nonce and store it
                nonce = generateNonce()
                pathToNonce = writeNonceToFile(nonce, port)

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
            } catch (e: Exception) {
                thisLogger().error("Failed to cleanup nonce file", e)
            }

            if (initialized.get()) {
                server?.stop(1)
                thisLogger().info("Completed cleanup of plugin")
            }
        } finally {
            pathToNonce = null
            server = null
            nonce = ""
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
                    MyBundle.message("cursing_less.notification.echo.title"),
                    MyBundle.message(
                        "cursing_less.notification.echo.content",
                        httpExchange.requestMethod,
                        httpExchange.requestURI,
                        getPortForCurrentIde()
                    ),
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
        val commandPortion = command.substring(command.indexOf("/") + 1)
        val commandInformation = commandPortion.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
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
