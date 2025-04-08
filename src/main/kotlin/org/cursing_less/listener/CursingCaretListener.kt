package org.cursing_less.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingMarkupService.Companion.INLAY_KEY
import org.cursing_less.service.CursingUserInteractionService

class CursingCaretListener(private val coroutineScope: CoroutineScope) : CaretListener {

    companion object {
        private const val MOVED_KEY_NAME = "CURSING_MOVED"
        private const val TIME_IN_MS_FROM_LAST_MOVE = 200L

        val MOVED_KEY = Key.create<MoveDirection>(MOVED_KEY_NAME)
    }

    override fun caretPositionChanged(event: CaretEvent) {
        coroutineScope.launch(Dispatchers.EDT) {
            val caret = event.caret
            val editor = event.editor
            if (!editor.isDisposed && editor.getUserData(ColorAndShapeManager.KEY) != null && caret != null) {
                val cursorOffset = caret.offset
                if (event.newPosition.line == event.oldPosition.line && event.newPosition.column == event.oldPosition.column) {
                    val inlays =
                        event.editor.inlayModel.getInlineElementsInRange(caret.offset, caret.offset)
                    val cursingUserInteractionService =
                        ApplicationManager.getApplication().getService(CursingUserInteractionService::class.java)
                    val last = editor.getUserData(MOVED_KEY)
                    val move = getMoveDirection()
                    if(!cursingUserInteractionService.leftMouseSelected &&
                        (last == null || last.offset != cursorOffset || last.direction != move || System.currentTimeMillis() - last.timeInMs > TIME_IN_MS_FROM_LAST_MOVE)) {
                        val data = inlays.firstNotNullOfOrNull { it.getUserData(INLAY_KEY) }
                        data?.let {
                            event.editor.caretModel.moveCaretRelatively(
                                move,
                                0,
                                editor.selectionModel.hasSelection() || cursingUserInteractionService.makingSelection,
                                false,
                                false
                            )
                            editor.putUserData(MOVED_KEY, MoveDirection(move, cursorOffset, System.currentTimeMillis()))
                        }
                    }
                }
                val cursingMarkupService =
                    ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
                cursingMarkupService.updateCursingTokens(event.editor, cursorOffset)
            }
        }
    }

    private fun getMoveDirection(): Int {
        val service = ApplicationManager.getApplication().getService(CursingUserInteractionService::class.java)
        val directionState = service.direction
        return if(directionState.direction != CursingUserInteractionService.CursingUserDirection.NONE &&
            (System.currentTimeMillis() - directionState.timeInMs <= TIME_IN_MS_FROM_LAST_MOVE)) {
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

    data class MoveDirection(val direction: Int, val offset: Int, val timeInMs: Long)
}
