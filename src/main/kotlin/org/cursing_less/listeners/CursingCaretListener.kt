package org.cursing_less.listeners

import com.intellij.codeInsight.codeVision.ui.mouseClicked
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.services.CursingMarkupService
import org.cursing_less.services.CursingMarkupService.Companion.INLAY_KEY
import org.cursing_less.services.SelectionService

class CursingCaretListener(private val coroutineScope: CoroutineScope) : CaretListener {

    override fun caretPositionChanged(event: CaretEvent) {
        coroutineScope.launch(Dispatchers.EDT) {
            val caret = event.caret
            var editor = event.editor
            if (editor.getUserData(ColorAndShapeManager.KEY) != null && caret != null) {
                val cursorOffset = caret.offset
                if (event.newPosition.line == event.oldPosition.line && event.newPosition.column == event.oldPosition.column) {
                    val move = if (event.newPosition.leansForward) 1 else -1
                    val inlays =
                        event.editor.inlayModel.getInlineElementsInRange(caret.offset, caret.offset)
                    val inlay = inlays.firstOrNull()
                    inlay?.getUserData(INLAY_KEY)?.let {
                        val selectionService = ApplicationManager.getApplication().getService(SelectionService::class.java)
                        event.editor.caretModel.moveCaretRelatively(
                            move,
                            0,
                            editor.selectionModel.hasSelection() || selectionService.leftMouseSelected,
                            false,
                            false
                        )
                    }
                }
                val cursingMarkupService =
                    ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
                cursingMarkupService.updateCursingTokens(event.editor, cursorOffset)
            }
        }
    }

    override fun caretAdded(event: CaretEvent) {
        coroutineScope.launch(Dispatchers.EDT) {
            val cursingMarkupService =
                ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
            cursingMarkupService.updateCursingTokens(event.editor, event.caret?.offset ?: 0)
        }
    }
}
