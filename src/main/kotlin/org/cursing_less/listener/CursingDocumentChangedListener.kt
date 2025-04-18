package org.cursing_less.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.cursing_less.service.CursingMarkupService

class CursingDocumentChangedListener(private val coroutineScope: CoroutineScope) : DocumentListener {

    override fun documentChanged(event: DocumentEvent) {

        coroutineScope.launch {
            val cursingMarkupService =
                ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
            val document = event.document
            val editors = EditorFactory.getInstance().getEditors(document)

            val editorsAndOffsets = readAction {
                editors.map { Pair(it, it.caretModel.offset) }
            }
            editorsAndOffsets.forEach { (editor, offset) ->
                if (!editor.isDisposed) {
                    cursingMarkupService.updateCursingTokens(editor, offset)
                }
            }
        }
    }
}