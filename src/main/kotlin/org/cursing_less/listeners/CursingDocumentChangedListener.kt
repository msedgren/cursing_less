package org.cursing_less.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cursing_less.services.CursingMarkupService

class CursingDocumentChangedListener(private val coroutineScope: CoroutineScope) : DocumentListener {

    override fun documentChanged(event: DocumentEvent) {
        coroutineScope.launch(Dispatchers.EDT) {
            val cursingMarkupService =
                ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
            val document = event.document
            val editors = EditorFactory.getInstance().getEditors(document)

            editors.forEach { editor ->
                cursingMarkupService.updateCursingTokens(editor, editor.caretModel.offset)
            }
        }
    }
}