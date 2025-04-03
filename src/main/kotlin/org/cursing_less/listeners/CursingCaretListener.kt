package org.cursing_less.listeners

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
import org.cursing_less.services.CursingUserInteractionService

class CursingCaretListener(private val coroutineScope: CoroutineScope) : CaretListener {

    override fun caretPositionChanged(event: CaretEvent) {
        coroutineScope.launch(Dispatchers.EDT) {
            val caret = event.caret
            var editor = event.editor
            if (!editor.isDisposed && editor.getUserData(ColorAndShapeManager.KEY) != null && caret != null) {
                val cursorOffset = caret.offset
                if (event.newPosition.line == event.oldPosition.line && event.newPosition.column == event.oldPosition.column) {
                    val inlays =
                        event.editor.inlayModel.getInlineElementsInRange(caret.offset, caret.offset)
                    val cursingUserInteractionService =
                        ApplicationManager.getApplication().getService(CursingUserInteractionService::class.java)
                    if(!cursingUserInteractionService.leftMouseSelected) {
                        val data = inlays.firstNotNullOfOrNull { it.getUserData(INLAY_KEY) }
                        data?.let {
                            val move = getMoveDirection(event)
                            thisLogger().warn("moving cursor by $move")
                            event.editor.caretModel.moveCaretRelatively(
                                move,
                                0,
                                editor.selectionModel.hasSelection() || cursingUserInteractionService.makingSelection,
                                false,
                                false
                            )
                        }
                    }
                }
                val cursingMarkupService =
                    ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
                cursingMarkupService.updateCursingTokens(event.editor, cursorOffset)
            }
        }
    }

    private fun getMoveDirection(event: CaretEvent): Int {
        val service = ApplicationManager.getApplication().getService(CursingUserInteractionService::class.java)
        val directionState = service.direction
        return if(directionState.direction != CursingUserInteractionService.CursingUserDirection.NONE &&
            (System.currentTimeMillis() - directionState.timeInMs <= 50)) {
            if(directionState.direction == CursingUserInteractionService.CursingUserDirection.LEFT) {
                -1
            } else {
                1
            }
        } else {
            0
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
