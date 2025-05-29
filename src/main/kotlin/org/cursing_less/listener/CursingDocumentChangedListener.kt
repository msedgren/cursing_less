package org.cursing_less.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.cursing_less.service.CursingDirectionState
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingUserInteractionService

class CursingDocumentChangedListener(private val coroutineScope: CoroutineScope) : DocumentListener {
    private val cursingUserInteractionService by lazy {
        ApplicationManager.getApplication().getService(CursingUserInteractionService::class.java)
    }
    private val cursingMarkupService by lazy {
        ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
    }

    override fun beforeDocumentChange(event: DocumentEvent) {
        cursingUserInteractionService.direction = CursingDirectionState.NONE
    }

    override fun documentChanged(event: DocumentEvent) {
        cursingUserInteractionService.direction = CursingDirectionState.NONE
        coroutineScope.launch {
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