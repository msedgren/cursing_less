package org.cursing_less.toolwindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.util.minimumHeight
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.cursing_less.service.CursingMarkStorageService
import org.cursing_less.topic.CursingMarkStorageListener
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import javax.swing.JTextArea

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
    private val markStorageService =
        ApplicationManager.getApplication().getService(CursingMarkStorageService::class.java)

    private val textArea = JTextArea().apply {
        isEditable = false
        text = "No marks defined\n\n\n\n\n\n\n\n"
    }
    private val panel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(textArea), BorderLayout.CENTER)
    }
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val connection = ApplicationManager.getApplication().messageBus.connect()
    private val processing = AtomicInteger(0)
    private val queue = MergingUpdateQueue("cursing_less_maks_delay", 250, true, null)


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
            connection.disconnect()
            queue.cancelAllUpdates()
            queue.dispose()
        }
    }

    private fun updateMarksDisplay() {
        val task = object : Update("shutdown") {
            override fun run() {
                coroutineScope.launch {
                    try {
                        val processingCount = processing.incrementAndGet()
                        if (processingCount <= 1) {
                            updateMarksDisplayNow()
                        } else {
                            updateMarksDisplay()
                        }
                    } finally {
                        processing.decrementAndGet()
                    }
                }
            }
        }
        queue.queue(task)
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
}
