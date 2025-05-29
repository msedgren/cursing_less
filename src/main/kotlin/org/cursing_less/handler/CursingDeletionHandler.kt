package org.cursing_less.handler

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.TextRange
import org.cursing_less.service.CursingDirectionState
import org.cursing_less.service.CursingMarkupService.Companion.INLAY_KEY
import org.cursing_less.service.CursingUserInteractionService

class CursingDeletionHandler(private val originalHandler: EditorActionHandler) :
    EditorActionHandler(originalHandler.runForAllCarets()) {

    private val cursingUserInteractionService: CursingUserInteractionService by lazy {
        ApplicationManager.getApplication().getService(CursingUserInteractionService::class.java)
    }

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        try {
            cursingUserInteractionService.direction = CursingDirectionState.NONE
            val offset = caret?.offset
            if (offset != null && offset > 0) {
                val selection =  ApplicationManager.getApplication().runReadAction<TextRange> {
                    caret.selectionRange.let {
                        if (it.isEmpty) TextRange.create(offset, offset) else it
                    }
                }
                val inlays = editor.inlayModel.getInlineElementsInRange(selection.startOffset, selection.endOffset)
                if (inlays.isNotEmpty()) {
                    inlays.forEach {
                        val cursingData = it?.getUserData(INLAY_KEY)
                        if (cursingData != null) {
                            editor.inlayModel.execute(false) {
                                it.dispose()
                            }
                            editor.contentComponent.repaint()
                        }
                    }
                }
            }
        } finally {
            originalHandler.execute(editor, caret, dataContext)
        }
    }

}
