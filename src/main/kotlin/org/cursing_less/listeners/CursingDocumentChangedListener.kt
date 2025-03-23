package org.cursing_less.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import org.cursing_less.services.CursingMarkupService

class CursingDocumentChangedListener : DocumentListener {

    override fun documentChanged(event: DocumentEvent) {
        val cursingMarkupService =
            ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
        val document = event.document
        val editors = EditorFactory.getInstance().getEditors(document)

        editors.forEach { editor ->
            cursingMarkupService.updateCursingTokens(editor, editor.caretModel.offset)
        }
    }
}