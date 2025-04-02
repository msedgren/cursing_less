package org.cursing_less.handler

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.cursing_less.services.CursingMarkupService.Companion.INLAY_KEY

class CursingDeletionHandler(private val originalHandler: EditorActionHandler) :
    EditorActionHandler(originalHandler.runForAllCarets()) {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        try {
            val offset = caret?.offset
            if (offset != null && offset > 0) {
                val inlays = editor.inlayModel.getInlineElementsInRange(caret.offset - 2, caret.offset + 2)
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
