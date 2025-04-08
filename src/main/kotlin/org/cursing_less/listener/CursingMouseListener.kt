package org.cursing_less.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import org.cursing_less.service.CursingUserInteractionService

class CursingMouseListener : EditorMouseListener {

    override fun mousePressed(event: EditorMouseEvent) {
        if (event.mouseEvent.button == 1) {
            ApplicationManager.getApplication().getService(CursingUserInteractionService::class.java).leftMouseSelected = true
        }
    }

    override fun mouseReleased(event: EditorMouseEvent) {
        if (event.mouseEvent.button == 1) {
            ApplicationManager.getApplication().getService(CursingUserInteractionService::class.java).leftMouseSelected = false
        }
    }
}