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
import org.cursing_less.services.CursingUserInteraction

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
                    if(inlays.size < 2) {
                        val data = inlays.firstNotNullOfOrNull { it.getUserData(INLAY_KEY) }
                        data?.let {
                            val cursingUserInteraction =
                                ApplicationManager.getApplication().getService(CursingUserInteraction::class.java)
                            val move = getMoveDirection(event)
                            thisLogger().warn("moving cursor by $move")
                            event.editor.caretModel.moveCaretRelatively(
                                move,
                                0,
                                editor.selectionModel.hasSelection() || cursingUserInteraction.leftMouseSelected,
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
        val service = ApplicationManager.getApplication().getService(CursingUserInteraction::class.java)
        val directionState = service.direction
        if(directionState.direction != CursingUserInteraction.CursingUserDirection.NONE &&
            (directionState.pressed || System.currentTimeMillis() - directionState.timeInMs <= 50)) {
            if(directionState.direction == CursingUserInteraction.CursingUserDirection.LEFT) {
                return -1
            } else {
                return 1
            }
        } else {
            return if (event.newPosition.leansForward) 1 else -1
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
