package org.cursing_less.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.cursing_less.service.CursingDirectionState
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingMarkupService.Companion.INLAY_KEY
import org.cursing_less.service.CursingUserInteractionService

class CursingDocumentChangedListener(private val coroutineScope: CoroutineScope) : DocumentListener {

    private val cursingUserInteractionService = ApplicationManager.getApplication()
        .getService(CursingUserInteractionService::class.java)
    private val cursingMarkupService =
        ApplicationManager.getApplication().getService(CursingMarkupService::class.java)

    override fun beforeDocumentChange(event: DocumentEvent) {
        cursingUserInteractionService.direction = CursingDirectionState.NONE
        val startOffset = event.offset
        val endOffset = event.oldLength + startOffset
        val editors = EditorFactory.getInstance().getEditors(event.document)
        editors.forEach { editor ->
            val inlays = editor.inlayModel.getInlineElementsInRange(startOffset, endOffset)
            thisLogger().debug("removing inlays for $startOffset and $endOffset as part of document change")
            if (inlays.isNotEmpty()) {
                inlays.forEach {
                    val cursingData = it?.getUserData(INLAY_KEY)
                    if (cursingData != null) {
                        editor.inlayModel.execute(false) {
                            it.dispose()
                        }
                    }
                }
            }
        }
    }

    override fun documentChanged(event: DocumentEvent) {
        coroutineScope.launch {
            cursingUserInteractionService.direction = CursingDirectionState.NONE
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