package org.cursing_less.listeners

import com.intellij.ide.AppLifecycleListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.PlatformUtils
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.MyBundle
import org.cursing_less.commands.VoiceCommand
import org.cursing_less.handler.CursingEditorActionHandler
import org.cursing_less.server.RequestHandler
import org.cursing_less.services.CursingMarkupService
import org.cursing_less.services.CursingScopeService
import org.cursing_less.services.CursingUserInteraction
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class CursingApplicationListener : AppLifecycleListener {


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

        var initialized = AtomicBoolean(false);

        suspend fun startup() {
            if (!initialized.get()) {
                thisLogger().info("app started initializing cursing_less plugin")
                if (startupServer()) {
                    setupListeners()
                    setupDeleteHandlers()
                    
                    val actionManager = EditorActionManager.getInstance()
                    // Left arrow
                    val leftHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT)
                    actionManager.setActionHandler(
                        IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT,
                        CursingEditorActionHandler(leftHandler, CursingUserInteraction.CursingUserDirection.LEFT)
                    )

                    // Right arrow
                    val rightHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
                    actionManager.setActionHandler(
                        IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT,
                        CursingEditorActionHandler(rightHandler, CursingUserInteraction.CursingUserDirection.RIGHT)
                    )


                    val cursingMarkupService =
                        ApplicationManager.getApplication().getService(CursingMarkupService::class.java)

                    withContext(Dispatchers.EDT) {
                        EditorFactory.getInstance().allEditors.forEach { editor ->
                            cursingMarkupService.updateCursingTokens(editor, editor.caretModel.offset)
                        }
                    }


                    VoiceCommand::class.sealedSubclasses
                        .forEach { thisLogger().info("Registered command handler for ${it.simpleName}") }


                    initialized.set(true)
                }
            }
        }

        private fun setupListeners() {
            val eventMulticaster = EditorFactory.getInstance().eventMulticaster

            val cursingScopeService =
                ApplicationManager.getApplication().getService(CursingScopeService::class.java)

            eventMulticaster.addCaretListener(CursingCaretListener(cursingScopeService.coroutineScope), DoNothingDisposable())
            eventMulticaster.addVisibleAreaListener(CursingVisibleAreaListener(cursingScopeService.coroutineScope), DoNothingDisposable())
            eventMulticaster.addDocumentListener(CursingDocumentChangedListener(cursingScopeService.coroutineScope), DoNothingDisposable())
            eventMulticaster.addEditorMouseListener(CursingMouseListener(), DoNothingDisposable())
        }

        private fun setupDeleteHandlers() {
            /*val actionManager = EditorActionManager.getInstance()
            val originalBackSpaceHandler = actionManager.getActionHandler("EditorBackSpace")
            actionManager.setActionHandler("EditorBackSpace", CursingDeletionHandler(originalBackSpaceHandler))
            val originalDeleteHandler = actionManager.getActionHandler("EditorDelete")
            actionManager.setActionHandler("EditorDelete", CursingDeletionHandler(originalDeleteHandler))*/
        }


        fun shutdown() {
            try {
                shutdownServer()
            } finally {
                initialized.set(false);
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
