package org.cursing_less.listener

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cursing_less.color_shape.ColorAndShapeManager
import org.cursing_less.service.CursingDirectionState
import org.cursing_less.service.CursingMarkupService
import org.cursing_less.service.CursingMarkupService.Companion.INLAY_KEY
import org.cursing_less.service.CursingUserDirection
import org.cursing_less.service.CursingUserInteractionService

class CursingCaretListener(private val coroutineScope: CoroutineScope) : CaretListener {

    private val cursingMarkupService: CursingMarkupService by lazy {
        ApplicationManager.getApplication().getService(CursingMarkupService::class.java)
    }
    private val cursingUserInteractionService: CursingUserInteractionService by lazy {
        ApplicationManager.getApplication().getService(CursingUserInteractionService::class.java)
    }

    companion object {
        private const val MOVED_KEY_NAME = "CURSING_MOVED"

        val MOVED_KEY = Key.create<MoveDirection>(MOVED_KEY_NAME)
    }

    override fun caretPositionChanged(event: CaretEvent) {
        coroutineScope.launch(Dispatchers.EDT) {
            val caret = event.caret
            val editor = event.editor
            var moveDirection: MoveDirection? = null
            if (!editor.isDisposed && editor.getUserData(ColorAndShapeManager.KEY) != null && caret != null) {
                val cursorOffset = caret.offset
                if (event.newPosition.line == event.oldPosition.line && event.newPosition.column == event.oldPosition.column) {
                    moveDirection = handleMovementAtSameOffset(event, editor, caret, cursorOffset)
                }
                cursingMarkupService.updateCursingTokens(event.editor, cursorOffset)
            }
            editor.putUserData(MOVED_KEY, moveDirection)
        }
    }

    private fun handleMovementAtSameOffset(
        event: CaretEvent,
        editor: Editor,
        caret: Caret,
        cursorOffset: Int
    ): MoveDirection? {
        val inlays = editor.inlayModel.getInlineElementsInRange(caret.offset, caret.offset)
        val directionState = cursingUserInteractionService.direction
        val last = editor.getUserData(MOVED_KEY)
        val move = getMoveDirection(directionState)
        // Only considering moving the caret if we're not currently making a selection and
        // we have not already adjusted for this move or adjusted for this offset or are moving in a different direction
        if (!cursingUserInteractionService.leftMouseSelected && (last == null || last.offset != cursorOffset || last.assistAmount != move)) {
            val data = inlays.firstNotNullOfOrNull { it.getUserData(INLAY_KEY) }
            // Check if there's any inlay in the direction of movement that doesn't have INLAY_KEY
            val correctForNonCursingInlayInDirection = hasNonCursingInlaysOfConcern(move, cursorOffset, editor) &&
                    !event.oldPosition.leansForward && event.newPosition.leansForward && last == null
            // Only move if we have our inlay and there's no non-cursing inlay in the direction of movement
            if (data != null && !correctForNonCursingInlayInDirection) {
                return addExtraMovement(editor, move, cursorOffset, cursingUserInteractionService, directionState)
            }
        }
        return null
    }

    private fun addExtraMovement(
        editor: Editor,
        move: Int,
        cursorOffset: Int,
        cursingUserInteractionService: CursingUserInteractionService,
        directionState: CursingDirectionState
    ): MoveDirection {
        editor.caretModel.moveCaretRelatively(
            move,
            0,
            editor.selectionModel.hasSelection() || cursingUserInteractionService.makingSelection,
            false,
            false
        )
        return MoveDirection(move, cursorOffset, System.currentTimeMillis(), directionState)

    }

    private fun hasNonCursingInlaysOfConcern(
        move: Int,
        cursorOffset: Int,
        editor: Editor
    ): Boolean = if (move != 0) {
        val cursorPosition = editor.visualPositionToXY(editor.offsetToVisualPosition(cursorOffset)).x
        // Check for any inlay in that range without INLAY_KEY
        editor.inlayModel.getInlineElementsInRange(cursorOffset, cursorOffset)
            .filter { it.getUserData(INLAY_KEY) == null }
            .map { editor.visualPositionToXY(it.visualPosition) }
            .map { it.x }
            .any { (move > 0 && it > cursorPosition) || (move < 0 && it < cursorPosition) }
    } else {
        false
    }

    private fun getMoveDirection(directionState: CursingDirectionState): Int {
        return when (directionState.direction) {
            CursingUserDirection.LEFT -> -1
            CursingUserDirection.RIGHT -> 1
            CursingUserDirection.NONE -> 0
        }
    }

    override fun caretAdded(event: CaretEvent) {
        coroutineScope.launch(Dispatchers.EDT) {
            cursingMarkupService.updateCursingTokens(event.editor, event.caret?.offset ?: 0)
        }
    }

    data class MoveDirection(
        val assistAmount: Int,
        val offset: Int,
        val timeInMs: Long,
        val directionState: CursingDirectionState
    )
}
