package org.cursing_less.handler


import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import org.cursing_less.services.CursingUserInteraction

class CursingEditorActionHandler(private val originalHandler: EditorActionHandler,
                                 private val direction: CursingUserInteraction.CursingUserDirection
) : EditorActionHandler() {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val service = ApplicationManager.getApplication().getService(CursingUserInteraction::class.java)
        service.direction = CursingUserInteraction.CursingDirectionState(direction, false, System.currentTimeMillis())
        originalHandler.execute(editor, caret, dataContext)
    }
}
