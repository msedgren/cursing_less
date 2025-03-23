package org.cursing_less.listeners

import com.intellij.ide.AppLifecycleListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.PlatformUtils
import com.sun.net.httpserver.HttpServer
import org.cursing_less.MyBundle
import org.cursing_less.commands.VoiceCommand
import org.cursing_less.handler.CursingDeletionHandler
import org.cursing_less.server.RequestHandler
import org.cursing_less.services.CursingMarkupService
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*


class CursingApplicationListener() : AppLifecycleListener {


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
        val handler = StartupHandler()
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        thisLogger().info("shutting down cursing_less plugin")
        handler.shutdown()
    }

    class StartupActivity : ProjectActivity {
        override suspend fun execute(project: Project) {
            handler.startup()
        }
    }

    class StartupHandler {
        private var pathToNonce: Path? = null
        private var server: HttpServer? = null

        var initialized = false;

        companion object {
            val mutex = Object()
        }

        fun startup() {
            synchronized(mutex) {
                if (!initialized) {
                    thisLogger().info("app started initializing cursing_less plugin")
                    if (startupServer()) {
                        setupListeners()
                        setupDeleteHandlers()

                        ApplicationManager.getApplication().invokeLater({
                            ApplicationManager.getApplication().runWriteAction {
                                val cursingMarkupService =
                                    ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
                                EditorFactory.getInstance().allEditors.forEach { editor ->
                                    cursingMarkupService.updateCursingTokens(editor, editor.caretModel.offset)
                                }
                            }
                        }, ModalityState.any())


                        VoiceCommand::class.sealedSubclasses
                            .forEach { thisLogger().info("Registered command handler for ${it.simpleName}") }


                        initialized = true
                    }
                }
            }

        }

        private fun setupListeners() {
            val eventMulticaster = EditorFactory.getInstance().eventMulticaster

            eventMulticaster.addCaretListener(CursingCaretListener(), DoNothingDisposable())
            eventMulticaster.addVisibleAreaListener(CursingVisibleAreaListener(), DoNothingDisposable())
            eventMulticaster.addDocumentListener(CursingDocumentChangedListener(), DoNothingDisposable())
        }

        private fun setupDeleteHandlers() {
            val actionManager = EditorActionManager.getInstance()
            val originalBackSpaceHandler = actionManager.getActionHandler("EditorBackSpace")
            actionManager.setActionHandler("EditorBackSpace", CursingDeletionHandler(originalBackSpaceHandler))
            val originalDeleteHandler = actionManager.getActionHandler("EditorDelete")
            actionManager.setActionHandler("EditorDelete", CursingDeletionHandler(originalDeleteHandler))
        }


        fun shutdown() {
            synchronized(mutex) {
                try {
                    shutdownServer()
                } finally {
                    initialized = false;
                }
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

}
