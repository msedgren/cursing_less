package org.cursing_less.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import org.cursing_less.services.CursingMarkupService

class CursingDeletionListener : DocumentListener {

    override fun beforeDocumentChange(event: DocumentEvent) {
        val cursingMarkupService =
            ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        //EditorFactory.getInstance().getEditors(event.document).forEach {
        //    cursingMarkupService.clearTokensAround(it, event.offset)
        //}


    }
}