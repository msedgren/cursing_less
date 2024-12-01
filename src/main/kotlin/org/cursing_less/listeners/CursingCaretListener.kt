package org.cursing_less.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import org.cursing_less.services.CursingMarkupService
import org.cursing_less.services.CursingMarkupService.Companion.INLAY_KEY

class CursingCaretListener : CaretListener {

    override fun caretPositionChanged(event: CaretEvent) {
        val caret = event.caret
        if (caret != null) {
            val cursorOffset = caret.offset
            if (event.newPosition.line == event.oldPosition.line && event.newPosition.column == event.oldPosition.column) {
                val move = if (event.newPosition.leansForward) 1 else -1
                val cursingData = event.editor.inlayModel.getInlineElementsInRange(caret.offset, caret.offset).firstOrNull()
                    ?.let { inlay: Inlay<*> ->  inlay.getUserData(INLAY_KEY) }
                if (cursingData != null) {
                    event.editor.caretModel.moveCaretRelatively(move, 0, true, false, false)
                }
            } else {
                val cursingMarkupService =
                    ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
                cursingMarkupService.updateHighlightedTokens(event.editor, cursorOffset)
            }
        }
    }
}