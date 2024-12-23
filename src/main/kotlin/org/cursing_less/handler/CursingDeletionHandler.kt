package org.cursing_less.handler

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.cursing_less.services.CursingMarkupService.Companion.INLAY_KEY

class CursingDeletionHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler(originalHandler.runForAllCarets())  {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        ApplicationManager.getApplication().runReadAction {
            val offset = caret?.offset
            if (offset != null && offset > 0) {
                thisLogger().info("delete or backspace!")
                editor.inlayModel.getInlineElementsInRange(caret.offset - 1, caret.offset).forEach {
                    val cursingData = it?.getUserData(INLAY_KEY)
                    if (cursingData != null) {
                        thisLogger().trace("Disposing of inlay at ${offset}")
                        ApplicationManager.getApplication().runWriteAction {
                            editor.inlayModel.execute(false) {
                                it.dispose()
                            }
                            editor.contentComponent.repaint()
                        }
                    }
                }
            }
        }
        originalHandler.execute(editor, caret, dataContext)
    }

}
