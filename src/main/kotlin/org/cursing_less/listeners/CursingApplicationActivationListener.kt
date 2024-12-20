package org.cursing_less.listeners

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.wm.IdeFrame
import com.intellij.util.PlatformUtils
import com.sun.net.httpserver.HttpServer
import org.cursing_less.MyBundle
import org.cursing_less.commands.VoiceCommand
import org.cursing_less.server.RequestHandler
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*
import kotlin.Exception

internal class CursingApplicationActivationListener : ApplicationActivationListener {

    private var caretListener: CursingCaretListener? = null
    private var cursingVisibleAreaListener: CursingVisibleAreaListener? = null

    private var pathToNonce: Path? = null
    private var server: HttpServer? = null

    companion object {
        const val DEFAULT_PORT: Int = 8652
        val PLATFORM_TO_PORT: Map<String, Int> = mapOf(
            PlatformUtils.IDEA_PREFIX to 8653,
            PlatformUtils.IDEA_CE_PREFIX to 8654,
            PlatformUtils.APPCODE_PREFIX to 8655,
            PlatformUtils.CLION_PREFIX to 8657,
            PlatformUtils.PYCHARM_PREFIX to 8658,
            PlatformUtils.PYCHARM_CE_PREFIX to 8658,
            PlatformUtils.PYCHARM_EDU_PREFIX to 8658,
            PlatformUtils.RUBY_PREFIX to 8661,
            PlatformUtils.PHP_PREFIX to 8662,
            PlatformUtils.WEB_PREFIX to 8663,
            PlatformUtils.DBE_PREFIX to 8664,
            PlatformUtils.RIDER_PREFIX to 8660,
            PlatformUtils.GOIDE_PREFIX to 8659,
        )
    }

    override fun applicationActivated(ideFrame: IdeFrame) {
        if (startupServer()) {
            val eventMulticaster = EditorFactory.getInstance().eventMulticaster
            caretListener = CursingCaretListener()
            eventMulticaster.addCaretListener(caretListener!!, DoNothingDisposable())

            cursingVisibleAreaListener = CursingVisibleAreaListener()
            eventMulticaster.addVisibleAreaListener(cursingVisibleAreaListener!!, DoNothingDisposable())

            VoiceCommand::class.sealedSubclasses
                .forEach { thisLogger().info("Registered command handler for ${it.simpleName}") }
        }
    }

    override fun applicationDeactivated(ideFrame: IdeFrame) {
        thisLogger().info("cleaning up listeners and shutting down server...")
        cleanupListeners()
        shutdownServer()
    }

    private fun cleanupListeners() {
        if (caretListener != null) {
            EditorFactory.getInstance().eventMulticaster.removeCaretListener(caretListener!!)
            caretListener = null
        }

        if (cursingVisibleAreaListener != null) {
            EditorFactory.getInstance().eventMulticaster.removeVisibleAreaListener(cursingVisibleAreaListener!!)
            cursingVisibleAreaListener = null
        }
    }

    class DoNothingDisposable : Disposable.Default

    private fun startupServer(): Boolean {
        try {
            val random = SecureRandom()
            val bytes = ByteArray(20)
            random.nextBytes(bytes)
            val nonce = String(Base64.getUrlEncoder().encode(bytes))
            val port: Int = PLATFORM_TO_PORT.getOrDefault(PlatformUtils.getPlatformPrefix(), DEFAULT_PORT)
            try {
                val noncePath = FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"), "vcidea_$port")
                pathToNonce = noncePath
                Files.write(noncePath, nonce.toByteArray())
            } catch (e: IOException) {
                thisLogger().error("Failed to write nonce file", e)
            }

            // https://stackoverflow.com/questions/3732109/simple-http-server-in-java-using-only-java-se-api#3732328
            val loopbackSocket = InetSocketAddress(InetAddress.getLoopbackAddress(), port)
            server = HttpServer.create(loopbackSocket, -1)
            server?.createContext("/$nonce", RequestHandler())
            server?.executor = null // creates a default executor
            server?.start()

            return true
        } catch (e: Exception) {
            notifyStartupFailure(e)
            thisLogger().error("Failed to start server to listen for commands", e)
        }
        return false
    }

    private fun shutdownServer() {
        try {
            try {
                val nonce = pathToNonce
                if (nonce != null) {
                    Files.delete(nonce)
                }
            } catch (e: IOException) {
                thisLogger().error("Failed to cleanup nonce file", e)
            }
            server?.stop(1)
            thisLogger().info("Completed cleanup of plugin")
        } finally {
            pathToNonce = null
            server = null
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
}
