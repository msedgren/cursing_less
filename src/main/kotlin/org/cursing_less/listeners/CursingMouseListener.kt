package org.cursing_less.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import org.cursing_less.services.SelectionService
import java.util.concurrent.atomic.AtomicBoolean

class CursingMouseListener : EditorMouseListener {



    override fun mousePressed(event: EditorMouseEvent) {
        if (event.mouseEvent.button == 1) {
            ApplicationManager.getApplication().getService(SelectionService::class.java).leftMouseSelected = true
        }
    }

    override fun mouseReleased(event: EditorMouseEvent) {
        if (event.mouseEvent.button == 1) {
            ApplicationManager.getApplication().getService(SelectionService::class.java).leftMouseSelected = false
        }
    }
}