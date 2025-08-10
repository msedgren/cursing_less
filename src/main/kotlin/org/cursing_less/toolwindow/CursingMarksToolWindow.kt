package org.cursing_less.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.util.coroutines.flow.debounceBatch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingMarkStorageService
import org.cursing_less.service.CursingScopeService
import org.cursing_less.topic.CursingMarkStorageListener
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import javax.swing.JTextArea
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tool window factory for displaying the current marks in the editor.
 */
class CursingMarksToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val marksToolWindow = CursingMarksToolWindow(toolWindow)
        marksToolWindow.initializeContent()
    }
}

/**
 * Tool window for displaying the current marks in the editor.
 */
class CursingMarksToolWindow(private val toolWindow: ToolWindow) {

    private val markStorageService by lazy {
        ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)
    }

    private val connection by lazy {
        ApplicationManager.getApplication().messageBus.connect()
    }

    private val coroutineScope by lazy {
        ApplicationManager.getApplication().getService(CursingScopeService::class.java)
            .coroutineScope
            .childScope("CursingMarksToolWindow")
    }

    private val textArea = JTextArea().apply {
        isEditable = false
        // Set a larger default size via rows to avoid tiny initial display
        rows = 12
        text = "No marks defined\n\n\n\n\n\n\n\n"
    }
    private val panel = JPanel(BorderLayout()).apply {
        // Provide a preferred size so the bottom tool window opens larger initially
        preferredSize = Dimension(600, 250)
        add(JBScrollPane(textArea), BorderLayout.CENTER)
    }

    private val processingCount = AtomicInteger(0)

    private val flow =
        MutableSharedFlow<Unit>(
            0,
            1,
            BufferOverflow.DROP_OLDEST
        )

    companion object {
        val instances = mutableListOf<CursingMarksToolWindow>()

        fun workToProcess(): Boolean {
            return instances.any { it.workToProcess() }
        }
    }

    init {
        instances.add(this)
        coroutineScope.launch(Dispatchers.Unconfined) {
            var debouncedFlow = flow.debounceBatch(250.milliseconds)
            debouncedFlow
                .cancellable()
                .collect {
                    try {
                        updateMarksDisplayNow()
                    } finally {
                        processingCount.decrementAndGet()
                    }
                }
        }.invokeOnCompletion {
            resetProcessingCount()
        }
    }


    /**
     * Initializes the content of the tool window.
     */
    fun initializeContent() {
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(panel, "Cursing Marks", false)
        toolWindow.contentManager.addContent(content)

        // Update the marks displayed initially
        updateMarksDisplay()

        // Subscribe to storage changes
        connection.subscribe(CursingMarkStorageListener.TOPIC, object : CursingMarkStorageListener {
            override fun onStorageChanged() {
                updateMarksDisplay()
            }
        })


        // Add a listener to dispose of the connection
        Disposer.register(toolWindow.disposable) {
            instances.remove(this)
            connection.disconnect()
            coroutineScope.cancel()
        }
    }

    private fun updateMarksDisplay() {
        coroutineScope.launch {
            processingCount.incrementAndGet()
            flow.emit(Unit)
        }
    }

    /**
     * Updates the display of marks in the tool window.
     */
    private suspend fun updateMarksDisplayNow() {
        // Get all marked text from the storage service
        val allMarks = markStorageService.getAllMarkedTextInfo()

        // Update the text area with the marks information
        val marksText = if (allMarks.isNotEmpty()) {
            buildString {
                allMarks.entries.forEach { (markNumber, markInfo) ->
                    append("Mark $markNumber: ")
                    append(markInfo.text.take(200))
                    if (markInfo.text.length > 200) {
                        append("...")
                    }
                    append("\n\n")
                }
            }
        } else {
            "No marks defined"
        }

        withContext(Dispatchers.EDT) {
            textArea.text = marksText
        }
    }

    fun workToProcess(): Boolean {
        return processingCount.get() > 0 && coroutineScope.isActive
    }

    fun resetProcessingCount() {
        if (processingCount.get() > 0) {
            thisLogger().error("processing count: ${processingCount.get()} reset to zero")
        }
        processingCount.set(0)
    }
}
