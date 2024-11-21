package org.cursing_less.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import org.cursing_less.services.CursingMarkupService

class CursingCaretListener : CaretListener {

    override fun caretPositionChanged(event: CaretEvent) {
        val cursorOffset = event.caret?.offset
        if (cursorOffset != null) {
            if (event.newPosition.line == event.oldPosition.line && event.newPosition.column == event.oldPosition.column) {
                val move = if (event.newPosition.leansForward) 1 else -1
                if (event.editor.inlayModel.hasInlineElementAt(cursorOffset)) {
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