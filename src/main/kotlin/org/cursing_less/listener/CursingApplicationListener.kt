package org.cursing_less.listener

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cursing_less.command.VoiceCommand
import org.cursing_less.handler.CursingEditorActionHandler
import org.cursing_less.service.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger


class CursingApplicationListener : AppLifecycleListener {


    companion object {
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
        private val commandService: CursingCommandService by lazy {
            ApplicationManager.getApplication().getService(CursingCommandService::class.java)
        }
        private val cursingMarkupService: CursingMarkupService by lazy {
            ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        }
        val initialized = AtomicBoolean(false)
        val processing = AtomicInteger(0)

        suspend fun startup() {
            try {
                val processingCount = processing.incrementAndGet()
                if (processingCount <= 1 && !initialized.get()) {
                    thisLogger().info("app started initializing cursing_less plugin")
                    if (ApplicationManager.getApplication().isUnitTestMode
                        || commandService.startup()
                    ) {
                        setupListeners()

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
            } finally {
                processing.decrementAndGet()
            }
        }

        private fun setupListeners() {
            val eventMulticaster = EditorFactory.getInstance().eventMulticaster

            val cursingScopeService =
                ApplicationManager.getApplication().getService(CursingScopeService::class.java)

            eventMulticaster.addCaretListener(
                CursingCaretListener(cursingScopeService.coroutineScope),
                CursingPluginLifetimeDisposable.getInstance()
            )
            eventMulticaster.addVisibleAreaListener(
                CursingVisibleAreaListener(cursingScopeService.coroutineScope),
                CursingPluginLifetimeDisposable.getInstance()
            )
            eventMulticaster.addDocumentListener(
                CursingDocumentChangedListener(cursingScopeService.coroutineScope),
                CursingPluginLifetimeDisposable.getInstance()
            )
            eventMulticaster.addEditorMouseListener(
                CursingMouseListener(),
                CursingPluginLifetimeDisposable.getInstance()
            )

            val actionManager = EditorActionManager.getInstance()
            // Left arrow
            val leftHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT)
            actionManager.setActionHandler(
                IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT,
                CursingEditorActionHandler(leftHandler, CursingUserDirection.LEFT)
            )

            // Right arrow
            val rightHandler = actionManager.getActionHandler(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT)
            actionManager.setActionHandler(
                IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT,
                CursingEditorActionHandler(rightHandler, CursingUserDirection.RIGHT)
            )
        }

        fun shutdown() {
            try {
                val commandService =
                    ApplicationManager.getApplication().getService(CursingCommandService::class.java)
                commandService.shutdown()
            } finally {
                initialized.set(false)
            }
        }

    }

}
